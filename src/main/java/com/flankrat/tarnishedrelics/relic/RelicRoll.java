package com.flankrat.tarnishedrelics.relic;

import com.flankrat.tarnishedrelics.TRConfig;
import com.flankrat.tarnishedrelics.api.RelicGeneratedEvent;
import com.flankrat.tarnishedrelics.data.Affix;
import com.flankrat.tarnishedrelics.data.Category;
import com.flankrat.tarnishedrelics.data.Legendary;
import com.flankrat.tarnishedrelics.data.Rarity;
import com.flankrat.tarnishedrelics.data.TRData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * One drop being rolled: the data, the random source, and where it's from (for RelicGeneratedEvent). Makes the
 * relics {@link Roller} picked: an ordinary one (affixes pool by pool), a generated legendary's extra parts, or a
 * hand-made legendary, and wraps each as an unidentified item. Split out of Roller.
 */
record RelicRoll(TRData data, RandomSource random, RelicGeneratedEvent.Source source, @Nullable LootContext context) {

    /**
     * An ordinary relic (or a generated legendary): a base item, its affixes pool by pool, the name parts.
     * The category is picked first (by its weight), then an item in it, so a mod adding 300 swords doesn't
     * make nearly every relic a sword.
     */
    ItemStack normal(Rarity rarity, @Nullable Category category) {
        // Base item: anything on the rarity's list that has a category (needed for its slot and silhouette)
        Map<Category, List<Item>> bases = new LinkedHashMap<>();
        for (Item item : rarity.items().resolve()) {
            Category c = data.categoryOf(item);
            if (c != null && (category == null || c == category)) bases.computeIfAbsent(c, k -> new ArrayList<>()).add(item);
        }
        Category cat = Weighted.pick(bases.keySet(), Category::weight, random);
        if (cat == null) return ItemStack.EMPTY;
        List<Item> baseItems = bases.get(cat);
        ItemStack real = new ItemStack(baseItems.get(random.nextInt(baseItems.size())));

        CompoundTag relic = relicTag(rarity, cat);
        // Roll affixes pool by pool; the first prefix and first suffix name the item
        ListTag affixes = new ListTag();
        Set<ResourceLocation> taken = new HashSet<>();
        Set<String> groups = new HashSet<>();
        for (Map.Entry<String, int[]> pool : rarity.rolls().entrySet()) {
            int count = Rarity.roll(pool.getValue(), random);
            for (int i = 0; i < count; i++) {
                Affix a = pickAffix(rarity, cat, pool.getKey(), taken, groups);
                if (a == null) break;   // nothing left that fits
                taken.add(a.id());
                if (a.group() != null) groups.add(a.group());
                affixes.add(affixEntry(a, rarity));
                if (a.nameKey() != null) {
                    if (a.kind().equals("prefix") && !relic.contains("Prefix")) relic.putString("Prefix", a.nameKey());
                    if (a.kind().equals("suffix") && !relic.contains("Suffix")) relic.putString("Suffix", a.nameKey());
                }
            }
        }
        if (rarity.legendary()) {
            generatedLegendary(rarity, cat, real, relic, affixes, taken, groups);
        } else if (rarity.uniqueName()) {
            relic.putString("UniqueName", data.names().generate(random));
        }
        relic.put("Affixes", affixes);
        return finish(real, relic, rarity, cat);
    }

    /**
     * The extra parts of a generated legendary: a drawback (maybe), a generated name and lore, and signature
     * enchantments: ones that fit the item, put on above their normal max.
     */
    private void generatedLegendary(Rarity rarity, Category cat, ItemStack real, CompoundTag relic, ListTag affixes,
                                    Set<ResourceLocation> taken, Set<String> groups) {
        if (random.nextDouble() < rarity.drawbackChance()) {
            Affix drawback = pickAffix(rarity, cat, rarity.drawbackPool(), taken, groups);
            if (drawback != null) affixes.add(affixEntry(drawback, rarity));
        }
        String name = data.names().generateLegendary(random);
        relic.putString("LegendaryName", name);
        relic.remove("Prefix");
        relic.remove("Suffix");
        relic.put("Lore", strings(data.names().lore(random, Rarity.roll(rarity.loreLines(), random), name)));

        // signature enchantments: any that fit the item (canEnchant, which other mods' item restrictions are part of),
        // not curses, not ones the item already has. Only loot ones: isDiscoverable false keeps an enchantment out of
        // loot and enchanting tables (Soul Speed, Swift Sneak; other mods' enchantments that come from their own
        // crafting). Legendaries may break the usual exclusions: Sharpness and Smite can go on one sword.
        Map<Enchantment, Integer> ench = new LinkedHashMap<>(EnchantmentHelper.getEnchantments(real));
        List<Enchantment> options = new ArrayList<>();
        for (Enchantment e : ForgeRegistries.ENCHANTMENTS) {
            if (!e.isCurse() && e.isDiscoverable() && !ench.containsKey(e) && e.canEnchant(real)) options.add(e);
        }
        int count = Rarity.roll(rarity.signatureEnchantments(), random);
        for (; count > 0 && !options.isEmpty(); count--) {
            Enchantment e = options.remove(random.nextInt(options.size()));
            // single-level enchantments (Mending, Silk Touch...) do nothing at higher levels, so they stay at 1
            ench.put(e, e.getMaxLevel() <= 1 ? 1 : e.getMaxLevel() + Rarity.roll(rarity.enchantmentBonus(), random));
        }
        EnchantmentHelper.setEnchantments(ench, real);
    }

