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
 *   "ends": ["ireal", "goth", "andor"],
 *   "titles": ["the Last Light"],                   generated legendaries: "<name>, <title>" half the time
 *   "lore": ["{name} was never meant to be found."] generated legendaries: lore lines ({name} = its name)
 * }
 * </pre>
 * Half the time a whole name is picked (when there are any), otherwise a start + end.
 */
public final class NameList {

    private final List<String> names = new ArrayList<>();
    private final List<String> starts = new ArrayList<>();
    private final List<String> ends = new ArrayList<>();
    private final List<String> titles = new ArrayList<>();
    private final List<String> lore = new ArrayList<>();

    void add(JsonObject o) {
        names.addAll(Conditions.strings(o.get("names")));
        starts.addAll(Conditions.strings(o.get("starts")));
        ends.addAll(Conditions.strings(o.get("ends")));
        titles.addAll(Conditions.strings(o.get("titles")));
        lore.addAll(Conditions.strings(o.get("lore")));
    }

    /** A generated legendary's name: a name, and half the time ", <title>" ("Grimfang, the Last Light"). */
    public String generateLegendary(RandomSource random) {
        String name = generate(random);
        if (!titles.isEmpty() && random.nextBoolean()) name += ", " + titles.get(random.nextInt(titles.size()));
        return name;
    }

    /** count different lore lines, with {name} replaced by the item's name. */
    public List<String> lore(RandomSource random, int count, String name) {
        List<String> pool = new ArrayList<>(lore);
        List<String> out = new ArrayList<>();
        String shortName = name.contains(",") ? name.substring(0, name.indexOf(',')) : name;
        for (int i = 0; i < count && !pool.isEmpty(); i++) out.add(pool.remove(random.nextInt(pool.size())).replace("{name}", shortName));
        return out;
    }

    public String generate(RandomSource random) {
        boolean canBuild = !starts.isEmpty() && !ends.isEmpty();
        if (!names.isEmpty() && (!canBuild || random.nextBoolean())) return names.get(random.nextInt(names.size()));
        if (canBuild) return starts.get(random.nextInt(starts.size())) + ends.get(random.nextInt(ends.size()));
        return "the Unknown";
    }
}
