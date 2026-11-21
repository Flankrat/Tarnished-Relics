package com.flankrat.tarnishedrelics.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

import static com.flankrat.tarnishedrelics.data.JsonFields.stringOr;

/**
 * A prefix or suffix ({@code tarnished_relics/affixes/<name>.json}).
 * <pre>
 * {
 *   "kind": "prefix",                    prefix | suffix | hidden (no name part)
 *   "name": "affix.mypack.champions",    lang key ("Champion's" / "of the Bear")
 *   "pool": "normal",                    which pool rarities roll it from ("unique" for unique-only affixes)
 *   "applies_to": ["tarnishedrelics:sword", "tarnishedrelics:axe"],    categories, or "*" for any
 *   "weight": 10,                        or per rarity: { "magic": 10, "rare": 6 }
 *   "group": "flat_damage",              only one affix per group on an item
 *   "effects": [ ... ]                   see Effect
 * }
 * </pre>
 */
public record Affix(ResourceLocation id, String kind, @Nullable String nameKey, String pool, List<String> appliesTo,
                    int weight, Map<String, Integer> weightByRarity, @Nullable String group, List<Effect> effects) {

    static Affix parse(ResourceLocation id, JsonObject o) {
        int weight = 10;
        Map<String, Integer> byRarity = new HashMap<>();
        if (o.has("weight")) {
            JsonElement w = o.get("weight");
            if (w.isJsonObject()) {
                weight = 0;   // rarities not listed can't roll it
                for (Map.Entry<String, JsonElement> e : w.getAsJsonObject().entrySet()) byRarity.put(e.getKey(), e.getValue().getAsInt());
            } else {
                weight = w.getAsInt();
            }
        }
        List<Effect> effects = new ArrayList<>();
        if (o.has("effects")) for (JsonElement e : o.getAsJsonArray("effects")) effects.add(Effect.parse(e.getAsJsonObject()));
        String kind = stringOr(o, "kind", "hidden");
        // any other word used to make a hidden affix: no name part
        if (!Set.of("prefix", "suffix", "hidden").contains(kind)) throw new JsonParseException("unknown kind \"" + kind + "\" (prefix, suffix or hidden)");
        List<String> applies = Conditions.strings(o.get("applies_to"));
        if (applies.isEmpty()) applies = List.of("*");
        return new Affix(id, kind,
                stringOr(o, "name", null),
                stringOr(o, "pool", "normal"), applies, weight, byRarity,
                stringOr(o, "group", null), effects);
    }

    public int weightFor(ResourceLocation rarity) {
        Integer w = weightByRarity.get(rarity.toString());
        if (w == null) w = weightByRarity.get(rarity.getPath());
        return w != null ? w : weight;
    }

    public boolean appliesTo(ResourceLocation category) {
        return appliesTo.contains("*") || appliesTo.contains(category.toString());
    }
}
