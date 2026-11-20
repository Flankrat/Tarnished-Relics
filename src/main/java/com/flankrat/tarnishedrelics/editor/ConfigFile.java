package com.flankrat.tarnishedrelics.editor;

import com.flankrat.tarnishedrelics.TRConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The config file (config/tarnishedrelics-common.toml) as JSON, so the editor can show and change it like the
 * datapack files. Keys are the toml's option names. Saving sets the values and writes the toml; they take effect
 * at once (no reload needed). The config belongs to the game folder, not a world: on a server it's the server's.
 */
public final class ConfigFile {

    public static final Set<String> LOOT_TYPES = Set.of("chest", "entity", "fishing", "archaeology", "gift", "barter");

    private ConfigFile() {
    }

    public static JsonObject read() {
        JsonObject o = new JsonObject();
        o.addProperty("enabled", TRConfig.ENABLED.get());
        o.addProperty("chance", TRConfig.CHANCE.get());
        o.add("lootTypes", array(TRConfig.LOOT_TYPES.get()));
        o.addProperty("entityNeedsPlayerKill", TRConfig.ENTITY_NEEDS_PLAYER.get());
        o.add("excludeTables", array(TRConfig.EXCLUDE_TABLES.get()));
        o.addProperty("confirmSeconds", TRConfig.CONFIRM_SECONDS.get());
        o.addProperty("showRarityColorUnidentified", TRConfig.SHOW_RARITY_COLOR.get());
        return o;
    }

    private static JsonArray array(List<? extends String> list) {
        JsonArray a = new JsonArray();
        list.forEach(a::add);
        return a;
    }

    /** Problems with the values (empty list = fine). */
    public static List<String> check(JsonObject o) {
        List<String> errors = new ArrayList<>();
        try {
            double chance = o.get("chance").getAsDouble();
            if (chance < 0 || chance > 1) errors.add("chance must be between 0 and 1");
            int confirm = o.get("confirmSeconds").getAsInt();
            if (confirm < 1 || confirm > 120) errors.add("confirmSeconds must be 1-120");
            for (JsonElement t : o.getAsJsonArray("lootTypes")) {
                if (!LOOT_TYPES.contains(t.getAsString())) errors.add("unknown loot type " + t.getAsString());
            }
            o.get("enabled").getAsBoolean();
            o.get("entityNeedsPlayerKill").getAsBoolean();
            o.get("showRarityColorUnidentified").getAsBoolean();
            o.getAsJsonArray("excludeTables");
        } catch (RuntimeException e) {
            errors.add("missing or wrong value (" + e.getMessage() + ")");
        }
        return errors;
    }

    public static void write(JsonObject o) {
        TRConfig.ENABLED.set(o.get("enabled").getAsBoolean());
        TRConfig.CHANCE.set(o.get("chance").getAsDouble());
        TRConfig.LOOT_TYPES.set(strings(o.getAsJsonArray("lootTypes")));
        TRConfig.ENTITY_NEEDS_PLAYER.set(o.get("entityNeedsPlayerKill").getAsBoolean());
        TRConfig.EXCLUDE_TABLES.set(strings(o.getAsJsonArray("excludeTables")));
        TRConfig.CONFIRM_SECONDS.set(o.get("confirmSeconds").getAsInt());
        TRConfig.SHOW_RARITY_COLOR.set(o.get("showRarityColorUnidentified").getAsBoolean());
        TRConfig.SPEC.save();
    }

    private static List<String> strings(JsonArray a) {
        List<String> out = new ArrayList<>();
        for (JsonElement e : a) out.add(e.getAsString());
        return out;
    }
}
