package com.flankrat.tarnishedrelics.relic;

import com.flankrat.tarnishedrelics.TRItems;
import com.flankrat.tarnishedrelics.api.RelicGeneratedEvent;
import com.flankrat.tarnishedrelics.data.Affix;
import com.flankrat.tarnishedrelics.data.Category;
import com.flankrat.tarnishedrelics.data.Legendary;
import com.flankrat.tarnishedrelics.data.Rarity;
import com.flankrat.tarnishedrelics.data.TRData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraftforge.common.MinecraftForge;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Makes relics: picks a rarity, a base item from that rarity's list, rolls its affixes, and wraps it as an
 * unidentified item.
 */
public final class Roller {

    private Roller() {
    }

    /**
     * A new unidentified relic, or EMPTY when the datapack has nothing usable.
     * rarity/category may be null for "random" (the commands use them).
     */
    public static ItemStack generate(RandomSource random, @Nullable Rarity rarity, @Nullable Category category) {
        return generate(random, rarity, category, RelicGeneratedEvent.Source.COMMAND, null);
    }

    /** Same, telling RelicGeneratedEvent listeners where the relic came from. Only the first relic of a set. */
    public static ItemStack generate(RandomSource random, @Nullable Rarity rarity, @Nullable Category category,
                                     RelicGeneratedEvent.Source source, @Nullable LootContext context) {
        List<ItemStack> all = generateAll(random, rarity, category, null, source, context);
        return all.isEmpty() ? ItemStack.EMPTY : all.get(0);
    }

    /**
     * Relics for one drop: usually one, more when a legendary drops with its companions (a set). Empty when the
     * datapack has nothing usable. legendary: a specific legendary to make (null = roll as usual).
     */
    public static List<ItemStack> generateAll(RandomSource random, @Nullable Rarity rarity, @Nullable Category category,
                                              @Nullable Legendary legendary, RelicGeneratedEvent.Source source, @Nullable LootContext context) {
        TRData data = TRData.get();
        if (legendary != null && rarity == null) rarity = legendaryRarity(data);
        if (rarity == null) {
            float luck = context != null ? context.getLuck() : 0;
            rarity = pickRarity(data, random, category, luck, context != null ? context.getQueriedLootTableId() : null);
        }
        if (rarity == null) return List.of();
        if (rarity.legendary()) {
            if (legendary == null) legendary = pickLegendary(data, random, category);
            if (legendary == null) return List.of();
            List<ItemStack> out = new ArrayList<>();
            ItemStack main = makeLegendary(data, random, rarity, legendary, source, context);
            if (main.isEmpty()) return List.of();
            out.add(main);
            // the rest of the set drops with it
            for (ResourceLocation c : legendary.companions()) {
                Legendary companion = data.legendaries().get(c);
                if (companion == null || companion == legendary) continue;
                ItemStack extra = makeLegendary(data, random, rarity, companion, source, context);
                if (!extra.isEmpty()) out.add(extra);
            }
            return out;
        }
        ItemStack one = rollNormal(data, random, rarity, category, source, context);
        return one.isEmpty() ? List.of() : List.of(one);
    }

    @Nullable
    private static Rarity legendaryRarity(TRData data) {
        for (Rarity r : data.rarities().values()) if (r.legendary()) return r;
        return null;
    }

    // a random legendary that can drop on its own (weight > 0), of the asked category if one was asked for
    @Nullable
    private static Legendary pickLegendary(TRData data, RandomSource random, @Nullable Category category) {
        List<Legendary> usable = new ArrayList<>();
        int total = 0;
        for (Legendary l : data.legendaries().values()) {
            if (l.weight() <= 0) continue;
            if (category != null && legendaryCategory(data, l, firstItem(l)) != category) continue;
            if (firstItem(l) == null) continue;
            usable.add(l);
            total += l.weight();
        }
        if (total <= 0) return null;
        int pick = random.nextInt(total);
        for (Legendary l : usable) {
            pick -= l.weight();
            if (pick < 0) return l;
        }
        return null;
    }

    @Nullable
    private static Item firstItem(Legendary l) {
        var items = l.items().resolve();
        return items.isEmpty() ? null : items.iterator().next();
    }

    @Nullable
    private static Category legendaryCategory(TRData data, Legendary l, @Nullable Item item) {
        if (l.category() != null) return data.categories().get(l.category());
        return item == null ? null : data.categoryOf(item);
    }

