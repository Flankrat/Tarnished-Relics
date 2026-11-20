package com.flankrat.tarnishedrelics.relic;

import com.flankrat.tarnishedrelics.TRItems;
import com.flankrat.tarnishedrelics.api.RelicGeneratedEvent;
import com.flankrat.tarnishedrelics.data.Affix;
import com.flankrat.tarnishedrelics.data.Category;
import com.flankrat.tarnishedrelics.data.Rarity;
import com.flankrat.tarnishedrelics.data.TRData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
        TRData data = TRData.get();
        if (rarity == null) rarity = pickRarity(data, random, category);
        if (rarity == null) return ItemStack.EMPTY;

        // Base item: anything on the rarity's list that has a category (needed for its slot and silhouette)
        List<Item> bases = new ArrayList<>();
        for (Item item : rarity.items().resolve()) {
            Category c = data.categoryOf(item);
            if (c != null && (category == null || c == category)) bases.add(item);
        }
        if (bases.isEmpty()) return ItemStack.EMPTY;
        ItemStack real = new ItemStack(bases.get(random.nextInt(bases.size())));
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
        MinecraftForge.EVENT_BUS.post(new RelicGeneratedEvent(real, rarity));
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

    @Nullable
    private static Rarity pickRarity(TRData data, RandomSource random, @Nullable Category category) {
        int total = 0;
        List<Rarity> usable = new ArrayList<>();
        for (Rarity r : data.rarities().values()) {
            if (r.weight() <= 0) continue;
            // only rarities that can actually make an item (of the asked category)
            boolean any = r.items().resolve().stream().anyMatch(i -> {
                Category c = data.categoryOf(i);
                return c != null && (category == null || c == category);
            });
            if (any) {
                usable.add(r);
                total += r.weight();
            }
        }
        if (total <= 0) return null;
        int pick = random.nextInt(total);
        for (Rarity r : usable) {
            pick -= r.weight();
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
