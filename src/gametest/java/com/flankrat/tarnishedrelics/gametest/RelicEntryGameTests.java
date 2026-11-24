package com.flankrat.tarnishedrelics.gametest;

import com.flankrat.tarnishedrelics.TRItems;
import com.flankrat.tarnishedrelics.TarnishedRelics;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.flankrat.tarnishedrelics.gametest.TestUtil.tr;

/**
 * The "tarnishedrelics:relic" loot table entry, rolled from real loot tables loaded from the test datapack
 * (data/tarnishedrelics/loot_tables/test/*.json) against the built-in rarities.
 */
@GameTestHolder(TarnishedRelics.MODID)
@PrefixGameTestTemplate(false)
public class RelicEntryGameTests {

    private static List<ItemStack> roll(GameTestHelper helper, String table) {
        LootTable t = helper.getLevel().getServer().getLootData().getLootTable(tr("test/" + table));
        helper.assertTrue(t != LootTable.EMPTY, "test loot table " + table + " didn't load");
        return t.getRandomItems(new LootParams.Builder(helper.getLevel()).create(LootContextParamSets.EMPTY));
    }

    // the relic's rarity, read from the unidentified item; fails on anything that isn't an unidentified relic
    private static String rarityOf(GameTestHelper helper, ItemStack stack) {
        helper.assertTrue(stack.is(TRItems.UNIDENTIFIED.get()), "not an unidentified relic: " + stack);
        return stack.getOrCreateTag().getString("Rarity");
    }

    // "rarities": [rare, unique, legendary]: every roll drops a relic, never a magic one; all three show up
    @GameTest(template = "gametest/empty")
    public static void listedRaritiesOnly(GameTestHelper helper) {
        Set<String> allowed = Set.of(tr("rare").toString(), tr("unique").toString(), tr("legendary").toString());
        Map<String, Integer> seen = new HashMap<>();
        for (int i = 0; i < 600; i++) {
            List<ItemStack> drop = roll(helper, "relic_rare_up");
            helper.assertFalse(drop.isEmpty(), "roll " + i + " dropped nothing");
            for (ItemStack s : drop) {
                String r = rarityOf(helper, s);
                helper.assertTrue(allowed.contains(r), "rarity outside the list: " + r);
                seen.merge(r, 1, Integer::sum);
            }
        }
        for (String r : allowed) helper.assertTrue(seen.containsKey(r), "never rolled " + r + ": " + seen);
        helper.succeed();
    }

    // no "rarities": a relic every roll, any rarity (magic, the most common, included)
    @GameTest(template = "gametest/empty")
    public static void anyRarityWithoutList(GameTestHelper helper) {
        boolean magic = false;
        for (int i = 0; i < 400; i++) {
            List<ItemStack> drop = roll(helper, "relic_any");
            helper.assertFalse(drop.isEmpty(), "roll " + i + " dropped nothing");
            for (ItemStack s : drop) magic |= rarityOf(helper, s).equals(tr("magic").toString());
        }
        helper.assertTrue(magic, "400 rolls without a magic relic");
        helper.succeed();
    }

    // a rarity that doesn't exist: the table still loads, and the entry drops nothing instead of failing
    @GameTest(template = "gametest/empty")
    public static void unknownRarityDropsNothing(GameTestHelper helper) {
        for (int i = 0; i < 20; i++) {
            helper.assertTrue(roll(helper, "relic_unknown_rarity").isEmpty(), "dropped something for an unknown rarity");
        }
        helper.succeed();
    }
}
