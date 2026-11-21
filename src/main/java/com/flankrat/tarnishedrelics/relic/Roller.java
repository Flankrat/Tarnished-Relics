package com.flankrat.tarnishedrelics.relic;

import com.flankrat.tarnishedrelics.TRItems;
import com.flankrat.tarnishedrelics.api.RelicGeneratedEvent;
import com.flankrat.tarnishedrelics.data.Category;
import com.flankrat.tarnishedrelics.data.Legendary;
import com.flankrat.tarnishedrelics.data.Rarity;
import com.flankrat.tarnishedrelics.data.TRData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Makes relics: picks a rarity, a base item from that rarity's list, rolls its affixes, and wraps it as an
 * unidentified item. Legendary rarities make a generated legendary, or sometimes one of the hand-made ones.
 * This class picks what to make; {@link RelicRoll} makes it.
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
        RelicRoll roll = new RelicRoll(data, random, source, context);
        if (legendary != null && (rarity == null || !rarity.legendary())) {
            // a specific legendary needs a legendary rarity (its color, price, smith level); without one there's
            // nothing to make, rather than a random ordinary relic
            rarity = legendaryRarity(data);
            if (rarity == null) return List.of();
        }
        if (rarity == null) {
            float luck = context != null ? context.getLuck() : 0;
            rarity = pickRarity(data, random, category, luck, context != null ? context.getQueriedLootTableId() : null);
        }
        if (rarity == null) return List.of();
        if (!rarity.legendary()) {
            ItemStack one = roll.normal(rarity, category);
            return one.isEmpty() ? List.of() : List.of(one);
        }
        if (legendary == null) {
            // mostly generated; sometimes (handmade_chance) one of the hand-made ones
            Legendary handmade = pickLegendary(data, random, category);
            boolean useHandmade = handmade != null && random.nextDouble() < rarity.handmadeChance();
            if (!useHandmade) {
                ItemStack generated = roll.normal(rarity, category);
                if (!generated.isEmpty()) return List.of(generated);
            }
            legendary = handmade;
        }
        if (legendary == null) return List.of();
        ItemStack main = roll.handmade(rarity, legendary, category);
        if (main.isEmpty()) return List.of();
        List<ItemStack> out = new ArrayList<>();
        out.add(main);
        // the rest of the set drops with it
        for (ResourceLocation c : legendary.companions()) {
            Legendary companion = data.legendaries().get(c);
            if (companion == null || companion == legendary) continue;
            ItemStack extra = roll.handmade(rarity, companion, null);   // a set's other pieces can be any category
            if (!extra.isEmpty()) out.add(extra);
        }
        return out;
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
        for (Legendary l : data.legendaries().values()) {
            if (l.weight() <= 0) continue;
            if (category != null && !fits(data, l, category)) continue;
            if (firstItem(l) == null) continue;
            usable.add(l);
        }
        return Weighted.pick(usable, Legendary::weight, random);
    }

    @Nullable
    private static Item firstItem(Legendary l) {
        var items = l.items().resolve();
        return items.isEmpty() ? null : items.iterator().next();
    }

    /**
     * True if the legendary can be made as this category: its own "category" is it, or (without one) one of its
     * base items is in it. Checking only its first item let a legendary with mixed items be picked for a category,
     * then made on an item of another one.
     */
    private static boolean fits(TRData data, Legendary l, Category category) {
        if (l.category() != null) return data.categories().get(l.category()) == category;
        for (Item item : l.items().resolve()) if (data.categoryOf(item) == category) return true;
        return false;
    }

    @Nullable
    static Category legendaryCategory(TRData data, Legendary l, @Nullable Item item) {
        if (l.category() != null) return data.categories().get(l.category());
        return item == null ? null : data.categoryOf(item);
    }

    /**
     * A random rarity that can make something (of the asked category), weighted by weight + quality x luck, and
     * allowed in this loot table (table null: not from a loot table).
     */
    @Nullable
    private static Rarity pickRarity(TRData data, RandomSource random, @Nullable Category category, float luck,
                                     @Nullable ResourceLocation table) {
        List<Rarity> usable = new ArrayList<>();
        for (Rarity r : data.rarities().values()) {
            if (r.weightWithLuck(luck) <= 0 || !r.allowsTable(table)) continue;
            if (canMake(data, r, category)) usable.add(r);
        }
        return Weighted.pick(usable, r -> r.weightWithLuck(luck), random);
    }

    // true if the rarity can make an item (of the asked category)
    private static boolean canMake(TRData data, Rarity r, @Nullable Category category) {
        if (r.legendary() && data.legendaries().values().stream().anyMatch(l -> l.weight() > 0
                && (category == null || fits(data, l, category)))) {
            return true;   // a hand-made one can drop (generated ones are checked below)
        }
        return r.items().resolve().stream().anyMatch(i -> {
            Category c = data.categoryOf(i);
            return c != null && (category == null || c == category);
        });
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
}
