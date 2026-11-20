package com.flankrat.tarnishedrelics.data;

import com.flankrat.tarnishedrelics.TarnishedRelics;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private final NameList names = new NameList();
    private final List<String> errors = new ArrayList<>();
    private final Map<ResourceLocation, String> rawJson;   // "folder/name" ids -> JSON, for the client sync

    public static TRData get() {
        return current;
    }

    /** Parses a full set of files and makes it the current data. Ids are "<folder>/<name>" under tarnished_relics/. */
    public static TRData load(Map<ResourceLocation, JsonElement> files) {
        Map<ResourceLocation, String> raw = new LinkedHashMap<>();
        files.forEach((id, json) -> raw.put(id, json.toString()));
        TRData data = new TRData(raw);
        data.parse(files);
        current = data;
        return data;
    }

    /** Client side: rebuilds the data from the JSON the server sent. */
    public static void loadFromSync(Map<ResourceLocation, String> raw) {
        Map<ResourceLocation, JsonElement> files = new LinkedHashMap<>();
        raw.forEach((id, s) -> files.put(id, com.google.gson.JsonParser.parseString(s)));
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
                switch (folder) {
                    case "categories" -> categories.put(id, Category.parse(id, o));
                    case "rarities" -> rarities.put(id, Rarity.parse(id, o));
                    case "affixes" -> affixes.put(id, Affix.parse(id, o));
                    case "names" -> names.add(o);
                    default -> errors.add(file + ": unknown folder \"" + folder + "\"");
                }
            } catch (RuntimeException ex) {
                // One bad file is skipped and reported; the rest still load
                errors.add(file + ": " + ex.getMessage());
            }
        }
        for (String err : errors) TarnishedRelics.LOGGER.error("[Tarnished Relics] bad datapack file {}", err);
        TarnishedRelics.LOGGER.info("[Tarnished Relics] loaded {} categories, {} rarities, {} affixes ({} errors)",
                categories.size(), rarities.size(), affixes.size(), errors.size());
    }

    /** Forget tag lookups (tags change on /reload). */
    public void clearCaches() {
        categories.values().forEach(c -> c.items().clearCache());
        rarities.values().forEach(r -> r.items().clearCache());
        categoryCache.clear();
    }

    public Map<ResourceLocation, Category> categories() {
        return Collections.unmodifiableMap(categories);
    }

    public Map<ResourceLocation, Rarity> rarities() {
        return Collections.unmodifiableMap(rarities);
    }

    public Map<ResourceLocation, Affix> affixes() {
        return Collections.unmodifiableMap(affixes);
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
    private final Map<Item, java.util.Optional<Category>> categoryCache = new java.util.concurrent.ConcurrentHashMap<>();

    @Nullable
    public Category categoryOf(Item item) {
        return categoryCache.computeIfAbsent(item, it -> categories.values().stream()
                .filter(c -> c.items().contains(it))
                .max(java.util.Comparator.comparingInt(Category::priority))).orElse(null);
    }
}
