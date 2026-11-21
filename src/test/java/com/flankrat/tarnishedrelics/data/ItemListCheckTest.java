package com.flankrat.tarnishedrelics.data;

import com.flankrat.tarnishedrelics.BuiltInData;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Item entries in datapack files are checked when the file loads (round 1 fix: a typo used to throw when a
 * chest's loot was rolled). Uses the exact kinds of broken entries a pack author writes.
 */
class ItemListCheckTest {

    @ParameterizedTest
    @ValueSource(strings = {"minecraft:iron_sword", "#minecraft:swords", "somemod:*", "@tarnishedrelics:sword", "*", "iron_sword"})
    void goodEntriesPass(String entry) {
        assertDoesNotThrow(() -> ItemList.check(entry));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Minecraft:Iron_Sword", "minecraft:iron sword", "#Minecraft:Swords", "Some Mod:*", "@tarnishedrelics:Big Sword", "minecraft::sword"})
    void badEntriesThrow(String entry) {
        assertThrows(RuntimeException.class, () -> ItemList.check(entry));
    }

    // the whole file is reported (by its id) and skipped; every other file still loads
    @Test
    void badEntryInARarityIsReportedAtLoad() {
        Map<ResourceLocation, JsonElement> files = new LinkedHashMap<>(BuiltInData.files());
        files.put(new ResourceLocation("testpack", "rarities/broken"), JsonParser.parseString(
                "{\"weight\": 5, \"items\": [\"minecraft:iron_sword\", \"Minecraft:Diamond_Sword\"]}"));
        TRData data = TRData.parseOnly(files);
        assertEquals(1, data.errors().size(), "errors: " + data.errors());
        assertTrue(data.errors().get(0).startsWith("testpack:rarities/broken:"));
        assertFalse(data.rarities().containsKey(new ResourceLocation("testpack", "broken")));
        assertEquals(BuiltInData.data().rarities().size(), data.rarities().size());
    }

    @Test
    void badLevelsItemIsReportedAtLoad() {
        Map<ResourceLocation, JsonElement> files = new LinkedHashMap<>(BuiltInData.files());
        files.put(new ResourceLocation("testpack", "levels/broken"), JsonParser.parseString("{\"items\": {\"minecraft:Iron_Sword\": 3}}"));
        assertTrue(TRData.parseOnly(files).errors().stream().anyMatch(e -> e.startsWith("testpack:levels/broken:")));
    }
}
