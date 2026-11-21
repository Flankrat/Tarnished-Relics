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
}
