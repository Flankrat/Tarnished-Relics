package com.flankrat.tarnishedrelics.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A tier ({@code tarnished_relics/rarities/<name>.json}): how often it drops, which base items it can be, how many
 * affixes it rolls from which pools, and what identifying it takes.
 * <pre>
 * {
 *   "name": "Magic",                              optional: lang key or plain text
 *   "weight": 25,                                 chance relative to the other rarities
 *   "items": ["*", "minecraft:trident"],          see ItemList ("*" = every item that has a category)
 *   "levels": [2, 3],                             only "*"/"@category" items of these material levels (see Levels)
 *   "rolls": { "normal": [3, 4] },                affixes per pool ("unique" pool for uniques)
 *   "identify_level": 3,                          villager level needed: 1 novice ... 5 master
 *   "cost": [8, 12],                              emeralds to identify
 *   "villager_xp": 10,                            trade XP the blacksmith gets
 *   "color": "#FFFF55",                           name color once identified
 *   "unique_name": false,                         true: ends the name with "of <generated name>"
 *   "legendary": false,                           true: a legendary. With "handmade_chance" it's one of the
 *                                                 hand-made ones (legendaries/), otherwise it's generated: base
 *                                                 item and affixes as usual (items/levels/rolls), plus a
 *                                                 generated name and lore (names/), signature enchantments and
 *                                                 maybe a drawback:
 *   "handmade_chance": 0.3,                       share that are hand-made (when any can drop)
 *   "signature_enchantments": [2, 3],             enchantments that fit the item, put on above their max...
 *   "enchantment_bonus": [1, 3],                  ...by this many levels
 *   "drawback_chance": 0.35,                      chance of one affix from "drawback_pool" (default "drawback")
 *   "lore_lines": [1, 2],                         generated lore lines
 *   "quality": 0,                                 extra weight per point of the looter's Luck (like loot table quality)
 *   "loot_tables": []                             only in these loot tables ("*" wildcards); empty = any
 * }
 * </pre>
 * The rarity's own name is the lang key {@code tarnishedrelics.rarity.<namespace>.<name>}.
 */
public record Rarity(ResourceLocation id, @javax.annotation.Nullable String name, int weight, ItemList items, Map<String, int[]> rolls, int identifyLevel,
                     int costMin, int costMax, int villagerXp, int color, boolean uniqueName, boolean legendary, int quality,
                     java.util.List<String> lootTables, double handmadeChance, int[] signatureEnchantments,
                     int[] enchantmentBonus, double drawbackChance, String drawbackPool, int[] loreLines) {

    static Rarity parse(ResourceLocation id, JsonObject o) {
        Map<String, int[]> rolls = new LinkedHashMap<>();
        if (o.has("rolls")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("rolls").entrySet()) rolls.put(e.getKey(), intRange(e.getValue()));
        }
        int[] cost = o.has("cost") ? intRange(o.get("cost")) : new int[]{1, 1};
        ItemList items = ItemList.parse(o.get("items"), o.get("exclude"));
        if (o.has("levels")) {
            int[] lv = intRange(o.get("levels"));
            items.levels(lv[0], lv[1]);
        }
        String color = o.has("color") ? o.get("color").getAsString() : "#FFFFFF";
        return new Rarity(id, o.has("name") ? o.get("name").getAsString() : null, o.has("weight") ? o.get("weight").getAsInt() : 1, items,
                rolls, o.has("identify_level") ? o.get("identify_level").getAsInt() : 1, cost[0], cost[1],
                o.has("villager_xp") ? o.get("villager_xp").getAsInt() : 5,
                Integer.parseInt(color.replace("#", ""), 16),
                o.has("unique_name") && o.get("unique_name").getAsBoolean(),
                o.has("legendary") && o.get("legendary").getAsBoolean(),
                o.has("quality") ? o.get("quality").getAsInt() : 0,
                Conditions.strings(o.get("loot_tables")),
                o.has("handmade_chance") ? o.get("handmade_chance").getAsDouble() : 0.3,
                o.has("signature_enchantments") ? intRange(o.get("signature_enchantments")) : new int[]{0, 0},
                o.has("enchantment_bonus") ? intRange(o.get("enchantment_bonus")) : new int[]{1, 1},
                o.has("drawback_chance") ? o.get("drawback_chance").getAsDouble() : 0,
                o.has("drawback_pool") ? o.get("drawback_pool").getAsString() : "drawback",
                o.has("lore_lines") ? intRange(o.get("lore_lines")) : new int[]{1, 2});
    }

    /** A random int in [r0, r1]. */
    public static int roll(int[] r, RandomSource random) {
        return r[0] + random.nextInt(Math.max(1, r[1] - r[0] + 1));
    }

    static int[] intRange(JsonElement e) {
        if (e.isJsonArray()) return new int[]{e.getAsJsonArray().get(0).getAsInt(), e.getAsJsonArray().get(1).getAsInt()};
        return new int[]{e.getAsInt(), e.getAsInt()};
    }

    public int rollCost(RandomSource random) {
        return costMin + random.nextInt(Math.max(1, costMax - costMin + 1));
    }

    /** Weight with the looter's luck counted in (never below 0). */
    public int weightWithLuck(float luck) {
        return Math.max(0, weight + (int) Math.floor(quality * luck));
    }

    /** True if this rarity can drop from this loot table (null = not from a loot table, e.g. a command). */
    public boolean allowsTable(@javax.annotation.Nullable ResourceLocation table) {
        if (lootTables.isEmpty() || table == null) return true;
        String id = table.toString();
        for (String p : lootTables) {
            if (id.matches(java.util.regex.Pattern.quote(p).replace("*", "\\E.*\\Q"))) return true;
        }
        return false;
    }

    public String nameKey() {
        if (name != null) return name;
        return "tarnishedrelics.rarity." + id.getNamespace() + "." + id.getPath();
    }
}
