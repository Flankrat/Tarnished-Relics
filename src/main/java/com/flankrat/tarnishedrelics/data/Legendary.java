package com.flankrat.tarnishedrelics.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

import static com.flankrat.tarnishedrelics.data.JsonFields.idOrNull;
import static com.flankrat.tarnishedrelics.data.JsonFields.intOr;

/**
 * A hand-made legendary relic ({@code tarnished_relics/legendaries/<name>.json}). Rarities with
 * {@code "legendary": true} don't roll random affixes: they pick one of these, which always has the same name, lore,
 * signature enchantments and effects. Only the numbers vary (each copy gets one 0-1 roll for all its value ranges),
 * so two copies of the same legendary are close but not identical.
 * <pre>
 * {
 *   "items": ["minecraft:netherite_sword", "minecraft:diamond_sword"],   base items (ids, #tags, mod:*), one is picked
 *   "exclude": ["minecraft:wooden_sword"],    optional: items taken out of "items"
 *   "category": "tarnishedrelics:sword",       optional: category (slot, silhouette) if the item has none of its own
 *   "name": "Dawnbreaker",                      lang key or plain text
 *   "lore": ["The first light of the last day."],   lines under the name (lang keys or text)
 *   "enchantments": { "minecraft:smite": 7 },   put on as they are: above the normal max, and ignoring which ones
 *                                               normally can't go together
 *   "effects": [ ... ],                         like an affix's effects (see Effect); ranges are rolled per copy
 *   "weight": 10,                               how often it's picked among the legendaries (0 = only as a companion)
 *   "companions": ["tarnishedrelics:dawnbreaker_crest"],  other legendaries that drop with it, as a set
 *   "set_effects": [ ... ]                      optional, needs companions: effects that work while the whole set
 *                                               (this one and its companions) is in its slots; values use this
 *                                               one's roll
 * }
 * </pre>
 * The effects are kept as a hidden affix with the id {@code <namespace>:legendary/<name>}, so they work and show in
 * tooltips exactly like affix effects.
 */
public record Legendary(ResourceLocation id, ItemList items, @Nullable ResourceLocation category, String name, List<String> lore,
                        Map<String, Integer> enchantments, int weight, List<ResourceLocation> companions, Affix affix,
                        List<Effect> setEffects) {

    /** Affix pool no rarity rolls from (so a legendary's effects never turn up as a normal affix). */
    public static final String POOL = "handmade_legendary";

    static Legendary parse(ResourceLocation id, JsonObject o) {
        if (!o.has("name")) throw new JsonParseException("a legendary needs a \"name\"");
        if (!o.has("items")) throw new JsonParseException("a legendary needs \"items\"");
        List<String> lore = Conditions.strings(o.get("lore"));
        Map<String, Integer> enchants = new LinkedHashMap<>();
        if (o.has("enchantments")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("enchantments").entrySet()) {
                if (ResourceLocation.tryParse(e.getKey()) == null) throw new JsonParseException("bad enchantment id \"" + e.getKey() + "\"");
                enchants.put(e.getKey(), e.getValue().getAsInt());
            }
        }
        List<Effect> effects = new ArrayList<>();
        if (o.has("effects")) for (JsonElement e : o.getAsJsonArray("effects")) effects.add(Effect.parse(e.getAsJsonObject()));
        ResourceLocation affixId = new ResourceLocation(id.getNamespace(), "legendary/" + id.getPath());
        Affix affix = new Affix(affixId, "hidden", null, POOL, List.of("*"), 0, Map.of(), null, effects);
        List<ResourceLocation> companions = Conditions.strings(o.get("companions")).stream().map(ResourceLocation::new).toList();
        List<Effect> setEffects = new ArrayList<>();
        if (o.has("set_effects")) for (JsonElement e : o.getAsJsonArray("set_effects")) setEffects.add(Effect.parse(e.getAsJsonObject()));
        // a set bonus is for wearing the set together: one piece alone is no set
        if (!setEffects.isEmpty() && companions.isEmpty()) throw new JsonParseException("set_effects needs companions (the set's other pieces)");
        return new Legendary(id, ItemList.parse(o.get("items"), o.get("exclude")),
                idOrNull(o, "category"),
                o.get("name").getAsString(), lore, enchants, intOr(o, "weight", 10), companions, affix, List.copyOf(setEffects));
    }
}
