package com.flankrat.tarnishedrelics.data;

import com.google.gson.JsonObject;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Words for generated Unique names ({@code tarnished_relics/names/<any>.json}). All files are merged.
 * <pre>
 * {
 *   "names": ["Ithireal", "Grimfang"],              whole names, used as they are
 *   "starts": ["Ith", "Mor", "Vael"],               or built from a start + an end
 *   "ends": ["ireal", "goth", "andor"]
 * }
 * </pre>
 * Half the time a whole name is picked (when there are any), otherwise a start + end.
 */
public final class NameList {

    private final List<String> names = new ArrayList<>();
    private final List<String> starts = new ArrayList<>();
    private final List<String> ends = new ArrayList<>();

    void add(JsonObject o) {
        names.addAll(Conditions.strings(o.get("names")));
        starts.addAll(Conditions.strings(o.get("starts")));
        ends.addAll(Conditions.strings(o.get("ends")));
    }

    public String generate(RandomSource random) {
        boolean canBuild = !starts.isEmpty() && !ends.isEmpty();
        if (!names.isEmpty() && (!canBuild || random.nextBoolean())) return names.get(random.nextInt(names.size()));
        if (canBuild) return starts.get(random.nextInt(starts.size())) + ends.get(random.nextInt(ends.size()));
        return "the Unknown";
    }
}
