package com.flankrat.tarnishedrelics.data;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reports category ids that name no category: a legendary's "category", an affix's "applies_to" and "@category"
 * item entries. A typo there used to fail silently (the legendary never dropped, the affix never rolled, the entry
 * matched nothing). A category switched off with "disabled" isn't an error: that's on purpose. Run by TRData once
 * every file is parsed.
 */
final class CategoryNames {

    private final TRData data;
    private final Set<ResourceLocation> disabled;
    private final List<String> errors;

    private CategoryNames(TRData data, Set<ResourceLocation> disabled, List<String> errors) {
        this.data = data;
        this.disabled = disabled;
        this.errors = errors;
    }

    /** exclusionFiles: each exclusions file's entries (they're merged into one list, so the files are passed in). */
    static void check(TRData data, Map<ResourceLocation, List<String>> exclusionFiles, Set<ResourceLocation> disabled, List<String> errors) {
        CategoryNames c = new CategoryNames(data, disabled, errors);
        for (Legendary l : data.legendaries().values()) {
            String file = TRData.fileId("legendaries", l.id());
            if (l.category() != null) c.category(file, l.category().toString(), "category");
            c.atEntries(file, l.items());
        }
        for (Affix a : data.affixes().values()) {
            if (a.pool().equals(Legendary.POOL)) continue;   // a legendary's own effects
            for (String cat : a.appliesTo()) if (!cat.equals("*")) c.category(TRData.fileId("affixes", a.id()), cat, "applies_to");
        }
        for (Category cat : data.categories().values()) c.atEntries(TRData.fileId("categories", cat.id()), cat.items());
        for (Rarity r : data.rarities().values()) c.atEntries(TRData.fileId("rarities", r.id()), r.items());
        exclusionFiles.forEach((file, entries) -> {
            for (String e : entries) if (e.startsWith("@")) c.category(file.toString(), e.substring(1), "items");
        });
    }

    private void atEntries(String file, ItemList list) {
        for (String e : list.entries()) if (e.startsWith("@")) category(file, e.substring(1), "items");
        for (String e : list.excluded()) if (e.startsWith("@")) category(file, e.substring(1), "exclude");
    }

    private void category(String file, String category, String field) {
        ResourceLocation id = ResourceLocation.tryParse(category);
        if (id == null || data.categories().containsKey(id) || disabled.contains(id)) return;
        errors.add(file + ": no category " + id + " (" + field + ")");
    }
}
