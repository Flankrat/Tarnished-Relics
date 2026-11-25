package com.flankrat.tarnishedrelics.editor;

import com.flankrat.tarnishedrelics.TarnishedRelics;
import com.flankrat.tarnishedrelics.api.RelicGeneratedEvent;
import com.flankrat.tarnishedrelics.data.Category;
import com.flankrat.tarnishedrelics.data.Legendary;
import com.flankrat.tarnishedrelics.data.Rarity;
import com.flankrat.tarnishedrelics.data.TRData;
import com.flankrat.tarnishedrelics.data.TRDataLoader;
import com.flankrat.tarnishedrelics.network.TRNetwork;
import com.flankrat.tarnishedrelics.relic.Roller;
import com.flankrat.tarnishedrelics.relic.UnidentifiedItem;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.network.PacketDistributor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Server side of the in-game editor ({@code /tarnishedrelics editor}, op level 2; saving for the whole modpack or
 * changing the config needs level 4, since every world uses those).
 * <p>
 * The editor works on "files" with ids "namespace:folder/name":
 * <ul>
 *   <li>the Tarnished Relics datapack folders (categories, rarities, affixes, names, levels, exclusions);</li>
 *   <li>{@code tags/<registry>/<name>}: the mod's tags in its namespace (blacksmith professions, the undead and
 *       arthropods mob lists...). Tags from every pack add up, so the editor shows the merged list;</li>
 *   <li>{@code tarnishedrelics:config/common}: the config file's values (not a datapack: saved to the toml).</li>
 * </ul>
 * Saves go to one of two datapacks:
 * <ul>
 *   <li>"This world": {@code <world>/datapacks/tarnishedrelics_editor}, moved to the top of the pack list.</li>
 *   <li>"Whole modpack": {@code config/tarnishedrelics/datapack} (see {@link ModpackPack}), loaded by every world,
 *       so a modpack ships it with its config folder. The world's copy of the same file is removed so it doesn't
 *       hide the change.</li>
 * </ul>
 * A file removed in the editor is deleted from whichever of those two packs has the copy in use, so the version
 * underneath (another pack's, or the mod's defaults) is used again. A file is switched off with
 * {@code "disabled": true}, which the loader skips.
 * <p>
 * Saving is checked first: the edited files are parsed together with the rest and nothing is written if any of
 * them has an error. The client sends files one packet each (client packets are limited to 32 KB), then a commit.
 */
public final class EditorServer {

    public static final String PACK = "tarnishedrelics_editor";
    public static final String PACK_ID = "file/" + PACK;
    public static final ResourceLocation CONFIG_FILE = new ResourceLocation(TarnishedRelics.MODID, "config/common");
    private static final int PERMISSION = 2;
    // saves that reach beyond this world (the whole-modpack pack, the config) need a server operator's level
    private static final int PERMISSION_EVERYWHERE = 4;

    // what a player has sent but not committed yet
    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    // a player's unsent save: id -> JSON text ("" = remove), built piece by piece; the text total is kept as it
    // grows, so each piece costs only its own length
    private static final class Pending {
        final Map<ResourceLocation, StringBuilder> files = new LinkedHashMap<>();
        long chars;
        boolean refused;   // went over a limit: commit answers with an error instead of saving part of it

        Map<ResourceLocation, String> toStrings() {
            Map<ResourceLocation, String> out = new LinkedHashMap<>();
            files.forEach((id, text) -> out.put(id, text.toString()));
            return out;
        }
    }

    private EditorServer() {
    }

    public static boolean allowed(ServerPlayer player) {
        return player.hasPermissions(PERMISSION);
    }

    /** May save for the whole modpack and change the config: files every world loads, so a server operator only. */
    public static boolean allowedEverywhere(ServerPlayer player) {
        return player.hasPermissions(PERMISSION_EVERYWHERE);
    }

    public static boolean isTag(ResourceLocation id) {
        return id.getPath().startsWith("tags/");
    }

