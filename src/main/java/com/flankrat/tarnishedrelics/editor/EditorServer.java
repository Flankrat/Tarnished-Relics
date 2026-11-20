package com.flankrat.tarnishedrelics.editor;

import com.flankrat.tarnishedrelics.TarnishedRelics;
import com.flankrat.tarnishedrelics.data.TRData;
import com.flankrat.tarnishedrelics.data.TRDataLoader;
import com.flankrat.tarnishedrelics.network.TRNetwork;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.network.PacketDistributor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of the in-game editor ({@code /tarnishedrelics editor}, op level 2).
 * <p>
 * Edits are saved as a normal datapack in the world, {@code datapacks/tarnishedrelics_editor}, so they work and
 * can be shared like any other datapack (copy the folder into a modpack's global datapacks or KubeJS). The pack is
 * moved to the top of the pack list on every save, so its files win over the same files from mods and other packs.
 * A file removed in the editor is deleted from the editor pack, so another pack's copy (a mod's defaults) is used
 * again. A file is switched off with {@code "disabled": true}, which the loader skips.
 * <p>
 * Saving is checked first: the edited files are parsed together with the rest and nothing is written if any of
 * them has an error. The client sends files one packet each (client packets are limited to 32 KB), then a commit.
 */
public final class EditorServer {

    public static final String PACK = "tarnishedrelics_editor";
    public static final String PACK_ID = "file/" + PACK;
    private static final int PERMISSION = 2;

    // files a player has sent but not committed yet: id -> JSON text ("" = remove)
    private static final Map<UUID, Map<ResourceLocation, String>> PENDING = new HashMap<>();

    private EditorServer() {
    }

    public static boolean allowed(ServerPlayer player) {
        return player.hasPermissions(PERMISSION);
    }

    /** Opens (or refreshes) the editor on the player's screen with every file and where it comes from. */
    public static void open(ServerPlayer player, boolean refresh) {
        Map<ResourceLocation, String> origin = new LinkedHashMap<>();
        // every pack that has the file, lowest first, comma-separated (the last one is the copy in use)
        TRDataLoader.origins().forEach((id, packs) -> origin.put(id, String.join(",", packs)));
        TRNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new TRNetwork.OpenEditor(TRData.get().rawJson(), origin, refresh));
    }

    public static void receiveFile(ServerPlayer player, ResourceLocation id, String json) {
        if (!allowed(player)) return;
        PENDING.computeIfAbsent(player.getUUID(), u -> new LinkedHashMap<>()).put(id, json);
    }

    public static void commit(ServerPlayer player) {
        Map<ResourceLocation, String> changes = PENDING.remove(player.getUUID());
        if (!allowed(player) || changes == null || changes.isEmpty()) return;

        // 1. check: current files with the changes applied must parse without errors in the changed files
        Map<ResourceLocation, JsonElement> merged = new LinkedHashMap<>();
        TRData.get().rawJson().forEach((id, s) -> merged.put(id, JsonParser.parseString(s)));
        List<String> errors = new ArrayList<>();
        for (Map.Entry<ResourceLocation, String> e : changes.entrySet()) {
            if (e.getValue().isEmpty()) {
                merged.remove(e.getKey());
                continue;
            }
            try {
                merged.put(e.getKey(), JsonParser.parseString(e.getValue()));
            } catch (RuntimeException ex) {
                errors.add(e.getKey() + ": not valid JSON (" + ex.getMessage() + ")");
            }
        }
        if (errors.isEmpty()) {
            for (String err : TRData.parseOnly(merged).errors()) {
                if (changes.keySet().stream().anyMatch(id -> err.startsWith(id + ":"))) errors.add(err);
            }
        }
        if (!errors.isEmpty()) {
            TRNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new TRNetwork.EditorResult(false, errors));
            return;
        }

        // 2. write the editor pack
        MinecraftServer server = player.server;
        Path pack = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(PACK).toAbsolutePath().normalize();
        try {
            writePackMeta(pack);
            for (Map.Entry<ResourceLocation, String> e : changes.entrySet()) write(server, pack, e.getKey(), e.getValue());
        } catch (IOException ex) {
            TarnishedRelics.LOGGER.error("[Tarnished Relics] editor couldn't save", ex);
            TRNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new TRNetwork.EditorResult(false, List.of("Couldn't write the datapack: " + ex.getMessage())));
            return;
        }
        TarnishedRelics.LOGGER.info("[Tarnished Relics] {} saved {} file(s) in the editor", player.getGameProfile().getName(), changes.size());

        // 3. turn the pack on (at the top) and reload, then refresh the editor with the result
        PackRepository repo = server.getPackRepository();
        repo.reload();
        List<String> selected = new ArrayList<>(repo.getSelectedIds());
        selected.remove(PACK_ID);
        selected.add(PACK_ID);
        server.reloadResources(selected).thenRunAsync(() -> {
            player.sendSystemMessage(Component.translatable("tarnishedrelics.editor.saved_chat", changes.size()));
            TRNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new TRNetwork.EditorResult(true, TRData.get().errors()));
            open(player, true);
        }, server);
    }

    public static void cancel(ServerPlayer player) {
        PENDING.remove(player.getUUID());
    }

    private static void writePackMeta(Path pack) throws IOException {
        Files.createDirectories(pack);
        Path meta = pack.resolve("pack.mcmeta");
        if (!Files.exists(meta)) {
            Files.writeString(meta, "{\n  \"pack\": {\n    \"pack_format\": 15,\n    \"description\": \"Tarnished Relics changes made in the in-game editor\"\n  }\n}\n");
        }
    }

    private static void write(MinecraftServer server, Path pack, ResourceLocation id, String json) throws IOException {
        Path file = pack.resolve("data").resolve(id.getNamespace()).resolve(TRDataLoader.FOLDER).resolve(id.getPath() + ".json");
        if (!file.normalize().startsWith(pack)) throw new IOException("bad file id " + id);   // no ../ tricks
        if (json.isEmpty()) {
            // removed: delete the editor's copy. If another pack has the file, its version is used again
            // (switching a file off is a normal edit: "disabled": true)
            Files.deleteIfExists(file);
            return;
        }
        Files.createDirectories(file.getParent());
        // pretty-print so the saved pack is readable and diffs nicely
        String pretty = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(JsonParser.parseString(json));
        Files.writeString(file, pretty + "\n", StandardCharsets.UTF_8);
    }
}
