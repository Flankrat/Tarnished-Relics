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
 * Makes relics: picks a rarity, a base item from that rarity's list, rolls its affixes, and wraps it as an
 * unidentified item. Legendary rarities make a generated legendary, or sometimes one of the hand-made ones.
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
        Roll roll = new Roll(data, random, source, context);
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
        ItemStack main = roll.handmade(rarity, legendary);
        if (main.isEmpty()) return List.of();
        List<ItemStack> out = new ArrayList<>();
        out.add(main);
        // the rest of the set drops with it
        for (ResourceLocation c : legendary.companions()) {
            Legendary companion = data.legendaries().get(c);
            if (companion == null || companion == legendary) continue;
            ItemStack extra = roll.handmade(rarity, companion);
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
            if (category != null && legendaryCategory(data, l, firstItem(l)) != category) continue;
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

    @Nullable
    private static Category legendaryCategory(TRData data, Legendary l, @Nullable Item item) {
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
                && (category == null || legendaryCategory(data, l, firstItem(l)) == category))) {
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

    /** One drop being rolled: the data, the random source, and where it's from (for RelicGeneratedEvent). */
    private record Roll(TRData data, RandomSource random, RelicGeneratedEvent.Source source, @Nullable LootContext context) {

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
                    affixes.add(affixEntry(a.id()));
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
                if (drawback != null) affixes.add(affixEntry(drawback.id()));
            }
            String name = data.names().generateLegendary(random);
            relic.putString("LegendaryName", name);
            relic.remove("Prefix");
            relic.remove("Suffix");
            relic.put("Lore", strings(data.names().lore(random, Rarity.roll(rarity.loreLines(), random), name)));

            // signature enchantments: fit the item, not curses, go together with each other
            Map<Enchantment, Integer> ench = new LinkedHashMap<>(EnchantmentHelper.getEnchantments(real));
            List<Enchantment> options = new ArrayList<>();
            for (Enchantment e : ForgeRegistries.ENCHANTMENTS) {
                if (!e.isCurse() && e.canEnchant(real)) options.add(e);
            }
            int count = Rarity.roll(rarity.signatureEnchantments(), random);
            while (count > 0 && !options.isEmpty()) {
                Enchantment e = options.remove(random.nextInt(options.size()));
                if (!EnchantmentHelper.isEnchantmentCompatible(ench.keySet(), e)) continue;
                // single-level enchantments (Mending, Silk Touch...) do nothing at higher levels, so they stay at 1
                ench.put(e, e.getMaxLevel() <= 1 ? 1 : e.getMaxLevel() + Rarity.roll(rarity.enchantmentBonus(), random));
                count--;
            }
            EnchantmentHelper.setEnchantments(ench, real);
        }

        /**
         * One hand-made legendary: its base item, its fixed name/lore/enchantments, and one 0-1 roll for all its
         * value ranges. Its effects are stored like an affix (the legendary's hidden affix), so they work like any
         * other relic's.
         */
        ItemStack handmade(Rarity rarity, Legendary l) {
            List<Item> items = new ArrayList<>(l.items().resolve());
            if (items.isEmpty()) return ItemStack.EMPTY;
            ItemStack real = new ItemStack(items.get(random.nextInt(items.size())));
            Category cat = legendaryCategory(data, l, real.getItem());
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
            affixes.add(affixEntry(l.affix().id()));
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

        // one rolled affix: its id and a 0-1 roll
        private CompoundTag affixEntry(ResourceLocation id) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Id", id.toString());
            entry.putFloat("Roll", random.nextFloat());
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
            return wrap(real, rarity, cat, random);
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
}
