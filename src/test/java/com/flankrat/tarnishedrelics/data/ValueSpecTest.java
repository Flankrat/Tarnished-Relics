package com.flankrat.tarnishedrelics.data;

import com.flankrat.tarnishedrelics.BuiltInData;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Values stored as a 0-1 roll and scaled into the datapack's range when used. */
class ValueSpecTest {

    private static final ResourceLocation MAGIC = new ResourceLocation("tarnishedrelics", "magic");
    private static final ResourceLocation RARE = new ResourceLocation("tarnishedrelics", "rare");
    private static final ResourceLocation OTHER = new ResourceLocation("othermod", "epic");

    private static ValueSpec spec(String json) {
        return ValueSpec.parse(JsonParser.parseString(json));
    }

    @Test
    void fixedNumberIgnoresTheRoll() {
        assertEquals(3, spec("3").get(MAGIC, 0), 1e-9);
        assertEquals(3, spec("3").get(MAGIC, 1), 1e-9);
    }

    @Test
    void rangeScalesTheRoll() {
        ValueSpec s = spec("[2, 6]");
        assertEquals(2, s.get(MAGIC, 0), 1e-9);
        assertEquals(4, s.get(MAGIC, 0.5), 1e-9);
        assertEquals(6, s.get(MAGIC, 1), 1e-9);
    }

    @Test
    void negativeRangeScalesTheSameWay() {
        assertEquals(-0.15, spec("[-0.1, -0.2]").get(MAGIC, 0.5), 1e-9);
    }

    @Test
    void perRarityByPathOrFullIdWithDefault() {
        ValueSpec s = spec("{\"magic\": [1, 2], \"tarnishedrelics:rare\": [10, 20], \"default\": 7}");
        assertEquals(1.5, s.get(MAGIC, 0.5), 1e-9);
        assertEquals(15, s.get(RARE, 0.5), 1e-9);
        assertEquals(7, s.get(OTHER, 0.5), 1e-9);
    }

    @Test
    void perRarityWithoutDefaultIsZeroForOthers() {
        assertEquals(0, spec("{\"magic\": [1, 2]}").get(RARE, 1), 1e-9);
    }

    @Test
    void rangeMustHaveTwoNumbers() {
        assertThrows(RuntimeException.class, () -> spec("[1, 2, 3]"));
    }

    // a real built-in affix: efficient's break_speed is per rarity
    @Test
    void builtInEfficientUsesItsRarityRange() {
        var effect = BuiltInData.file("affixes/efficient").getAsJsonObject().getAsJsonArray("effects").get(0).getAsJsonObject();
        ValueSpec s = ValueSpec.parse(effect.get("value"));
        assertEquals(0.1, s.get(MAGIC, 0), 1e-9);
        assertEquals(0.35, s.get(RARE, 1), 1e-9);
    }
}