    /** Opens (or refreshes) the editor on the player's screen with every file and where it comes from. */
    public static void open(ServerPlayer player, boolean refresh) {
        if (!refresh) PENDING.remove(player.getUUID());   // files from an earlier session that never got a commit
        Map<ResourceLocation, String> files = new LinkedHashMap<>(TRData.get().rawJson());
        Map<ResourceLocation, String> origin = new LinkedHashMap<>();
        // every pack that has the file, lowest first, comma-separated (the last one is the copy in use)
        TRDataLoader.origins().forEach((id, packs) -> origin.put(id, String.join(",", packs)));
        ResourceManager rm = player.server.getResourceManager();
        rm.listResourceStacks("tags", l -> l.getNamespace().equals(TarnishedRelics.MODID) && l.getPath().endsWith(".json")).forEach((loc, stack) -> {
            ResourceLocation id = new ResourceLocation(loc.getNamespace(), loc.getPath().substring(0, loc.getPath().length() - 5));
            JsonObject shown = new JsonObject();
            shown.add("values", EditorTags.mergedValues(stack, Set.of()));
            files.put(id, shown.toString());
            origin.put(id, String.join(",", stack.stream().map(Resource::sourcePackId).toList()));
        });
        files.put(CONFIG_FILE, ConfigFile.read().toString());
        origin.put(CONFIG_FILE, "config");
        TRNetwork.sendEditor(player, files, origin, refresh);
    }

    /** Most text one save can send (all files together), so a broken client can't fill the server's memory. */
    private static final int MAX_PENDING_CHARS = 16 * 1024 * 1024;
    /**
     * Most files one save can send. Empty files (removals) are 0 characters, so the text limit alone let a broken
     * client queue any number of them; the editor sends only the files that changed, far fewer than this.
     */
    static final int MAX_PENDING_FILES = 4096;

    /** A file (or, with append, the next piece of one) sent for the next commit. */
    public static void receiveFile(ServerPlayer player, ResourceLocation id, String json, boolean append) {
        if (!allowed(player)) return;
        Pending pending = PENDING.computeIfAbsent(player.getUUID(), u -> new Pending());
        if (pending.refused) return;   // already over a limit: nothing more is kept
        StringBuilder text = pending.files.get(id);
        if (text == null) {
            text = new StringBuilder();
            pending.files.put(id, text);
        } else if (!append) {
            pending.chars -= text.length();   // sent again from the start: replaces the earlier text
            text.setLength(0);
        }
        text.append(json);
        pending.chars += json.length();
        if (pending.chars > MAX_PENDING_CHARS || pending.files.size() > MAX_PENDING_FILES) {
            pending.files.clear();
            pending.refused = true;
        }
    }

    /** Forgets a player's unsent save (they logged out). */
    public static void forget(ServerPlayer player) {
        PENDING.remove(player.getUUID());
    }

    public static void commit(ServerPlayer player, boolean modpack) {
        Pending pending = PENDING.remove(player.getUUID());
        Map<ResourceLocation, String> changes = pending == null ? null : pending.toStrings();
        // always answer, or the client's editor waits on "Saving..." forever
        if (!allowed(player)) {
            reply(player, false, List.of(Component.translatable("tarnishedrelics.editor.no_permission").getString()));
            return;
        }
        if (pending != null && pending.refused) {
            reply(player, false, List.of("Too much data in one save (over " + MAX_PENDING_CHARS / 1024 / 1024 + " MB of text or "
                    + MAX_PENDING_FILES + " files)"));
            return;
        }
        // the whole-modpack pack and the config are used by every world: a level 2 op only edits this world
        if ((modpack || (changes != null && changes.containsKey(CONFIG_FILE))) && !allowedEverywhere(player)) {
            reply(player, false, List.of(Component.translatable("tarnishedrelics.editor.no_permission_everywhere").getString()));
            return;
        }
        if (changes == null || changes.isEmpty()) {
            reply(player, true, TRData.get().errors());
            return;
        }
        // 1. check: nothing is written if a changed file has an error
        List<String> errors = check(changes);
        if (!errors.isEmpty()) {
            reply(player, false, errors);
            return;
        }
        // 2. write: config values to the toml, files to the chosen pack
        MinecraftServer server = player.server;
        boolean packFiles = changes.keySet().stream().anyMatch(id -> !id.equals(CONFIG_FILE));
        try {
            write(server, changes, modpack);
        } catch (IOException | RuntimeException ex) {
            TarnishedRelics.LOGGER.error("[Tarnished Relics] editor couldn't save", ex);
            List<String> error = List.of("Couldn't write the datapack: " + ex.getMessage());
            // files written before the error are on disk: reload so the server runs what's there, not older data
            if (packFiles) reloadThen(player, () -> {
                reply(player, false, error);
                open(player, true);
            });
            else reply(player, false, error);
            return;
        }
        TarnishedRelics.LOGGER.info("[Tarnished Relics] {} saved {} file(s) in the editor ({})", player.getGameProfile().getName(), changes.size(),
                modpack ? "whole modpack: " + ModpackPack.folder().toAbsolutePath().normalize() : "this world");

        Runnable done = () -> {
            player.sendSystemMessage(Component.translatable(modpack ? "tarnishedrelics.editor.saved_chat_modpack" : "tarnishedrelics.editor.saved_chat", changes.size()));
            reply(player, true, TRData.get().errors());
            open(player, true);
        };
        // 3. reload (only the config changed: nothing to reload), then refresh the editor
        if (packFiles) reloadThen(player, done);
        else done.run();
    }

