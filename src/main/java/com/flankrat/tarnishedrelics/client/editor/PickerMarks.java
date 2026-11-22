package com.flankrat.tarnishedrelics.client.editor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The values shift-clicked in a picker that can add several at once. Kept by value, not by list row, so marks
 * survive changing the search text or the mod/kind filters. Keeps the order they were clicked in.
 */
final class PickerMarks {

    private final Set<String> marked = new LinkedHashSet<>();

    /** Shift-click: marks the value, or unmarks it if it was marked. */
    void toggle(String value) {
        if (!marked.remove(value)) marked.add(value);
    }

    boolean isMarked(String value) {
        return marked.contains(value);
    }

    int size() {
        return marked.size();
    }

    /**
     * What Select / Enter / double-click picks: every marked value (in click order) plus the highlighted one
     * if it isn't marked yet, or just the highlighted one when nothing is marked. Empty if neither.
     */
    List<String> picked(String highlighted) {
        List<String> out = new ArrayList<>(marked);
        if (highlighted != null && !marked.contains(highlighted)) out.add(highlighted);
        return out;
    }
}
