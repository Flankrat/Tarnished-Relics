package com.flankrat.tarnishedrelics.data;

import com.flankrat.tarnishedrelics.BuiltInData;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The exclusions lists ("these items are never relics"), on the built-in data plus one more file. */
class ExclusionsTest {

    private static TRData withExclusions(String json) {
        Map<ResourceLocation, JsonElement> files = new LinkedHashMap<>(BuiltInData.files());
        files.put(new ResourceLocation("testpack", "exclusions/test"), JsonParser.parseString(json));
        return TRData.parseOnly(files);
    }

    @Test
    void excludedItemHasNoCategory() {
        TRData data = withExclusions("{\"items\": [\"minecraft:iron_sword\"]}");
        assertNull(data.categoryOf(Items.IRON_SWORD));
        assertNotNull(data.categoryOf(Items.DIAMOND_SWORD));
    }

    @Test
    void modWildcardExcludesTheWholeMod() {
        TRData data = withExclusions("{\"items\": [\"minecraft:*\"]}");
        assertNull(data.categoryOf(Items.DIAMOND_SWORD));
    }

    // Bug (round 2): "*" means "every item that has a category", which asks categoryOf, which asks the exclusions:
    // categoryOf crashed with "Recursive update" from its cache. A pack author could write it, so it must be
    // reported as a bad file instead of crashing every loot roll.
    @Test
    void starInExclusionsIsReportedNotACrash() {
        TRData data = withExclusions("{\"items\": [\"*\"]}");
        assertDoesNotThrow(() -> data.categoryOf(Items.IRON_SWORD));
        assertTrue(data.errors().stream().anyMatch(e -> e.startsWith("testpack:exclusions/test:")), "errors: " + data.errors());
    }

    // Same loop through a category: "*" in a category's items asks categoryOf, which asks every category's items
    @Test
    void starInACategoryIsReportedNotACrash() {
        Map<ResourceLocation, JsonElement> files = new LinkedHashMap<>(BuiltInData.files());
        files.put(new ResourceLocation("testpack", "categories/everything"), JsonParser.parseString("{\"slot\": \"mainhand\", \"items\": [\"*\"]}"));
        TRData data = TRData.parseOnly(files);
        assertDoesNotThrow(() -> data.categoryOf(Items.IRON_SWORD));
        assertTrue(data.errors().stream().anyMatch(e -> e.startsWith("testpack:categories/everything:")), "errors: " + data.errors());
    }
}