    private static void reply(ServerPlayer player, boolean saved, List<String> errors) {
        TRNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new TRNetwork.EditorResult(saved, errors));
    }

    /** The errors in the changed files: the current files with the changes applied are parsed together. */
    static List<String> check(Map<ResourceLocation, String> changes) {
        List<String> errors = new ArrayList<>();
        Map<ResourceLocation, JsonElement> merged = new LinkedHashMap<>();
        TRData.get().rawJson().forEach((id, s) -> merged.put(id, JsonParser.parseString(s)));
        for (Map.Entry<ResourceLocation, String> e : changes.entrySet()) {
            ResourceLocation id = e.getKey();
            boolean special = isTag(id) || id.equals(CONFIG_FILE);
            // an id that would be written outside the mod's folders is refused here, before anything is written
            // (found while writing, the files before it were already saved)
            if (!id.equals(CONFIG_FILE) && !EditorFiles.isWritable(id)) {
                errors.add(id + ": bad file id (outside the mod's folders)");
                continue;
            }
            if (e.getValue().isEmpty()) {
                // "" removes a file; the config isn't one, and parsing "" for it crashed the write
                if (id.equals(CONFIG_FILE)) errors.add(id + ": the config can't be removed");
                else merged.remove(id);
                continue;
            }
            try {
                JsonElement json = JsonParser.parseString(e.getValue());
                if (!json.isJsonObject()) throw new IllegalStateException("must be a JSON object");
                if (isTag(id)) EditorTags.check(id, json.getAsJsonObject(), errors);
                if (id.equals(CONFIG_FILE)) for (String err : ConfigFile.check(json.getAsJsonObject())) errors.add(id + ": " + err);
                if (!special) merged.put(id, json);
            } catch (RuntimeException ex) {
                errors.add(id + ": not valid JSON (" + ex.getMessage() + ")");
            }
        }
        if (errors.isEmpty()) {
            for (String err : TRData.parseOnly(merged).errors()) {
                if (changes.keySet().stream().anyMatch(id -> err.startsWith(id + ":"))) errors.add(err);
            }
        }
        return errors;
    }

    // writes the checked changes: the config to the toml, the rest to the world's or the modpack's pack
    private static void write(MinecraftServer server, Map<ResourceLocation, String> changes, boolean modpack) throws IOException {
        Path worldPack = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(PACK).toAbsolutePath().normalize();
        Path modpackPack = ModpackPack.folder().toAbsolutePath().normalize();
        boolean packFiles = changes.keySet().stream().anyMatch(id -> !id.equals(CONFIG_FILE));
        // everything that can throw on bad values is worked out before the first file is touched
        Map<ResourceLocation, String> toWrite = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, String> e : changes.entrySet()) {
            ResourceLocation id = e.getKey();
            if (id.equals(CONFIG_FILE) || e.getValue().isEmpty()) toWrite.put(id, e.getValue());
            else toWrite.put(id, isTag(id) ? EditorTags.toWrite(server, id, JsonParser.parseString(e.getValue()).getAsJsonObject()) : e.getValue());
        }
        JsonObject config = changes.containsKey(CONFIG_FILE) ? JsonParser.parseString(changes.get(CONFIG_FILE)).getAsJsonObject() : null;
        if (packFiles && !modpack) EditorFiles.writePackMeta(worldPack);
        if (packFiles && modpack) ModpackPack.ensure();
        for (Map.Entry<ResourceLocation, String> e : toWrite.entrySet()) {
            ResourceLocation id = e.getKey();
            if (id.equals(CONFIG_FILE)) {
                ConfigFile.write(config);
                TRNetwork.sendSettings();   // players show what the server set, not their own config
                continue;
            }
            if (e.getValue().isEmpty()) {
                EditorFiles.remove(server, worldPack, modpackPack, id);
                continue;
            }
            EditorFiles.write(modpack ? modpackPack : worldPack, id, e.getValue());
            // saved for the whole modpack: the world's own copy would hide it, so it goes
            if (modpack) Files.deleteIfExists(EditorFiles.file(worldPack, id));
        }
    }

    // turns the world pack on (at the top), makes sure the modpack pack is on, reloads, then runs done
    private static void reloadThen(ServerPlayer player, Runnable done) {
        MinecraftServer server = player.server;
        PackRepository repo = server.getPackRepository();
        repo.reload();
        List<String> selected = new ArrayList<>(repo.getSelectedIds());
        if (repo.getAvailableIds().contains(ModpackPack.ID) && !selected.contains(ModpackPack.ID)) selected.add(ModpackPack.ID);
        if (repo.getAvailableIds().contains(PACK_ID)) {
            selected.remove(PACK_ID);
            selected.add(PACK_ID);
        }
        afterReload(server.reloadResources(selected), server, done, ex -> {
            // a failed reload (another pack broke it): the files are written, but say so instead of waiting forever
            TarnishedRelics.LOGGER.error("[Tarnished Relics] reload after the editor's save failed", ex);
            reply(player, false, List.of("Saved, but the reload failed: " + ex.getMessage()));
        });
    }

    /**
     * Runs done on the executor after the reload, or failed with the reload's own error. An error thrown by done
     * itself (refreshing the editor) is only logged: the reload worked, so the op isn't told it failed.
     */
    static java.util.concurrent.CompletableFuture<Void> afterReload(java.util.concurrent.CompletableFuture<Void> reload,
                                                                   java.util.concurrent.Executor executor, Runnable done,
                                                                   java.util.function.Consumer<Throwable> failed) {
        return reload.handle((v, reloadError) -> reloadError).thenAcceptAsync(reloadError -> {
            if (reloadError != null) failed.accept(reloadError);
            else done.run();
        }, executor).exceptionally(ex -> {
            TarnishedRelics.LOGGER.error("[Tarnished Relics] error after the editor's save was reloaded", ex);
            return null;
        });
    }

    public static void cancel(ServerPlayer player) {
        PENDING.remove(player.getUUID());
    }

    /** Gives the op a freshly rolled relic (from the saved data), unidentified or already identified. */
    public static void give(ServerPlayer player, @Nullable ResourceLocation rarityId, @Nullable ResourceLocation categoryId,
                            @Nullable ResourceLocation legendaryId, boolean identified) {
        if (!allowed(player)) return;
        TRData data = TRData.get();
        Rarity rarity = rarityId == null ? null : data.rarities().get(rarityId);
        Category category = categoryId == null ? null : data.categories().get(categoryId);
        Legendary legendary = legendaryId == null ? null : data.legendaries().get(legendaryId);
        List<ItemStack> relics = Roller.generateAll(player.getRandom(), rarity, category, legendary,
                RelicGeneratedEvent.Source.COMMAND, null);
        if (relics.isEmpty()) {
            player.sendSystemMessage(Component.translatable("tarnishedrelics.editor.give_none"));
            return;
        }
        for (ItemStack relic : relics) {
            ItemStack out = identified ? UnidentifiedItem.hidden(relic) : relic;
            Component name = out.getDisplayName();
            if (!player.getInventory().add(out)) player.drop(out, false);
            player.sendSystemMessage(Component.translatable("tarnishedrelics.editor.given", name));
        }
    }
}
