package com.flankrat.tarnishedrelics.data;

import com.flankrat.tarnishedrelics.BuiltInData;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A misspelled key in a rarity, affix, category, legendary, names, levels or exclusions file, and a missing "value" on
 * an effect that needs a number. Effects and conditions already reported their typos; the files around them didn't:
 * the key was skipped, its default used, and the file did something else than written ("signature_enchantment" gave
 * generated legendaries no signature enchantments, a lifesteal without "value" healed nothing).
 */
class FileKeysTest {

    private static List<String> errors(String folder, String json) {
        Map<ResourceLocation, JsonElement> files = new LinkedHashMap<>(BuiltInData.files());
        files.put(new ResourceLocation("testpack", folder + "/typo"), JsonParser.parseString(json));
        return TRData.parseOnly(files).errors();
    }

    private static void reported(String folder, String json, String word) {
        List<String> errors = errors(folder, json);
        assertTrue(errors.stream().anyMatch(e -> e.startsWith("testpack:" + folder + "/typo:") && e.contains(word)), "errors: " + errors);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "rarities|{\"weight\": 1, \"items\": [\"*\"], \"legendary\": true, \"signature_enchantment\": [2, 3]}|signature_enchantment",
            "affixes|{\"pool\": \"normal\", \"applies-to\": [\"tarnishedrelics:sword\"], \"effects\": []}|applies-to",
            "categories|{\"items\": [\"minecraft:stick\"], \"slots\": \"offhand\"}|slots",
            "legendaries|{\"name\": \"Stick\", \"items\": [\"minecraft:stick\"], \"category\": \"tarnishedrelics:sword\", \"enchants\": {}}|enchants",
            "names|{\"name\": [\"Grim\"]}|name",
            "levels|{\"keyword\": {\"iron\": 2}}|keyword",
            "exclusions|{\"item\": [\"minecraft:stick\"]}|item"})
    void misspelledKey(String folder, String json, String word) {
        reported(folder, json, word);
    }

    // the editor switches a file off with "disabled", in any folder
    @Test
    void disabledIsFine() {
        assertTrue(errors("rarities", "{\"disabled\": true}").stream().noneMatch(e -> e.startsWith("testpack:")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"lifesteal", "damage_vs", "damage_taken", "crit_chance", "crit_damage", "thorns", "on_hit_fire",
            "break_speed", "durability", "xp_bonus", "looting"})
    void numberEffectNeedsAValue(String type) {
        reported("affixes", "{\"pool\": \"normal\", \"effects\": [{\"type\": \"" + type + "\"}]}", "value");
    }

    @Test
    void attributeNeedsAValue() {
        reported("affixes", "{\"pool\": \"normal\", \"effects\": [{\"type\": \"attribute\", \"attribute\": \"minecraft:generic.armor\"}]}", "value");
    }

    // effects that aren't a number don't need one
    @Test
    void effectsWithoutANumberAreFine() {
        List<String> errors = errors("affixes", "{\"pool\": \"normal\", \"effects\": [{\"type\": \"on_hit_effect\", \"effect\": \"minecraft:poison\"},"
                + " {\"type\": \"passive_effect\", \"effect\": \"minecraft:speed\"}, {\"type\": \"function\", \"function\": \"mypack:f\"}]}");
        assertTrue(errors.stream().noneMatch(e -> e.startsWith("testpack:")), "errors: " + errors);
    }

    // the built-in data has none of these mistakes
    @Test
    void builtInDataHasNoErrors() {
        assertEquals(List.of(), TRData.parseOnly(BuiltInData.files()).errors());
    }

    // a set bonus without the set's other pieces could never work
    @Test
    void setEffectsNeedCompanions() {
        reported("legendaries", "{\"name\": \"Lonely\", \"items\": [\"minecraft:stick\"], \"category\": \"tarnishedrelics:sword\","
                + " \"set_effects\": [{\"type\": \"lifesteal\", \"value\": 0.1}]}", "companions");
    }
}
