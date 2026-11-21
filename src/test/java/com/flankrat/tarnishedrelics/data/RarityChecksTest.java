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

/** Rarity values that loaded but made the rarity quietly useless or wrong. */
class RarityChecksTest {

    private static TRData rarity(String json) {
        Map<ResourceLocation, JsonElement> files = new LinkedHashMap<>(BuiltInData.files());
        files.put(new ResourceLocation("testpack", "rarities/odd"), JsonParser.parseString(json));
        return TRData.parseOnly(files);
    }

    private static void reported(String json, String word) {
        List<String> errors = rarity(json).errors();
        assertTrue(errors.stream().anyMatch(e -> e.startsWith("testpack:rarities/odd:") && e.contains(word)), "errors: " + errors);
    }

    // villagers only go up to level 5 (Master): a relic that needs 6 could never be identified
    @Test
    void identifyLevelAboveMaster() {
        reported("{\"weight\": 5, \"items\": [\"*\"], \"identify_level\": 6}", "identify_level");
    }

    // "#FFF" was read as 0x000FFF (dark blue), not white
    @ParameterizedTest
    @ValueSource(strings = {"#FFF", "#12345", "#1234567", "red"})
    void colorThatIsntSixHexDigits(String color) {
        reported("{\"weight\": 5, \"items\": [\"*\"], \"color\": \"" + color + "\"}", "color");
    }

    // [3, 1] matched no item level at all, so the rarity never dropped anything
    @Test
    void levelsTheWrongWayRound() {
        reported("{\"weight\": 5, \"items\": [\"*\"], \"levels\": [3, 1]}", "levels");
    }

    @Test
    void costTheWrongWayRound() {
        reported("{\"weight\": 5, \"items\": [\"*\"], \"cost\": [9, 2]}", "cost");
    }

    @Test
    void sensibleValuesAreFine() {
        assertEquals(List.of(), rarity("{\"weight\": 5, \"items\": [\"*\"], \"identify_level\": 5, \"color\": \"#AABBCC\", \"levels\": [2, 2], \"cost\": [3, 3], \"rolls\": {\"normal\": [1, 3]}}").errors());
    }

    // Bug (round 8): per-rarity values without "default" give 0 to rarities not listed. Generated legendaries roll
    // "normal" affixes, and the built-in ones only listed magic, rare and unique, so a legendary's normal affixes did
    // nothing ("+0% critical hit chance"). An affix with no value for a rarity that can roll it is reported.
    @Test
    void affixWithNoValueForARarityThatRollsIt() {
        Map<ResourceLocation, JsonElement> files = new LinkedHashMap<>(BuiltInData.files());
        files.put(new ResourceLocation("testpack", "affixes/half"), JsonParser.parseString(
                "{\"kind\": \"prefix\", \"name\": \"Half\", \"effects\": [{\"type\": \"crit_chance\", \"value\": {\"magic\": 0.1}}]}"));
        List<String> errors = TRData.parseOnly(files).errors();
        assertTrue(errors.stream().anyMatch(e -> e.startsWith("testpack:affixes/half:") && e.contains("tarnishedrelics:rare")), "errors: " + errors);
        assertTrue(errors.stream().noneMatch(e -> e.startsWith("testpack:affixes/half:") && e.contains("tarnishedrelics:magic")), "magic has a value: " + errors);
    }

    // a fixed number, a "default", or a value for every rarity that rolls it are fine (and so are the built-in files)
    @Test
    void affixValuesForEveryRarityAreFine() {
        Map<ResourceLocation, JsonElement> files = new LinkedHashMap<>(BuiltInData.files());
        files.put(new ResourceLocation("testpack", "affixes/whole"), JsonParser.parseString(
                "{\"kind\": \"prefix\", \"name\": \"Whole\", \"effects\": [{\"type\": \"crit_chance\", \"value\": {\"magic\": 0.1, \"default\": 0.2}},"
                        + " {\"type\": \"crit_damage\", \"value\": 0.3}]}"));
        assertEquals(List.of(), TRData.parseOnly(files).errors());
    }

    // the bug itself, in the built-in data: every normal affix a legendary can roll does something on a legendary
    @Test
    void builtInNormalAffixesWorkOnLegendaries() {
        TRData data = BuiltInData.data();
        ResourceLocation legendary = new ResourceLocation("tarnishedrelics", "legendary");
        List<String> zero = new java.util.ArrayList<>();
        for (Affix a : data.affixes().values()) {
            if (!a.pool().equals("normal") || a.weightFor(legendary) <= 0) continue;
            for (Effect e : a.effects()) {
                if (!e.json.has("value")) continue;
                ValueSpec.Range r = e.value.rangeFor(legendary);
                if (r.min() == 0 && r.max() == 0) zero.add(a.id() + " " + e.type.key());
            }
        }
        assertEquals(List.of(), zero, "normal affixes that do nothing on a legendary");
    }
}
