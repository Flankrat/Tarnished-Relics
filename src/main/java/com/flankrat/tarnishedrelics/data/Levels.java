package com.flankrat.tarnishedrelics.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TieredItem;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How good an item's material is ({@code tarnished_relics/levels/<any>.json}, all files merged). Rarities use it
 * to pick base items ({@code "levels": [2, 3]}), so modded gear lands in the right rarity without being listed.
 * <pre>
 * {
 *   "items": { "minecraft:golden_sword": 1, "#somemod:fancy_bows": 3, "somemod:*": 2 },
 *   "keywords": { "diamond": 3, "dragonbone": 4 },
 *   "default": 2
 * }
 * </pre>
 * An item's level:
 * <ol>
 *   <li>"items" (id, then #tag, then "mod:*") if one names it</li>
 *   <li>otherwise the highest of: its tool tier (wood/gold 0, stone 1, iron 2, diamond 3, netherite 4; modded tiers
 *       say their own), its armor (its material's chestplate armor: leather 0, gold/chain 1, iron 2, diamond 3,
 *       + toughness 3 = 4) and a "keywords" word in its id ("iron_longbow" has "iron"; whole words between underscores; the longest word wins)</li>
 *   <li>"default" if none of those apply</li>
 * </ol>
 */
public final class Levels {

    private final Map<String, Integer> items = new LinkedHashMap<>();
    private final Map<String, Integer> keywords = new LinkedHashMap<>();
    private int fallback = 2;
    private final Map<Item, Integer> cache = new ConcurrentHashMap<>();

    void add(JsonObject o) {
        if (o.has("items")) for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("items").entrySet()) items.put(e.getKey(), e.getValue().getAsInt());
        if (o.has("keywords")) for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("keywords").entrySet()) keywords.put(e.getKey(), e.getValue().getAsInt());
        if (o.has("default")) fallback = o.get("default").getAsInt();
    }

    void clearCache() {
        cache.clear();
    }

    public int of(Item item) {
        return cache.computeIfAbsent(item, this::compute);
    }

    private int compute(Item item) {
        var key = ForgeRegistries.ITEMS.getKey(item);
        if (key != null) {
            Integer direct = items.get(key.toString());
            if (direct != null) return direct;
            for (Map.Entry<String, Integer> e : items.entrySet()) {
                if (e.getKey().startsWith("#") && ItemList.inTag(item, e.getKey().substring(1))) return e.getValue();
            }
            Integer mod = items.get(key.getNamespace() + ":*");
            if (mod != null) return mod;
        }
        // the best of what the item says about itself: its tool tier, its armor, a material word in its id
        // (some mods leave their tiers at level 0, e.g. Spartan Weaponry's weapons, so the id's "iron" counts too)
        int best = -1;
        if (item instanceof TieredItem t) best = t.getTier().getLevel();
        if (item instanceof ArmorItem a) best = Math.max(best, armorLevel(a.getMaterial()));
        if (key != null) {
            String word = null;
            // whole words between underscores only, so "tin" doesn't match "tinted"
            String path = "_" + key.getPath().replace('/', '_') + "_";
            for (String w : keywords.keySet()) {
                if (path.contains("_" + w + "_") && (word == null || w.length() > word.length())) word = w;
            }
            if (word != null) best = Math.max(best, keywords.get(word));
        }
        if (best >= 0) return best;
        return fallback;
    }

    // Vanilla chestplates: leather 3, gold 5, chain 5, iron 6, diamond 8, netherite 8 with toughness 3
    private static int armorLevel(ArmorMaterial m) {
        int chest = m.getDefenseForType(ArmorItem.Type.CHESTPLATE);
        float tough = m.getToughness();
        if (chest >= 8) return tough >= 3 ? 4 : 3;
        if (chest >= 6) return 2;
        if (chest >= 5) return 1;
        return 0;
    }
}
