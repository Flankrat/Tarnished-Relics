package com.flankrat.tarnishedrelics.editor;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.flankrat.tarnishedrelics.TRConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraftforge.fml.config.ModConfig;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The config file (config/tarnishedrelics-common.toml) as JSON, so the editor can show and change it like the
 * datapack files. Keys are the toml's option names. Saving sets the values and writes the toml; they take effect
 * at once (no reload needed). The config belongs to the game folder, not a world: on a server it's the server's.
 */
public final class ConfigFile {

    public static final Set<String> LOOT_TYPES = TRConfig.LOOT_TYPE_NAMES;

    private ConfigFile() {
    }

    public static JsonObject read() {
        JsonObject o = new JsonObject();
        o.addProperty("enabled", TRConfig.ENABLED.get());
        o.addProperty("chance", TRConfig.CHANCE.get());
        o.add("lootTypes", array(TRConfig.LOOT_TYPES.get()));
        o.addProperty("entityNeedsPlayerKill", TRConfig.ENTITY_NEEDS_PLAYER.get());
        o.add("excludeTables", array(TRConfig.EXCLUDE_TABLES.get()));
        o.addProperty("luckRollBonus", TRConfig.LUCK_ROLL_BONUS.get());
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
            double luck = o.get("luckRollBonus").getAsDouble();
            if (luck < 0 || luck > 2) errors.add("luckRollBonus must be between 0 and 2");
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

    /**
     * Saves the values to the toml, and they take effect at once. All of them go into the file in one write, which
     * the loaded config then reads back. Setting them one by one lost values: Forge saves the file after every
     * set and reads it back on its file-watcher thread, and a read-back between two sets put older values back in
     * memory, which the next set then saved. The file is written to a temp file and moved into place, because the
     * watcher also read a half-written file, found keys missing and "corrected" them to their defaults.
     */
    public static void write(JsonObject o) {
        ModConfig config = TRConfig.loaded();
        if (config == null || !(config.getConfigData() instanceof CommentedFileConfig live)) {
            setEach(o);   // not loaded from a file (shouldn't happen): set them the plain way
            return;
        }
        Path path = live.getNioPath();
        try (CommentedFileConfig file = CommentedFileConfig.builder(path).sync().preserveInsertionOrder().build()) {
            file.load();   // a second handle on the same file (keeps its comments), without auto-save
            file.set(TRConfig.ENABLED.getPath(), o.get("enabled").getAsBoolean());
            file.set(TRConfig.CHANCE.getPath(), o.get("chance").getAsDouble());
            file.set(TRConfig.LOOT_TYPES.getPath(), strings(o.getAsJsonArray("lootTypes")));
            file.set(TRConfig.ENTITY_NEEDS_PLAYER.getPath(), o.get("entityNeedsPlayerKill").getAsBoolean());
            file.set(TRConfig.EXCLUDE_TABLES.getPath(), strings(o.getAsJsonArray("excludeTables")));
            file.set(TRConfig.LUCK_ROLL_BONUS.getPath(), o.get("luckRollBonus").getAsDouble());
            file.set(TRConfig.CONFIRM_SECONDS.getPath(), o.get("confirmSeconds").getAsInt());
            file.set(TRConfig.SHOW_RARITY_COLOR.getPath(), o.get("showRarityColorUnidentified").getAsBoolean());
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            TomlFormat.instance().createWriter().write(file, temp.toFile(), WritingMode.REPLACE);
            move(temp, path);
        }
        // the loaded config reads the finished file now (the file watcher reading it again later changes nothing)
        live.load();
        TRConfig.SPEC.afterReload();   // forget the cached values
    }

    // in one step where the system can, so nothing ever reads half a file
    private static void move(Path from, Path to) {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException atomicFailed) {
            try {
                Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new UncheckedIOException("couldn't save " + to, e);
            }
        }
    }

    private static void setEach(JsonObject o) {
        TRConfig.ENABLED.set(o.get("enabled").getAsBoolean());
        TRConfig.CHANCE.set(o.get("chance").getAsDouble());
        TRConfig.LOOT_TYPES.set(strings(o.getAsJsonArray("lootTypes")));
        TRConfig.ENTITY_NEEDS_PLAYER.set(o.get("entityNeedsPlayerKill").getAsBoolean());
        TRConfig.EXCLUDE_TABLES.set(strings(o.getAsJsonArray("excludeTables")));
        TRConfig.LUCK_ROLL_BONUS.set(o.get("luckRollBonus").getAsDouble());
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
