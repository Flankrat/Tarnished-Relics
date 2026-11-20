package com.flankrat.tarnishedrelics.name;

import java.util.Locale;

/**
 * Readable names for things that have no lang entry (a category or rarity made in a datapack or the editor), built
 * from their id: "tarnishedrelics:war_axe" -> "War Axe".
 */
public final class NameFallback {

    private NameFallback() {
    }

    /** "War Axe" from "ns:war_axe" (or from "war_axe"). */
    public static String title(String id) {
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        path = path.substring(path.lastIndexOf('/') + 1);
        StringBuilder out = new StringBuilder();
        for (String word : path.split("_")) {
            if (word.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return out.length() == 0 ? "Relic" : out.toString();
    }

    public static String unidentified(String categoryId) {
        return categoryId.isEmpty() ? "Unidentified Relic" : "Unidentified " + title(categoryId);
    }
}
