package com.flankrat.tarnishedrelics.data;

import com.flankrat.tarnishedrelics.BuiltInData;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Misspelled keys and values in an affix. Each one used to be ignored without a word, and the affix then did
 * something else than written: an effect for the wielder went to the enemy, a condition never applied, a prefix had
 * no name part.
 */
class TyposTest {

    private static TRData affix(String json) {
        Map<ResourceLocation, JsonElement> files = new LinkedHashMap<>(BuiltInData.files());
        files.put(new ResourceLocation("testpack", "affixes/typo"), JsonParser.parseString(json));
        return TRData.parseOnly(files);
    }

    private static void reported(String json, String word) {
        List<String> errors = affix(json).errors();
        assertTrue(errors.stream().anyMatch(e -> e.startsWith("testpack:affixes/typo:") && e.contains(word)), "errors: " + errors);
    }

    // "wielder" is the word "as" uses; for apply_to it fell through to "target", so the buff went to the enemy
    @Test
    void applyToWielder() {
        reported("{\"effects\": [{\"type\": \"on_hit_effect\", \"effect\": \"minecraft:strength\", \"apply_to\": \"wielder\"}]}", "wielder");
    }

    @Test
    void functionAsSomeoneElse() {
        reported("{\"effects\": [{\"type\": \"function\", \"function\": \"mypack:boom\", \"as\": \"attacker\"}]}", "attacker");
    }

    // these two were already refused; checked so they stay refused
    @Test
    void wrongOperationAndTrigger() {
        reported("{\"effects\": [{\"type\": \"attribute\", \"attribute\": \"minecraft:generic.armor\", \"operation\": \"multiply\", \"value\": 1}]}", "multiply");
        reported("{\"effects\": [{\"type\": \"function\", \"function\": \"mypack:f\", \"trigger\": \"attack\"}]}", "attack");
    }

    // a misspelled condition was skipped, so the effect worked all the time
    @Test
    void misspelledCondition() {
        reported("{\"effects\": [{\"type\": \"damage_vs\", \"value\": 0.5, \"conditions\": {\"health_belw\": 0.3}}]}", "health_belw");
    }

    // a misspelled effect field fell back to its default (here 100 ticks instead of 400)
    @Test
    void misspelledEffectField() {
        reported("{\"effects\": [{\"type\": \"on_hit_effect\", \"effect\": \"minecraft:poison\", \"durration\": 400}]}", "durration");
    }

    // "Prefix" (or any other word) made a hidden affix: no name part
    @ParameterizedTest
    @ValueSource(strings = {"Prefix", "prefx", "name"})
    void unknownKind(String kind) {
        reported("{\"kind\": \"" + kind + "\", \"name\": \"affix.testpack.typo\", \"effects\": []}", kind);
    }

    // the right spellings, every effect field and every condition, are fine (and so are the built-in files)
    @Test
    void rightSpellingsAreFine() {
        String json = "{\"kind\": \"suffix\", \"name\": \"affix.testpack.ok\", \"effects\": ["
                + "{\"type\": \"on_hit_effect\", \"effect\": \"minecraft:poison\", \"duration\": 40, \"amplifier\": 1, \"apply_to\": \"self\","
                + " \"tooltip\": \"x\", \"hidden\": false, \"slot\": \"offhand\", \"conditions\": {\"chance\": 0.5, \"health_below\": 0.5,"
                + " \"health_above\": 0.1, \"in_water\": true, \"is_night\": true, \"is_day\": false, \"sneaking\": true, \"on_fire\": false,"
                + " \"y_below\": 60, \"y_above\": 0, \"dimensions\": [\"minecraft:overworld\"], \"biomes\": [\"#minecraft:is_forest\"], \"target\": [\"minecraft:zombie\"], \"target_in_air\": true}},"
                + "{\"type\": \"on_hurt_effect\", \"effect\": \"minecraft:slowness\", \"apply_to\": \"attacker\"},"
                + "{\"type\": \"function\", \"function\": \"mypack:f\", \"trigger\": \"kill\", \"as\": \"target\"},"
                + "{\"type\": \"attribute\", \"attribute\": \"minecraft:generic.armor\", \"operation\": \"add\", \"value\": 1},"
                + "{\"type\": \"damage_taken\", \"value\": -0.1, \"damage\": [\"#minecraft:is_fire\"]},"
                + "{\"type\": \"break_speed\", \"value\": 0.2, \"blocks\": \"#minecraft:mineable/pickaxe\"}]}";
        assertEquals(List.of(), affix(json).errors());
        assertEquals(List.of(), BuiltInData.data().errors());
    }
}
