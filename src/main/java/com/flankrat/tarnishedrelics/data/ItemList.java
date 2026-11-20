package com.flankrat.tarnishedrelics.data;

import com.google.gson.JsonElement;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A list of items from a datapack: ids, {@code #tags}, and {@code @category} (every item of that category), minus an
 * exclude list. Tags are only bound after a reload finishes, so the list is worked out lazily and cached.
 */
public final class ItemList {

    private final List<String> include;
    private final List<String> exclude;
    private Set<Item> resolved;   // cleared on reload

    private ItemList(List<String> include, List<String> exclude) {
        this.include = include;
        this.exclude = exclude;
    }

    static ItemList parse(@Nullable JsonElement include, @Nullable JsonElement exclude) {
        return new ItemList(Conditions.strings(include), Conditions.strings(exclude));
    }

    void clearCache() {
        resolved = null;
    }

    public boolean contains(Item item) {
        return resolve().contains(item);
    }

    public Set<Item> resolve() {
        if (resolved == null) {
            Set<Item> set = new LinkedHashSet<>();
            for (String s : include) add(set, s);
            Set<Item> minus = new LinkedHashSet<>();
            for (String s : exclude) add(minus, s);
            set.removeAll(minus);
            resolved = set;
        }
        return resolved;
    }

    private static void add(Set<Item> set, String entry) {
        if (entry.startsWith("@")) {
            // every item of a category
            Category c = TRData.get().categories().get(new ResourceLocation(entry.substring(1)));
            if (c != null) set.addAll(c.items().resolve());
        } else if (entry.startsWith("#")) {
            var tags = ForgeRegistries.ITEMS.tags();
            if (tags != null) tags.getTag(net.minecraft.tags.TagKey.create(Registries.ITEM, new ResourceLocation(entry.substring(1)))).forEach(set::add);
        } else {
            ResourceLocation id = new ResourceLocation(entry);
            if (ForgeRegistries.ITEMS.containsKey(id)) set.add(ForgeRegistries.ITEMS.getValue(id));
        }
    }

    public List<String> entries() {
        return new ArrayList<>(include);
    }
}
