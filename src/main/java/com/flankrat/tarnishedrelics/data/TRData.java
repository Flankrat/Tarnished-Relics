package com.flankrat.tarnishedrelics.data;

import com.flankrat.tarnishedrelics.TarnishedRelics;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

import static com.flankrat.tarnishedrelics.data.JsonFields.boolOr;

/**
 * Everything loaded from {@code data/<namespace>/tarnished_relics/}: categories, rarities, affixes and names.
 * The server loads it on every (re)load and sends the same JSON to clients (for tooltips and attribute display),
 * so both sides parse it with the same code.
 */
public final class TRData {

    private static volatile TRData current = new TRData(Map.of());

    private final Map<ResourceLocation, Category> categories = new LinkedHashMap<>();
    private final Map<ResourceLocation, Rarity> rarities = new LinkedHashMap<>();
    private final Map<ResourceLocation, Affix> affixes = new LinkedHashMap<>();
    private final Map<ResourceLocation, Legendary> legendaries = new LinkedHashMap<>();
    private final NameList names = new NameList();
    private final Levels levels = new Levels();
    private final List<String> exclusionEntries = new ArrayList<>();
    private final Map<ResourceLocation, List<String>> exclusionFiles = new LinkedHashMap<>();   // for the category check
    private final Set<ResourceLocation> disabledCategories = new HashSet<>();
    private ItemList exclusions = ItemList.of(List.of());
    private final List<String> errors = new ArrayList<>();
    private final Map<ResourceLocation, String> rawJson;   // "folder/name" ids -> JSON, for the client sync

    public static TRData get() {
        return current;
    }

    /** Parses a full set of files and makes it the current data. Ids are "<folder>/<name>" under tarnished_relics/. */
    public static TRData load(Map<ResourceLocation, JsonElement> files) {
        return load(files, List.of());
    }

    /** Same, with errors from reading the files (ones that weren't valid JSON), reported first. */
    public static TRData load(Map<ResourceLocation, JsonElement> files, List<String> readErrors) {
        TRData data = parseOnly(files);
        data.errors.addAll(0, readErrors);
        data.logResult();
        current = data;
        return data;
    }

    /** Parses files without making them current: the editor uses it to check and preview unsaved changes. */
    public static TRData parseOnly(Map<ResourceLocation, JsonElement> files) {
        Map<ResourceLocation, String> raw = new LinkedHashMap<>();
        files.forEach((id, json) -> raw.put(id, json.toString()));
        TRData data = new TRData(raw);
        data.parse(files);
        return data;
    }

    /**
     * Client side: rebuilds the data from the JSON the server sent. If it's the data already loaded (single
     * player: the integrated server, in the same game, loaded it), that's kept: rebuilding it from the JSON lost
     * the server's errors for files that weren't valid JSON (they aren't in the sync), and parsed it all again.
     */
    public static void loadFromSync(Map<ResourceLocation, String> raw) {
        if (current.rawJson.equals(raw)) return;
        Map<ResourceLocation, JsonElement> files = new LinkedHashMap<>();
        raw.forEach((id, s) -> files.put(id, JsonParser.parseString(s)));
        load(files);
    }

    private TRData(Map<ResourceLocation, String> rawJson) {
        this.rawJson = rawJson;
    }

    private void parse(Map<ResourceLocation, JsonElement> files) {
        for (Map.Entry<ResourceLocation, JsonElement> e : files.entrySet()) {
            ResourceLocation file = e.getKey();
            int slash = file.getPath().indexOf('/');
            if (slash < 0) continue;
            String folder = file.getPath().substring(0, slash);
            ResourceLocation id = new ResourceLocation(file.getNamespace(), file.getPath().substring(slash + 1));
            try {
                JsonObject o = e.getValue().getAsJsonObject();
                // "disabled": true switches a file off (how the editor removes a file another pack ships)
                if (boolOr(o, "disabled", false)) {
                    if (folder.equals("categories")) disabledCategories.add(id);
                    continue;
                }
                checkKeys(folder, o);
                switch (folder) {
                    case "categories" -> categories.put(id, Category.parse(id, o));
                    case "rarities" -> rarities.put(id, Rarity.parse(id, o));
                    case "affixes" -> affixes.put(id, Affix.parse(id, o));
                    case "names" -> names.add(o);
                    case "levels" -> levels.add(o);
                    case "exclusions" -> {
                        List<String> entries = Conditions.strings(o.get("items"));
                        // "*" is "every item that has a category": asking that while working out an item's category
                        // looped (categoryOf -> exclusions -> categoryOf), so it's refused like in categories
                        if (entries.contains("*")) throw new JsonParseException("\"*\" only works in rarities");
                        entries.forEach(ItemList::check);   // a bad entry is reported, not found when loot is rolled
                        exclusionEntries.addAll(entries);
                        exclusionFiles.put(file, entries);
                    }
                    case "legendaries" -> legendaries.put(id, Legendary.parse(id, o));
                    default -> errors.add(file + ": unknown folder \"" + folder + "\"");
                }
            } catch (RuntimeException ex) {
                // One bad file is skipped and reported; the rest still load
                errors.add(file + ": " + ex.getMessage());
            }
        }
        // a legendary's effects work as a hidden affix (see Legendary)
        for (Legendary l : legendaries.values()) affixes.put(l.affix().id(), l.affix());
        for (Legendary l : legendaries.values()) {
            for (ResourceLocation c : l.companions()) {
                if (!legendaries.containsKey(c)) errors.add(fileId("legendaries", l.id()) + ": no legendary " + c + " (companions)");
            }
        }
        checkAffixValues();
        CategoryNames.check(this, exclusionFiles, disabledCategories, errors);
        exclusions = ItemList.of(exclusionEntries);
        // lists look up categories/levels in this data set, not whatever is current
        exclusions.owner(this);
        categories.values().forEach(c -> c.items().owner(this));
        rarities.values().forEach(r -> r.items().owner(this));
        legendaries.values().forEach(l -> l.items().owner(this));
    }

