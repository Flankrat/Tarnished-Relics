package com.flankrat.tarnishedrelics.client;

import com.flankrat.tarnishedrelics.BuiltInData;
import com.flankrat.tarnishedrelics.data.Effect;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * An effect with a "chance" shows that it doesn't always work. Effects whose line has its own "X% chance" (on-hit and
 * on-hurt effects, on-hit fire) say it there; for the others (lifesteal, thorns, crits, damage...) the "(sometimes)"
 * note was only added for conditions other than chance, so a 50% lifesteal read like a sure one.
 */
class TooltipChanceTest {

    @BeforeAll
    static void bootstrap() {
        BuiltInData.files();   // bootstraps Minecraft (registries, components)
    }

    private static String line(String effectJson) {
        Component c = RelicTooltips.describe(Effect.parse(JsonParser.parseString(effectJson).getAsJsonObject()), 0.1);
        return String.valueOf(c);
    }

    @ParameterizedTest
    @ValueSource(strings = {"lifesteal", "thorns", "crit_chance", "crit_damage", "damage_vs", "damage_taken", "xp_bonus", "looting"})
    void chanceOnlyIsMarked(String type) {
        String line = line("{\"type\": \"" + type + "\", \"value\": 0.1, \"conditions\": {\"chance\": 0.5}}");
        assertTrue(line.contains("tarnishedrelics.effect.conditional"), type + " with a 50% chance isn't marked: " + line);
    }

    @ParameterizedTest
    @ValueSource(strings = {"lifesteal", "crit_chance"})
    void noConditionsIsNotMarked(String type) {
        assertFalse(line("{\"type\": \"" + type + "\", \"value\": 0.1}").contains("tarnishedrelics.effect.conditional"));
    }

    // these lines already start with "35% chance on hit", so no "(sometimes)" on top
    @ParameterizedTest
    @ValueSource(strings = {"on_hit_effect", "on_hurt_effect"})
    void chanceInTheLineIsNotMarkedTwice(String type) {
        String line = line("{\"type\": \"" + type + "\", \"effect\": \"minecraft:slowness\", \"conditions\": {\"chance\": 0.35}}");
        assertFalse(line.contains("tarnishedrelics.effect.conditional"), line);
    }
}
