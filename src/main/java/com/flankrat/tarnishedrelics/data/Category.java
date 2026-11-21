package com.flankrat.tarnishedrelics.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nullable;

import static com.flankrat.tarnishedrelics.data.JsonFields.intOr;
import static com.flankrat.tarnishedrelics.data.JsonFields.stringOr;

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
 *   "weight": 10,                  how often a relic is this category, relative to the others (default 10)
 *   "smiths": ["#tarnishedrelics:weaponsmiths"]   villager professions (ids or #tags) that can identify it;
 *                                  none: any in #tarnishedrelics:blacksmiths
 * }
 * </pre>
 * Items on the global exclusions list (tarnished_relics/exclusions/) are never in a category.
 * The unidentified name is the lang key {@code item.<namespace>.unidentified.<name>} ("Unidentified Sword").
 */
public record Category(ResourceLocation id, @Nullable String name, List<AutoMatch> auto, ItemList items, EquipmentSlot slot, String silhouette, int priority, int weight,
                       List<IdOrTag<VillagerProfession>> smiths) {

    static Category parse(ResourceLocation id, JsonObject o) {
        EquipmentSlot slot = EquipmentSlot.byName(o.has("slot") ? o.get("slot").getAsString().toLowerCase(Locale.ROOT) : "mainhand");
        if (Conditions.strings(o.get("items")).contains("*")) throw new JsonParseException("\"*\" only works in rarities");
        List<AutoMatch> auto = Conditions.strings(o.get("auto")).stream().map(AutoMatch::parse).toList();
        List<IdOrTag<VillagerProfession>> smiths = new ArrayList<>();
        for (String s : Conditions.strings(o.get("smiths"))) {
            IdOrTag<VillagerProfession> smith = IdOrTag.parse(s, Registries.VILLAGER_PROFESSION);
            // an id that isn't a profession could never identify anything (a tag can list a mod's smith as optional)
            if (!smith.isTag() && !ForgeRegistries.VILLAGER_PROFESSIONS.containsKey(smith.id()))
                throw new JsonParseException("no villager profession " + smith.id() + " (smiths; for a mod that may be missing, use a tag)");
            smiths.add(smith);
        }
        return new Category(id, stringOr(o, "name", null), auto, ItemList.parse(o.get("items"), o.get("exclude")), slot,
                stringOr(o, "silhouette", "generic"),
                intOr(o, "priority", 0),
                intOr(o, "weight", 10),
                List.copyOf(smiths));
    }

    /**
     * The professions that identify this category's relics: its "smiths" (tags looked up now, they can change on
     * reload). Empty when it names none, or none of them resolve to a profession; then any blacksmith can.
     */
    public List<VillagerProfession> smithProfessions() {
        Set<VillagerProfession> out = new LinkedHashSet<>();
        var tags = ForgeRegistries.VILLAGER_PROFESSIONS.tags();
        for (IdOrTag<VillagerProfession> s : smiths) {
            if (s.isTag()) {
                if (tags != null) tags.getTag(TagKey.create(Registries.VILLAGER_PROFESSION, s.id())).forEach(out::add);
            } else {
                VillagerProfession p = ForgeRegistries.VILLAGER_PROFESSIONS.getValue(s.id());
                if (p != null) out.add(p);
            }
        }
        return List.copyOf(out);
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