    /**
     * One hand-made legendary: its base item, its fixed name/lore/enchantments, and one 0-1 roll for all its
     * value ranges. Its effects are stored like an affix (the legendary's hidden affix), so they work like any
     * other relic's. category (may be null): only base items of that category.
     */
    ItemStack handmade(Rarity rarity, Legendary l, @Nullable Category category) {
        List<Item> items = new ArrayList<>(l.items().resolve());
        // asked for a category: only the base items in it (unless the legendary sets its own category)
        if (category != null && l.category() == null) items.removeIf(i -> data.categoryOf(i) != category);
        if (items.isEmpty()) return ItemStack.EMPTY;
        ItemStack real = new ItemStack(items.get(random.nextInt(items.size())));
        Category cat = Roller.legendaryCategory(data, l, real.getItem());
        if (cat == null) return ItemStack.EMPTY;   // no slot/silhouette for it: the datapack must set "category"

        // signature enchantments, as they are (above the normal max, any combination)
        Map<Enchantment, Integer> ench = new LinkedHashMap<>(EnchantmentHelper.getEnchantments(real));
        l.enchantments().forEach((id, level) -> {
            Enchantment e = ForgeRegistries.ENCHANTMENTS.getValue(ResourceLocation.tryParse(id));
            if (e != null) ench.put(e, level);   // enchantments from a mod that isn't installed are skipped
        });
        EnchantmentHelper.setEnchantments(ench, real);

        CompoundTag relic = relicTag(rarity, cat);
        relic.putString("Legendary", l.id().toString());
        relic.putString("LegendaryName", l.name());
        relic.put("Lore", strings(l.lore()));
        ListTag affixes = new ListTag();
        affixes.add(affixEntry(l.affix(), rarity));
        relic.put("Affixes", affixes);
        return finish(real, relic, rarity, cat);
    }

    // the stored relic data every relic starts with (see RelicTag)
    private static CompoundTag relicTag(Rarity rarity, Category cat) {
        CompoundTag relic = new CompoundTag();
        relic.putString("Rarity", rarity.id().toString());
        relic.putString("Category", cat.id().toString());
        relic.putString("Slot", cat.slot().getName());
        relic.putInt("Color", rarity.color());
        return relic;
    }

    // one rolled affix: its id and a 0-1 roll, pushed toward its good end by the looter's Luck (see LuckRoll)
    private CompoundTag affixEntry(Affix affix, Rarity rarity) {
        CompoundTag entry = new CompoundTag();
        entry.putString("Id", affix.id().toString());
        float roll = random.nextFloat();
        float luck = context != null ? context.getLuck() : 0;   // commands and the editor have no looter: no Luck
        if (luck != 0) roll = LuckRoll.apply(roll, luck, TRConfig.LUCK_ROLL_BONUS.get(), LuckRoll.direction(affix, rarity.id()));
        entry.putFloat("Roll", roll);
        return entry;
    }

    private static ListTag strings(List<String> lines) {
        ListTag list = new ListTag();
        for (String line : lines) list.add(StringTag.valueOf(line));
        return list;
    }

    // stores the relic data, lets other mods add their rolls (sockets etc.) to the real item, then hides it
    private ItemStack finish(ItemStack real, CompoundTag relic, Rarity rarity, Category cat) {
        real.getOrCreateTag().put(RelicTag.KEY, relic);
        MinecraftForge.EVENT_BUS.post(new RelicGeneratedEvent(real, rarity, source, context));
        return Roller.wrap(real, rarity, cat, random);
    }

    @Nullable
    private Affix pickAffix(Rarity rarity, Category cat, String pool, Set<ResourceLocation> taken, Set<String> groups) {
        List<Affix> candidates = new ArrayList<>();
        for (Affix a : data.affixes().values()) {
            if (!a.pool().equals(pool) || taken.contains(a.id()) || !a.appliesTo(cat.id())) continue;
            if (a.group() != null && groups.contains(a.group())) continue;
            if (a.weightFor(rarity.id()) > 0) candidates.add(a);
        }
        return Weighted.pick(candidates, a -> a.weightFor(rarity.id()), random);
    }
}
