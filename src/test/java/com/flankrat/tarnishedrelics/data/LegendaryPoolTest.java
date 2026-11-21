package com.flankrat.tarnishedrelics.data;

import com.flankrat.tarnishedrelics.BuiltInData;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A generated legendary rolls 2 affixes from the legendary pool that apply to its category. With only 2 or 3 in a
 * category's pool, every legendary pickaxe, shovel, hoe and shield got the same pair. At least 4 per category gives
 * 6 or more different pairs.
 */
class LegendaryPoolTest {

    private static final int MIN = 4;

    @Test
    void everyCategoryHasEnoughLegendaryAffixes() {
        TRData data = BuiltInData.data();
        assertEquals(java.util.List.of(), data.errors(), "built-in data loads without errors");
        Map<String, Integer> tooFew = new TreeMap<>();
        for (ResourceLocation category : data.categories().keySet()) {
            int count = 0;
            for (Affix a : data.affixes().values()) {
                if (a.pool().equals("legendary") && a.appliesTo(category)) count++;
            }
            if (count < MIN) tooFew.put(category.toString(), count);
        }
        assertEquals(Map.of(), tooFew, "categories with fewer than " + MIN + " legendary affixes");
    }
}
