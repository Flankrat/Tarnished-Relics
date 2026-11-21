package com.flankrat.tarnishedrelics.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;

/**
 * An item category ({@code tarnished_relics/categories/<name>.json}): which items count as e.g. a sword, which slot
 * their affixes work in, and the silhouette their unidentified version shows.
 * <pre>
 * {
 *   "name": "Unidentified Spear",  optional: lang key or plain text (default item.<ns>.unidentified.<name>)
 *   "auto": ["sword"],             item kinds picked up from every mod (see AutoMatch); optional
 *   "items": ["#minecraft:swords", "somemod:cool_blade", "othermod:*"],
 *   "exclude": ["minecraft:wooden_sword"],
 *   "slot": "mainhand",            mainhand | offhand | head | chest | legs | feet
 *   "silhouette": "sword",         one of the built-in silhouettes (see README)
 *   "priority": 0,                 when an item is in several categories, the highest priority wins
 *   "weight": 10                   how often a relic is this category, relative to the others (default 10)
 * }
 * </pre>
 * Items on the global exclusions list (tarnished_relics/exclusions/) are never in a category.
 * The unidentified name is the lang key {@code item.<namespace>.unidentified.<name>} ("Unidentified Sword").
 */
public record Category(ResourceLocation id, @Nullable String name, List<AutoMatch> auto, ItemList items, EquipmentSlot slot, String silhouette, int priority, int weight) {

    static Category parse(ResourceLocation id, JsonObject o) {
        EquipmentSlot slot = EquipmentSlot.byName(o.has("slot") ? o.get("slot").getAsString().toLowerCase(Locale.ROOT) : "mainhand");
        if (Conditions.strings(o.get("items")).contains("*")) throw new JsonParseException("\"*\" only works in rarities");
        List<AutoMatch> auto = Conditions.strings(o.get("auto")).stream().map(AutoMatch::parse).toList();
        return new Category(id, o.has("name") ? o.get("name").getAsString() : null, auto, ItemList.parse(o.get("items"), o.get("exclude")), slot,
                o.has("silhouette") ? o.get("silhouette").getAsString() : "generic",
                o.has("priority") ? o.get("priority").getAsInt() : 0,
                o.has("weight") ? o.get("weight").getAsInt() : 10);
    }

    /** True if the item is listed or matches an auto kind, and isn't excluded here. */
    public boolean matches(Item item) {
        if (items.contains(item)) return true;
        if (auto.isEmpty() || items.excludes(item)) return false;
        for (AutoMatch a : auto) if (a.matches(item)) return true;
        return false;
    }

    /** Lang key of "Unidentified Sword" etc. ("name" if the file sets one: a lang key or plain text) */
    public String unidentifiedKey() {
        if (name != null) return name;
        return "item." + id.getNamespace() + ".unidentified." + id.getPath();
    }
}
