package com.flankrat.tarnishedrelics.client;

import com.flankrat.tarnishedrelics.BuiltInData;
import com.flankrat.tarnishedrelics.data.Effect;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The Shift tooltip's roll note: the range a value was rolled in and where in it the roll is, in the line's units. */
class TooltipRollTest {

    private static final ResourceLocation RARE = new ResourceLocation("tarnishedrelics", "rare");

    @BeforeAll
    static void bootstrap() {
        BuiltInData.files();
    }

    private static Effect effect(String json) {
        return Effect.parse(JsonParser.parseString(json).getAsJsonObject());
    }

    private static String note(String json, double roll) {
        return String.valueOf(RelicTooltips.rollNote(effect(json), RARE, roll));
    }

    @Test
    void percentRange() {
        String n = note("{\"type\": \"damage_vs\", \"value\": [0.2, 0.4]}", 0.5);
        assertTrue(n.contains("tarnishedrelics.tooltip.roll") && n.contains("+20%") && n.contains("+40%") && n.contains("50%"), n);
    }

    // the rarity's own range, not another's
    @Test
    void perRarityRange() {
        String n = note("{\"type\": \"lifesteal\", \"value\": {\"magic\": [0.01, 0.02], \"rare\": [0.05, 0.1]}}", 1.0);
        assertTrue(n.contains("+5%") && n.contains("+10%") && n.contains("100%"), n);
    }

    @Test
    void addedAttributeIsANumber() {
        String n = note("{\"type\": \"attribute\", \"attribute\": \"minecraft:generic.attack_damage\", \"operation\": \"add\", \"value\": [2, 5]}", 0.0);
        assertTrue(n.contains("+2") && n.contains("+5") && n.contains("0%") && !n.contains("+200%"), n);
    }

    @Test
    void secondsAndLevels() {
        assertTrue(note("{\"type\": \"on_hit_fire\", \"value\": [3, 6]}", 0.5).contains("6"));
        assertTrue(note("{\"type\": \"looting\", \"value\": [1, 2]}", 0.5).contains("+2"));
    }

    // Bug: for damage_taken a lower value is better (-10% taken beats -5%), but the note counted from the lowest
    // number, so of_fire_warding's best value (roll 0 = -10%) said "0% roll" and its worst "100% roll". The percent is
    // now how good the value is, and the range reads from the worst to the best like every other line.
    @Test
    void damageTakenCountsTheBetterEnd() {
        String warding = "{\"type\": \"damage_taken\", \"value\": {\"rare\": [-0.15, -0.08]}}";   // of_fire_warding, rare
        String best = note(warding, 0.0), worst = note(warding, 1.0);
        assertTrue(best.contains("100%"), "the best value (-15%) should be a 100% roll: " + best);
        assertTrue(worst.contains("0%") && !worst.contains("100%"), "the worst value (-8%) should be a 0% roll: " + worst);
        assertTrue(best.indexOf("-8%") < best.indexOf("-15%"), "range from the worst to the best: " + best);
        // a drawback the same way: Exposed's +10% taken is the milder end
        assertTrue(note("{\"type\": \"damage_taken\", \"value\": [0.1, 0.2]}", 0.0).contains("100%"));
    }

    // nothing to show: a fixed number, or an effect without one
    @Test
    void noRange() {
        assertNull(RelicTooltips.rollNote(effect("{\"type\": \"lifesteal\", \"value\": 0.1}"), RARE, 0.5));
        assertNull(RelicTooltips.rollNote(effect("{\"type\": \"on_hit_effect\", \"effect\": \"minecraft:poison\"}"), RARE, 0.5));
    }

    @Test
    void attributeLineHasTheValueAndName() {
        Component line = RelicTooltips.attributeLine(effect("{\"type\": \"attribute\", \"attribute\": \"minecraft:generic.attack_damage\", \"operation\": \"add\", \"value\": [2, 5]}"), 3.5);
        String s = String.valueOf(line);
        assertTrue(s.contains("+3.5") && s.contains("attribute.name.generic.attack_damage"), s);
    }
}
