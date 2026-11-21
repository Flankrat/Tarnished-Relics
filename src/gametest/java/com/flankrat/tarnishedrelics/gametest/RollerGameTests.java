package com.flankrat.tarnishedrelics.gametest;

import com.flankrat.tarnishedrelics.TarnishedRelics;
import com.flankrat.tarnishedrelics.api.RelicGeneratedEvent;
import com.flankrat.tarnishedrelics.data.Affix;
import com.flankrat.tarnishedrelics.data.Rarity;
import com.flankrat.tarnishedrelics.data.TRData;
import com.flankrat.tarnishedrelics.relic.RelicTag;
import com.flankrat.tarnishedrelics.relic.Roller;
import com.flankrat.tarnishedrelics.relic.UnidentifiedItem;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.Set;

import static com.flankrat.tarnishedrelics.gametest.TestUtil.tr;

/** Rolling relics from the real built-in data (needs the game: the unidentified item is a registered item). */
@GameTestHolder(TarnishedRelics.MODID)
@PrefixGameTestTemplate(false)
public class RollerGameTests {

    private static Rarity rarity(String name) {
        return TRData.get().rarities().get(tr(name));
    }

    private static CompoundTag relicOf(ItemStack wrapper) {
        return RelicTag.get(UnidentifiedItem.hidden(wrapper));
    }

    // never the same affix twice, never two of one group, every affix fits the category, rolls in [0, 1)
    @GameTest(template = "gametest/empty")
    public static void groupsAndDuplicatesAreExcluded(GameTestHelper helper) {
        RandomSource random = RandomSource.create(1234);
        for (String r : new String[]{"magic", "rare", "unique", "legendary"}) {
            for (int i = 0; i < 300; i++) {
                ItemStack wrapper = Roller.generate(random, rarity(r), null);
                helper.assertFalse(wrapper.isEmpty(), "nothing made for " + r);
                CompoundTag relic = relicOf(wrapper);
                Set<ResourceLocation> ids = new HashSet<>();
                Set<String> groups = new HashSet<>();
                ListTag list = relic.getList("Affixes", Tag.TAG_COMPOUND);
                ResourceLocation category = new ResourceLocation(relic.getString("Category"));
                for (int k = 0; k < list.size(); k++) {
                    ResourceLocation id = new ResourceLocation(list.getCompound(k).getString("Id"));
                    helper.assertTrue(ids.add(id), "same affix twice: " + relic);
                    Affix a = TRData.get().affixes().get(id);
                    helper.assertTrue(a != null, "unknown affix " + id);
                    if (a.group() != null) helper.assertTrue(groups.add(a.group()), "two of group " + a.group() + ": " + relic);
                    helper.assertTrue(a.appliesTo(category) || id.getPath().startsWith("legendary/"), id + " on " + category);
                    float roll = list.getCompound(k).getFloat("Roll");
                    helper.assertTrue(roll >= 0 && roll < 1, "roll " + roll);
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "gametest/empty")
    public static void magicRollsOneOrTwo(GameTestHelper helper) {
        RandomSource random = RandomSource.create(99);
        for (int i = 0; i < 200; i++) {
            int n = relicOf(Roller.generate(random, rarity("magic"), null)).getList("Affixes", Tag.TAG_COMPOUND).size();
            helper.assertTrue(n >= 1 && n <= 2, "magic with " + n + " affixes");
        }
        helper.succeed();
    }

    // the wrapper carries what the smith needs from the rarity
    @GameTest(template = "gametest/empty")
    public static void wrapperHasTheRarityPriceAndLevel(GameTestHelper helper) {
        ItemStack wrapper = Roller.generate(RandomSource.create(5), rarity("rare"), null);
        CompoundTag tag = wrapper.getTag();
        helper.assertTrue(tag.getInt("Level") == 3, "level " + tag.getInt("Level"));
        helper.assertTrue(tag.getInt("Xp") == 15, "xp " + tag.getInt("Xp"));
        helper.assertTrue(tag.getString("Rarity").equals("tarnishedrelics:rare"), tag.getString("Rarity"));
        int cost = tag.getInt("Cost");
        helper.assertTrue(cost >= rarity("rare").costMin() && cost <= rarity("rare").costMax(), "cost " + cost);
        helper.succeed();
    }

    @GameTest(template = "gametest/empty")
    public static void uniquesGetAGeneratedName(GameTestHelper helper) {
        helper.assertFalse(relicOf(Roller.generate(RandomSource.create(3), rarity("unique"), null)).getString("UniqueName").isEmpty(), "no unique name");
        helper.succeed();
    }

    @GameTest(template = "gametest/empty")
    public static void legendariesHaveANameAndNoAffixNameParts(GameTestHelper helper) {
        RandomSource random = RandomSource.create(8);
        for (int i = 0; i < 50; i++) {
            CompoundTag relic = relicOf(Roller.generate(random, rarity("legendary"), null));
            helper.assertFalse(relic.getString("LegendaryName").isEmpty(), "no legendary name");
            helper.assertFalse(relic.contains("Prefix") || relic.contains("Suffix"), "legendary with affix name parts: " + relic);
        }
        helper.succeed();
    }

    // each hand-made legendary can be made, carrying its hidden affix; a set drops its companions too
    @GameTest(template = "gametest/empty")
    public static void everyHandmadeLegendaryCanBeMade(GameTestHelper helper) {
        RandomSource random = RandomSource.create(11);
        for (var l : TRData.get().legendaries().values()) {
            var all = Roller.generateAll(random, null, null, l, RelicGeneratedEvent.Source.COMMAND, null);
            helper.assertFalse(all.isEmpty(), "couldn't make " + l.id());
            CompoundTag relic = relicOf(all.get(0));
            helper.assertTrue(relic.getString("Legendary").equals(l.id().toString()), l.id() + ": " + relic);
            helper.assertTrue(relic.getList("Affixes", Tag.TAG_COMPOUND).getCompound(0).getString("Id").equals(l.affix().id().toString()), l.id() + " affix");
            helper.assertTrue(all.size() == 1 + l.companions().size(), l.id() + " set size " + all.size());
        }
        helper.succeed();
    }
}
