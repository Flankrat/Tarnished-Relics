package com.flankrat.tarnishedrelics.data;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.Locale;

/**
 * An item category ({@code tarnished_relics/categories/<name>.json}): which items count as e.g. a sword, which slot
 * their affixes work in, and the silhouette their unidentified version shows.
 * <pre>
 * {
 *   "items": ["#minecraft:swords", "somemod:cool_blade"],
 *   "exclude": ["minecraft:wooden_sword"],
 *   "slot": "mainhand",            mainhand | offhand | head | chest | legs | feet
 *   "silhouette": "sword",         one of the built-in silhouettes (see README)
 *   "priority": 0                  when an item is in several categories, the highest priority wins
 * }
 * </pre>
 * The unidentified name is the lang key {@code item.<namespace>.unidentified.<name>} ("Unidentified Sword").
 */
public record Category(ResourceLocation id, ItemList items, EquipmentSlot slot, String silhouette, int priority) {

    static Category parse(ResourceLocation id, JsonObject o) {
        EquipmentSlot slot = EquipmentSlot.byName(o.has("slot") ? o.get("slot").getAsString().toLowerCase(Locale.ROOT) : "mainhand");
        return new Category(id, ItemList.parse(o.get("items"), o.get("exclude")), slot,
                o.has("silhouette") ? o.get("silhouette").getAsString() : "generic",
                o.has("priority") ? o.get("priority").getAsInt() : 0);
    }

    /** Lang key of "Unidentified Sword" etc. */
    public String unidentifiedKey() {
        return "item." + id.getNamespace() + ".unidentified." + id.getPath();
    }
}
