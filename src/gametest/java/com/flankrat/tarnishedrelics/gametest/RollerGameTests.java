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

import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.HashSet;
import java.util.Map;
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

    // the real item of a generated (not hand-made) legendary of a category, or null for a hand-made one
    private static ItemStack generatedLegendary(RandomSource random, String category) {
        ItemStack real = UnidentifiedItem.hidden(Roller.generate(random, rarity("legendary"), TRData.get().categories().get(tr(category))));
        CompoundTag relic = RelicTag.get(real);
        return relic.contains("Legendary") ? null : real;
    }

    // Signature enchantments are loot: never one the game keeps out of loot and enchanting tables (isDiscoverable
    // false, e.g. Soul Speed and Swift Sneak, only found in their structures; other mods hide enchantments that come
    // from their own crafting the same way)
    @GameTest(template = "gametest/empty")
    public static void legendarySignatureEnchantmentsAreDiscoverable(GameTestHelper helper) {
        RandomSource random = RandomSource.create(21);
        for (String category : new String[]{"boots", "leggings"}) {
            for (int i = 0; i < 300; i++) {
                ItemStack real = generatedLegendary(random, category);
                if (real == null) continue;
                for (Enchantment e : EnchantmentHelper.getEnchantments(real).keySet()) {
                    helper.assertTrue(e.isDiscoverable(), "legendary " + category + " with " + e.getDescriptionId() + ", which isn't loot: " + real.getTag());
                }
            }
        }
        helper.succeed();
    }

    // Legendaries may break the usual enchantment exclusions (Sharpness with Smite...): any enchantment that goes on
    // the item can be picked, so some legendary swords get two that an enchanting table never puts together
    @GameTest(template = "gametest/empty")
    public static void legendarySignatureEnchantmentsIgnoreExclusions(GameTestHelper helper) {
        RandomSource random = RandomSource.create(22);
        int together = 0;
        for (int i = 0; i < 300; i++) {
            ItemStack real = generatedLegendary(random, "sword");
            if (real == null) continue;
            Map<Enchantment, Integer> ench = EnchantmentHelper.getEnchantments(real);
            for (Enchantment e : ench.keySet()) {
                helper.assertTrue(e.canEnchant(real), e.getDescriptionId() + " on a " + real.getItem() + ", which can't take it");
            }
            int damage = 0;
            for (Enchantment e : new Enchantment[]{Enchantments.SHARPNESS, Enchantments.SMITE, Enchantments.BANE_OF_ARTHROPODS}) {
                if (ench.containsKey(e)) damage++;
            }
            if (damage >= 2) together++;
        }
        helper.assertTrue(together > 0, "no legendary sword out of 300 got two of Sharpness, Smite and Bane of Arthropods");
        helper.succeed();
    }

    // Every kind of relic gets as many affixes as its tier asks for: a category with too few affixes that fit it (and
    // the tier) just gets fewer, so a "Rare" hoe could have no more affixes than a Magic one. Built-in data, every
    // built-in category, the most each tier can roll per pool.
    @GameTest(template = "gametest/empty", timeoutTicks = 400)
    public static void everyCategoryCanFillEveryTier(GameTestHelper helper) {
        RandomSource random = RandomSource.create(31);
        StringBuilder short_ = new StringBuilder();
        for (var cat : TRData.get().categories().values()) {
            for (String r : new String[]{"magic", "rare", "unique"}) {
                Rarity rarity = rarity(r);
                int want = rarity.rolls().values().stream().mapToInt(x -> x[1]).sum();
                int most = 0;
                for (int i = 0; i < 200; i++) {
                    CompoundTag relic = relicOf(Roller.generate(random, rarity, cat));
                    if (relic != null) most = Math.max(most, relic.getList("Affixes", Tag.TAG_COMPOUND).size());
                }
                if (most > 0 && most < want) short_.append(cat.id().getPath()).append(" ").append(r).append(": at most ").append(most)
                        .append(" of ").append(want).append("; ");
            }
        }
        helper.assertTrue(short_.isEmpty(), "categories that can't fill a tier: " + short_);
        helper.succeed();
    }

    // every attribute a built-in affix or legendary gives exists (a missing one is skipped without a word in play)
    @GameTest(template = "gametest/empty")
    public static void builtInAttributesExist(GameTestHelper helper) {
        StringBuilder missing = new StringBuilder();
        for (var a : TRData.get().affixes().values()) {
            if (!a.id().getNamespace().equals(TarnishedRelics.MODID)) continue;
            for (var e : a.effects()) {
                if (e.attribute != null && !net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES.containsKey(e.attribute)) {
                    missing.append(a.id()).append(": ").append(e.attribute).append("; ");
                }
            }
        }
        helper.assertTrue(missing.isEmpty(), "attributes that don't exist: " + missing);
        helper.succeed();
    }
}
