package com.flankrat.tarnishedrelics.data;

import com.flankrat.tarnishedrelics.BuiltInData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Item levels from the built-in levels file (tool tier, armor, keywords, items, default). */
class LevelsTest {

    private static int level(Item item) {
        return BuiltInData.data().levels().of(item);
    }

    @Test
    void toolTiers() {
        assertEquals(0, level(Items.WOODEN_SWORD));
        assertEquals(1, level(Items.STONE_PICKAXE));
        assertEquals(2, level(Items.IRON_AXE));
        assertEquals(3, level(Items.DIAMOND_SHOVEL));
        assertEquals(4, level(Items.NETHERITE_SWORD));
    }

    // gold tools are tier level 0, but "golden" in the id is keyword level 1: the highest counts
    @Test
    void keywordBeatsALowerTier() {
        assertEquals(1, level(Items.GOLDEN_SWORD));
    }

    @Test
    void armorByChestplateArmor() {
        assertEquals(0, level(Items.LEATHER_HELMET));
        assertEquals(1, level(Items.CHAINMAIL_BOOTS));
        assertEquals(2, level(Items.IRON_CHESTPLATE));
        assertEquals(3, level(Items.DIAMOND_LEGGINGS));
        assertEquals(4, level(Items.NETHERITE_HELMET));
    }

    @Test
    void itemsEntryWins() {
        assertEquals(3, level(Items.TRIDENT));
    }

    // whole words only: "tin" must not match "tinted"; nothing else applies, so the default (2)
    @Test
    void keywordsAreWholeWords() {
        assertEquals(2, level(Items.TINTED_GLASS));
    }

    @Test
    void defaultWhenNothingApplies() {
        assertEquals(2, level(Items.BOW));
    }
}