    /**
     * One legendary: its base item, its fixed name/lore/enchantments, and one 0-1 roll for all its value ranges.
     * Its effects are stored like an affix (the legendary's hidden affix), so they work like any other relic's.
     */
    private static ItemStack makeLegendary(TRData data, RandomSource random, Rarity rarity, Legendary l,
                                           RelicGeneratedEvent.Source source, @Nullable LootContext context) {
        List<Item> items = new ArrayList<>(l.items().resolve());
        if (items.isEmpty()) return ItemStack.EMPTY;
        ItemStack real = new ItemStack(items.get(random.nextInt(items.size())));
        Category cat = legendaryCategory(data, l, real.getItem());
        if (cat == null) return ItemStack.EMPTY;   // no slot/silhouette for it: the datapack must set "category"

        // signature enchantments, as they are (above the normal max, any combination)
        Map<net.minecraft.world.item.enchantment.Enchantment, Integer> ench = new java.util.LinkedHashMap<>(
                net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantments(real));
        l.enchantments().forEach((id, level) -> {
            var e = net.minecraftforge.registries.ForgeRegistries.ENCHANTMENTS.getValue(ResourceLocation.tryParse(id));
            if (e != null) ench.put(e, level);   // enchantments from a mod that isn't installed are skipped
        });
        net.minecraft.world.item.enchantment.EnchantmentHelper.setEnchantments(ench, real);

        CompoundTag relic = new CompoundTag();
        relic.putString("Rarity", rarity.id().toString());
        relic.putString("Category", cat.id().toString());
        relic.putString("Slot", cat.slot().getName());
        relic.putInt("Color", rarity.color());
        relic.putString("Legendary", l.id().toString());
        relic.putString("LegendaryName", l.name());
        ListTag lore = new ListTag();
        for (String line : l.lore()) lore.add(net.minecraft.nbt.StringTag.valueOf(line));
        relic.put("Lore", lore);
        ListTag affixes = new ListTag();
        CompoundTag entry = new CompoundTag();
        entry.putString("Id", l.affix().id().toString());
        entry.putFloat("Roll", random.nextFloat());
        affixes.add(entry);
        relic.put("Affixes", affixes);
        real.getOrCreateTag().put(RelicTag.KEY, relic);

        MinecraftForge.EVENT_BUS.post(new RelicGeneratedEvent(real, rarity, source, context));
        return wrap(real, rarity, cat, random);
    }

    private static ItemStack rollNormal(TRData data, RandomSource random, Rarity rarity, @Nullable Category category,
                                        RelicGeneratedEvent.Source source, @Nullable LootContext context) {

        // Base item: anything on the rarity's list that has a category (needed for its slot and silhouette).
        // The category is picked first (by its weight), then an item in it, so a mod adding 300 swords doesn't
        // make nearly every relic a sword.
        Map<Category, List<Item>> bases = new java.util.LinkedHashMap<>();
        for (Item item : rarity.items().resolve()) {
            Category c = data.categoryOf(item);
            if (c != null && (category == null || c == category)) bases.computeIfAbsent(c, k -> new ArrayList<>()).add(item);
        }
        int total = 0;
        for (Category c : bases.keySet()) total += Math.max(0, c.weight());
        if (total <= 0) return ItemStack.EMPTY;
        int pick = random.nextInt(total);
        List<Item> baseItems = null;
        for (Map.Entry<Category, List<Item>> e : bases.entrySet()) {
            pick -= Math.max(0, e.getKey().weight());
            if (pick < 0) {
                baseItems = e.getValue();
                break;
            }
        }
        if (baseItems == null) return ItemStack.EMPTY;
        ItemStack real = new ItemStack(baseItems.get(random.nextInt(baseItems.size())));
        Category cat = data.categoryOf(real.getItem());

        CompoundTag relic = new CompoundTag();
        relic.putString("Rarity", rarity.id().toString());
        relic.putString("Category", cat.id().toString());
        relic.putString("Slot", cat.slot().getName());
        relic.putInt("Color", rarity.color());

        // Roll affixes pool by pool; the first prefix and first suffix name the item
        ListTag affixes = new ListTag();
        Set<ResourceLocation> taken = new HashSet<>();
        Set<String> groups = new HashSet<>();
        for (Map.Entry<String, int[]> pool : rarity.rolls().entrySet()) {
            int[] r = pool.getValue();
            int count = r[0] + random.nextInt(Math.max(1, r[1] - r[0] + 1));
            for (int i = 0; i < count; i++) {
                Affix a = pickAffix(data, random, rarity, cat, pool.getKey(), taken, groups);
                if (a == null) break;   // nothing left that fits
                taken.add(a.id());
                if (a.group() != null) groups.add(a.group());
                CompoundTag entry = new CompoundTag();
                entry.putString("Id", a.id().toString());
                entry.putFloat("Roll", random.nextFloat());
                affixes.add(entry);
                if (a.nameKey() != null) {
                    if (a.kind().equals("prefix") && !relic.contains("Prefix")) relic.putString("Prefix", a.nameKey());
                    if (a.kind().equals("suffix") && !relic.contains("Suffix")) relic.putString("Suffix", a.nameKey());
                }
            }
        }
        relic.put("Affixes", affixes);
        if (rarity.uniqueName()) relic.putString("UniqueName", data.names().generate(random));
        real.getOrCreateTag().put(RelicTag.KEY, relic);

        // Let other mods add their own loot rolls (sockets etc.) to the real item before it's hidden
        MinecraftForge.EVENT_BUS.post(new RelicGeneratedEvent(real, rarity, source, context));
        return wrap(real, rarity, cat, random);
    }

