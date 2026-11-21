package com.flankrat.tarnishedrelics.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * A number in a datapack file that can be fixed, a range, or different per rarity:
 * <pre>
 *   "value": 3                                   always 3
 *   "value": [2, 5]                              rolled between 2 and 5
 *   "value": { "magic": [1, 2], "rare": [2, 4], "default": 1 }   per rarity (by rarity id or just its path)
 * </pre>
 * An item stores a 0-1 "roll" per affix instead of the final number, so a later balance change in the datapack
 * applies to items already found (a perfect roll stays a perfect roll).
 */
public final class ValueSpec {

    /** Min and max of one range (equal for a fixed number). */
    public record Range(double min, double max) {
        double at(double roll) {
            return min + (max - min) * roll;
        }
    }

    public static final ValueSpec ZERO = new ValueSpec(new Range(0, 0), Map.of(), false);

    private final Range fallback;              // used when no rarity-specific range matches
    private final Map<String, Range> byRarity; // keyed by full rarity id and by its path
    private final boolean perRarity;           // written per rarity ({...}) without a "default"

    private ValueSpec(Range fallback, Map<String, Range> byRarity, boolean perRarity) {
        this.fallback = fallback;
        this.byRarity = byRarity;
        this.perRarity = perRarity;
    }

    public static ValueSpec parse(JsonElement json) {
        if (json == null) return ZERO;
        if (json.isJsonObject()) {
            JsonObject obj = json.getAsJsonObject();
            Map<String, Range> map = new HashMap<>();
            Range fallback = null;
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                Range r = range(e.getValue());
                if (e.getKey().equals("default")) fallback = r;
                else map.put(e.getKey(), r);
            }
            // No "default": rarities that aren't listed get 0
            return new ValueSpec(fallback != null ? fallback : new Range(0, 0), map, fallback == null);
        }
        return new ValueSpec(range(json), Map.of(), false);
    }

    private static Range range(JsonElement json) {
        if (json.isJsonArray()) {
            if (json.getAsJsonArray().size() != 2) throw new JsonParseException("a range must be [min, max]");
            return new Range(json.getAsJsonArray().get(0).getAsDouble(), json.getAsJsonArray().get(1).getAsDouble());
        }
        double v = json.getAsDouble();
        return new Range(v, v);
    }

    /** The value for an item of this rarity with this 0-1 roll. */
    public double get(ResourceLocation rarity, double roll) {
        return rangeFor(rarity).at(roll);
    }

    /** False when the value is written per rarity, without "default", and this rarity isn't listed (it gets 0). */
    public boolean covers(ResourceLocation rarity) {
        return !perRarity || byRarity.containsKey(rarity.toString()) || byRarity.containsKey(rarity.getPath());
    }

    public Range rangeFor(ResourceLocation rarity) {
        if (rarity != null) {
            Range r = byRarity.get(rarity.toString());
            if (r == null) r = byRarity.get(rarity.getPath());
            if (r != null) return r;
        }
        return fallback;
    }
}
