package com.flankrat.tarnishedrelics.data;

import com.google.gson.JsonElement;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * A list of items from a datapack, minus an exclude list. Entries can be:
 * <pre>
 *   "minecraft:iron_sword"   one item
 *   "#minecraft:swords"      an item tag
 *   "somemod:*"              every item of a mod
 *   "@tarnishedrelics:sword" every item of a category
 *   "*"                      every item that has a category (rarities only)
 * </pre>
 * A rarity can give a level range: then "*" and "@category" only bring in items whose {@link Levels level} fits,
 * while items named directly (ids, tags, mods) always count. Tags are only bound after a reload finishes, so the
 * list is worked out lazily and cached.
 */
public final class ItemList {

    private final List<String> include;
    private final List<String> exclude;
    private int minLevel = Integer.MIN_VALUE, maxLevel = Integer.MAX_VALUE;
    private TRData owner;   // the data set this list belongs to (null: the current one)
    // cleared on reload; volatile because the client and the integrated server can both read the same lists
    private volatile Set<Item> resolved, excludedSet;
    // lists being worked out on this thread (per thread: the client and integrated server may resolve at once),
    // to catch a list that includes itself through @category
    private static final ThreadLocal<Set<ItemList>> RESOLVING = ThreadLocal.withInitial(HashSet::new);

    private ItemList(List<String> include, List<String> exclude) {
        this.include = include;
        this.exclude = exclude;
    }

    static ItemList parse(@Nullable JsonElement include, @Nullable JsonElement exclude) {
        return new ItemList(Conditions.strings(include), Conditions.strings(exclude));
    }

    static ItemList of(List<String> include) {
        return new ItemList(include, List.of());
    }

    ItemList levels(int min, int max) {
        minLevel = min;
        maxLevel = max;
        return this;
    }

    void owner(TRData data) {
        owner = data;
    }

    void clearCache() {
        resolved = null;
        excludedSet = null;
    }

    public boolean contains(Item item) {
        return resolve().contains(item);
    }

    public Set<Item> resolve() {
        // a list that (through @category) includes itself would loop forever; the inner use sees it as empty
        if (resolved == null) {
            if (!RESOLVING.get().add(this)) return Set.of();
            try {
                resolved = build();
            } finally {
                RESOLVING.get().remove(this);
            }
        }
        return resolved;
    }

    private Set<Item> build() {
        Set<Item> set = new LinkedHashSet<>();
        for (String s : include) add(set, s, true);
        Set<Item> minus = new LinkedHashSet<>();
        for (String s : exclude) add(minus, s, false);
        set.removeAll(minus);
        return set;
    }

    private void add(Set<Item> set, String entry, boolean filter) {
        TRData data = owner != null ? owner : TRData.get();
        if (entry.equals("*")) {
            for (Item item : ForgeRegistries.ITEMS) {
                if (data.categoryOf(item) != null && (!filter || fits(data, item))) set.add(item);
            }
        } else if (entry.startsWith("@")) {
            Category c = data.categories().get(new ResourceLocation(entry.substring(1)));
            if (c != null) for (Item item : ForgeRegistries.ITEMS) {
                if (c.matches(item) && (!filter || fits(data, item))) set.add(item);
            }
        } else {
            set.addAll(match(entry));
        }
    }

    private boolean fits(TRData data, Item item) {
        int level = data.levels().of(item);
        return level >= minLevel && level <= maxLevel;
    }

    /** Items an id, #tag or "mod:*" entry names. */
    static List<Item> match(String entry) {
        List<Item> out = new ArrayList<>();
        if (entry.startsWith("#")) {
            var tags = ForgeRegistries.ITEMS.tags();
            if (tags != null) tags.getTag(TagKey.create(Registries.ITEM, new ResourceLocation(entry.substring(1)))).forEach(out::add);
        } else if (entry.endsWith(":*")) {
            String mod = entry.substring(0, entry.length() - 2);
            for (var e : ForgeRegistries.ITEMS.getEntries()) if (e.getKey().location().getNamespace().equals(mod)) out.add(e.getValue());
        } else {
            ResourceLocation id = new ResourceLocation(entry);
            if (ForgeRegistries.ITEMS.containsKey(id)) out.add(ForgeRegistries.ITEMS.getValue(id));
        }
        return out;
    }

    static boolean inTag(Item item, String tag) {
        var tags = ForgeRegistries.ITEMS.tags();
        return tags != null && tags.getTag(TagKey.create(Registries.ITEM, new ResourceLocation(tag))).contains(item);
    }

    /** True if an exclude entry names the item. */
    public boolean excludes(Item item) {
        if (excludedSet == null) {
            Set<Item> minus = new LinkedHashSet<>();
            for (String s : exclude) add(minus, s, false);
            excludedSet = minus;
        }
        return excludedSet.contains(item);
    }

    public List<String> entries() {
        return new ArrayList<>(include);
    }

    public List<String> excluded() {
        return new ArrayList<>(exclude);
    }
}