    /** Puts a rolled item inside an unidentified item. */
    public static ItemStack wrap(ItemStack real, Rarity rarity, Category cat, RandomSource random) {
        ItemStack wrapper = new ItemStack(TRItems.UNIDENTIFIED.get());
        CompoundTag tag = wrapper.getOrCreateTag();
        tag.put("Hidden", real.save(new CompoundTag()));
        tag.putString("Category", cat.id().toString());
        tag.putString("NameKey", cat.unidentifiedKey());
        tag.putString("Silhouette", cat.silhouette());
        tag.putString("Rarity", rarity.id().toString());
        tag.putInt("Level", rarity.identifyLevel());
        tag.putInt("Cost", rarity.rollCost(random));
        tag.putInt("Xp", rarity.villagerXp());
        tag.putInt("Color", rarity.color());
        return wrapper;
    }

    /**
     * A random rarity that can make something (of the asked category), weighted by weight + quality x luck, and
     * allowed in this loot table (table null: not from a loot table).
     */
    @Nullable
    private static Rarity pickRarity(TRData data, RandomSource random, @Nullable Category category, float luck,
                                     @Nullable ResourceLocation table) {
        int total = 0;
        List<Rarity> usable = new ArrayList<>();
        for (Rarity r : data.rarities().values()) {
            int w = r.weightWithLuck(luck);
            if (w <= 0 || !r.allowsTable(table)) continue;
            boolean any;
            if (r.legendary()) {
                any = data.legendaries().values().stream().anyMatch(l -> l.weight() > 0
                        && (category == null || legendaryCategory(data, l, firstItem(l)) == category));
            } else {
                // only rarities that can actually make an item (of the asked category)
                any = r.items().resolve().stream().anyMatch(i -> {
                    Category c = data.categoryOf(i);
                    return c != null && (category == null || c == category);
                });
            }
            if (any) {
                usable.add(r);
                total += w;
            }
        }
        if (total <= 0) return null;
        int pick = random.nextInt(total);
        for (Rarity r : usable) {
            pick -= r.weightWithLuck(luck);
            if (pick < 0) return r;
        }
        return null;
    }

    @Nullable
    private static Affix pickAffix(TRData data, RandomSource random, Rarity rarity, Category cat, String pool,
                                   Set<ResourceLocation> taken, Set<String> groups) {
        List<Affix> candidates = new ArrayList<>();
        int total = 0;
        for (Affix a : data.affixes().values()) {
            if (!a.pool().equals(pool) || taken.contains(a.id()) || !a.appliesTo(cat.id())) continue;
            if (a.group() != null && groups.contains(a.group())) continue;
            int w = a.weightFor(rarity.id());
            if (w <= 0) continue;
            candidates.add(a);
            total += w;
        }
        if (total <= 0) return null;
        int pick = random.nextInt(total);
        for (Affix a : candidates) {
            pick -= a.weightFor(rarity.id());
            if (pick < 0) return a;
        }
        return null;
    }
}
