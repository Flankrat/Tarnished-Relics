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
 *   "weight": 25,                                 chance relative to the other rarities
 *   "items": ["minecraft:iron_sword", "#minecraft:swords", "@tarnishedrelics:helmet"],
 *   "rolls": { "normal": [3, 4] },                affixes per pool ("unique" pool for uniques)
 *   "identify_level": 3,                          villager level needed: 1 novice ... 5 master
 *   "cost": [8, 12],                              emeralds to identify
 *   "villager_xp": 10,                            trade XP the blacksmith gets
 *   "color": "#FFFF55",                           name color once identified
 *   "unique_name": false                          true: ends the name with "of <generated name>"
 * }
 * </pre>
 * The rarity's own name is the lang key {@code tarnishedrelics.rarity.<namespace>.<name>}.
 */
public record Rarity(ResourceLocation id, int weight, ItemList items, Map<String, int[]> rolls, int identifyLevel,
                     int costMin, int costMax, int villagerXp, int color, boolean uniqueName) {

    static Rarity parse(ResourceLocation id, JsonObject o) {
        Map<String, int[]> rolls = new LinkedHashMap<>();
        if (o.has("rolls")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("rolls").entrySet()) rolls.put(e.getKey(), intRange(e.getValue()));
        }
        int[] cost = o.has("cost") ? intRange(o.get("cost")) : new int[]{1, 1};
        String color = o.has("color") ? o.get("color").getAsString() : "#FFFFFF";
        return new Rarity(id, o.has("weight") ? o.get("weight").getAsInt() : 1, ItemList.parse(o.get("items"), o.get("exclude")),
                rolls, o.has("identify_level") ? o.get("identify_level").getAsInt() : 1, cost[0], cost[1],
                o.has("villager_xp") ? o.get("villager_xp").getAsInt() : 5,
                Integer.parseInt(color.replace("#", ""), 16),
                o.has("unique_name") && o.get("unique_name").getAsBoolean());
    }

    static int[] intRange(JsonElement e) {
        if (e.isJsonArray()) return new int[]{e.getAsJsonArray().get(0).getAsInt(), e.getAsJsonArray().get(1).getAsInt()};
        return new int[]{e.getAsInt(), e.getAsInt()};
    }

    public int rollCost(RandomSource random) {
        return costMin + random.nextInt(Math.max(1, costMax - costMin + 1));
    }

    public String nameKey() {
        return "tarnishedrelics.rarity." + id.getNamespace() + "." + id.getPath();
    }
}
