package com.flankrat.tarnishedrelics.data;

import com.flankrat.tarnishedrelics.BuiltInData;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Files that name a category by id. A typo there used to fail silently: the legendary never dropped, the affix
 * never rolled, the "@category" entry matched nothing, and /tarnishedrelics errors said all was fine.
 */
class ReferencesTest {

    private static TRData with(String id, String json) {
        Map<ResourceLocation, JsonElement> files = new LinkedHashMap<>(BuiltInData.files());
        files.put(new ResourceLocation("testpack", id), JsonParser.parseString(json));
        return TRData.parseOnly(files);
    }

    private static void reported(TRData data, String file) {
        assertTrue(data.errors().stream().anyMatch(e -> e.startsWith("testpack:" + file + ":") && e.contains("tarnishedrelics:swrod")),
                "errors: " + data.errors());
    }

    @Test
    void legendaryWithAnUnknownCategory() {
        TRData data = with("legendaries/typo", "{\"name\": \"Typo\", \"items\": [\"minecraft:iron_sword\"], \"category\": \"tarnishedrelics:swrod\"}");
        reported(data, "legendaries/typo");
        assertTrue(data.legendaries().containsKey(new ResourceLocation("testpack", "typo")), "the legendary still loads");
    }

    @Test
    void affixForAnUnknownCategory() {
        TRData data = with("affixes/typo", "{\"kind\": \"prefix\", \"applies_to\": [\"tarnishedrelics:sword\", \"tarnishedrelics:swrod\"], \"effects\": []}");
        reported(data, "affixes/typo");
    }

    @Test
    void atCategoryForAnUnknownCategory() {
        TRData data = with("rarities/typo", "{\"weight\": 1, \"items\": [\"@tarnishedrelics:swrod\"]}");
        reported(data, "rarities/typo");
    }

    // "*" (any category) and categories from another file of the same pack are fine
    @Test
    void knownCategoriesAreFine() {
        Map<ResourceLocation, JsonElement> files = new LinkedHashMap<>(BuiltInData.files());
        files.put(new ResourceLocation("testpack", "categories/spear"), JsonParser.parseString("{\"slot\": \"mainhand\", \"items\": [\"minecraft:stick\"]}"));
        files.put(new ResourceLocation("testpack", "affixes/spear"), JsonParser.parseString("{\"applies_to\": [\"testpack:spear\", \"*\"], \"effects\": []}"));
        files.put(new ResourceLocation("testpack", "rarities/spear"), JsonParser.parseString("{\"items\": [\"@testpack:spear\", \"@tarnishedrelics:sword\"]}"));
        assertEquals(java.util.List.of(), TRData.parseOnly(files).errors());
    }

    // turning a category off on purpose doesn't make every file that names it an error
    @Test
    void disabledCategoryIsNotAnError() {
        Map<ResourceLocation, JsonElement> files = new LinkedHashMap<>(BuiltInData.files());
        files.put(new ResourceLocation("tarnishedrelics", "categories/trident"), JsonParser.parseString("{\"disabled\": true}"));
        TRData data = TRData.parseOnly(files);
        assertFalse(data.categories().containsKey(new ResourceLocation("tarnishedrelics", "trident")));
        assertEquals(java.util.List.of(), data.errors());
    }

    @Test
    void exclusionsWithAnUnknownCategory() {
        reported(with("exclusions/typo", "{\"items\": [\"@tarnishedrelics:swrod\"]}"), "exclusions/typo");
    }
}