    /**
     * Every key each kind of file can have (plus "disabled", which the editor uses to switch any file off). A
     * misspelled key used to be skipped and its default used, so the file did something else than written
     * ("signature_enchantment" gave generated legendaries no signature enchantments). The editor's fields
     * (client.editor.Schema) are the same lists.
     */
    private static final Map<String, Set<String>> KEYS = Map.of(
            "categories", Set.of("name", "auto", "items", "exclude", "slot", "silhouette", "priority", "weight", "smiths"),
            "rarities", Set.of("name", "weight", "items", "levels", "exclude", "rolls", "identify_level", "cost", "villager_xp",
                    "color", "unique_name", "legendary", "handmade_chance", "signature_enchantments", "enchantment_bonus",
                    "drawback_chance", "drawback_pool", "lore_lines", "quality", "loot_tables"),
            "affixes", Set.of("kind", "name", "pool", "applies_to", "weight", "group", "effects"),
            "legendaries", Set.of("name", "lore", "items", "exclude", "category", "enchantments", "effects", "weight", "companions", "set_effects"),
            "names", Set.of("names", "starts", "ends", "titles", "lore"),
            "levels", Set.of("items", "keywords", "default"),
            "exclusions", Set.of("items"));

    /** The keys a file of this folder can have besides "disabled" (empty for a folder that isn't one of these). */
    public static Set<String> keys(String folder) {
        return KEYS.getOrDefault(folder, Set.of());
    }

    // an unknown key is an error (the file is skipped and reported, like any other bad file)
    private static void checkKeys(String folder, JsonObject o) {
        Set<String> known = KEYS.get(folder);
        if (known == null) return;   // an unknown folder is reported below
        for (String key : o.keySet()) {
            if (!known.contains(key) && !key.equals("disabled")) throw new JsonParseException("unknown key \"" + key + "\"");
        }
    }

    /**
     * An affix a rarity can roll whose value is written per rarity without that one (and without "default") is 0 on
     * that rarity: it rolls and does nothing. Generated legendaries roll "normal" affixes, and the built-in ones only
     * listed magic, rare and unique, so a legendary's normal affixes did nothing.
     */
    private void checkAffixValues() {
        for (Rarity r : rarities.values()) {
            Set<String> pools = new HashSet<>(r.rolls().keySet());
            if (r.drawbackChance() > 0) pools.add(r.drawbackPool());
            for (Affix a : affixes.values()) {
                if (!pools.contains(a.pool()) || a.weightFor(r.id()) <= 0) continue;
                for (Effect e : a.effects()) {
                    if (e.value.covers(r.id())) continue;
                    errors.add(fileId("affixes", a.id()) + ": its " + e.type.key() + " value has no \"" + r.id().getPath() + "\" (or \"default\"), so on "
                            + r.id() + " relics, which roll it, it does nothing");
                    break;
                }
            }
        }
    }

    private void logResult() {
        for (String err : errors) TarnishedRelics.LOGGER.error("[Tarnished Relics] bad datapack file {}", err);
        TarnishedRelics.LOGGER.info("[Tarnished Relics] loaded {} categories, {} rarities, {} affixes, {} legendaries ({} errors)",
                categories.size(), rarities.size(), affixes.size() - legendaries.size(), legendaries.size(), errors.size());
    }

    /** Forget tag lookups (tags change on /reload). */
    public void clearCaches() {
        categories.values().forEach(c -> c.items().clearCache());
        rarities.values().forEach(r -> r.items().clearCache());
        legendaries.values().forEach(l -> l.items().clearCache());
        exclusions.clearCache();
        levels.clearCache();
        categoryCache.clear();
    }

    public Map<ResourceLocation, Category> categories() {
        return Collections.unmodifiableMap(categories);
    }

    public Map<ResourceLocation, Rarity> rarities() {
        return Collections.unmodifiableMap(rarities);
    }

    public Map<ResourceLocation, Legendary> legendaries() {
        return Collections.unmodifiableMap(legendaries);
    }

    static String fileId(String folder, ResourceLocation id) {
        return id.getNamespace() + ":" + folder + "/" + id.getPath();
    }

    public Map<ResourceLocation, Affix> affixes() {
        return Collections.unmodifiableMap(affixes);
    }

    public Levels levels() {
        return levels;
    }

    /** The merged exclusions list: these items are never relics. */
    public ItemList exclusions() {
        return exclusions;
    }

    public NameList names() {
        return names;
    }

    public List<String> errors() {
        return Collections.unmodifiableList(errors);
    }

    public Map<ResourceLocation, String> rawJson() {
        return rawJson;
    }

    // item -> its category (highest priority wins), cached per item
    private final Map<Item, Optional<Category>> categoryCache = new ConcurrentHashMap<>();

    @Nullable
    public Category categoryOf(Item item) {
        return categoryCache.computeIfAbsent(item, it -> exclusions.contains(it) ? Optional.<Category>empty() : categories.values().stream()
                .filter(c -> c.matches(it))
                .max(Comparator.comparingInt(Category::priority))).orElse(null);
    }
}
