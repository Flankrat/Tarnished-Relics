package com.flankrat.tarnishedrelics.editor;

import com.flankrat.tarnishedrelics.TarnishedRelics;
import com.flankrat.tarnishedrelics.data.Category;
import com.flankrat.tarnishedrelics.data.Rarity;
import com.flankrat.tarnishedrelics.data.TRData;
import com.flankrat.tarnishedrelics.data.TRDataLoader;
import com.flankrat.tarnishedrelics.network.TRNetwork;
import com.flankrat.tarnishedrelics.relic.Roller;
import com.flankrat.tarnishedrelics.relic.UnidentifiedItem;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
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

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server side of the in-game editor ({@code /tarnishedrelics editor}, op level 2).
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

    // files a player has sent but not committed yet: id -> JSON text ("" = remove)
    private static final Map<UUID, Map<ResourceLocation, String>> PENDING = new HashMap<>();

    private EditorServer() {
    }

    public static boolean allowed(ServerPlayer player) {
        return player.hasPermissions(PERMISSION);
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
            shown.add("values", mergedValues(stack, Set.of()));
            files.put(id, shown.toString());
            origin.put(id, String.join(",", stack.stream().map(Resource::sourcePackId).toList()));
        });
        files.put(CONFIG_FILE, ConfigFile.read().toString());
        origin.put(CONFIG_FILE, "config");
        TRNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new TRNetwork.OpenEditor(files, origin, refresh));
    }

    /** A tag's values across packs, lowest first, honouring "replace". Packs in skip are left out. */
    private static JsonArray mergedValues(List<Resource> stack, Set<String> skip) {
        JsonArray out = new JsonArray();
        for (Resource r : stack) {
            if (skip.contains(r.sourcePackId())) continue;
            try (Reader reader = r.openAsReader()) {
                JsonObject o = JsonParser.parseReader(reader).getAsJsonObject();
                if (o.has("replace") && o.get("replace").getAsBoolean()) out = new JsonArray();
                if (o.has("values")) for (JsonElement v : o.getAsJsonArray("values")) if (!out.contains(v)) out.add(v);
            } catch (IOException | RuntimeException e) {
                // a broken tag file: vanilla reports it, the editor just skips it
            }
        }
        return out;
    }

    public static void receiveFile(ServerPlayer player, ResourceLocation id, String json) {
        if (!allowed(player)) return;
        PENDING.computeIfAbsent(player.getUUID(), u -> new LinkedHashMap<>()).put(id, json);
    }

    public static void commit(ServerPlayer player, boolean modpack) {
        Map<ResourceLocation, String> changes = PENDING.remove(player.getUUID());
        // always answer, or the client's editor waits on "Saving..." forever
        if (!allowed(player)) {
            TRNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new TRNetwork.EditorResult(false, List.of(Component.translatable("tarnishedrelics.editor.no_permission").getString())));
            return;
        }
        if (changes == null || changes.isEmpty()) {
            TRNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new TRNetwork.EditorResult(true, TRData.get().errors()));
            return;
        }
        MinecraftServer server = player.server;

        // 1. check: current files with the changes applied must parse without errors in the changed files
        List<String> errors = new ArrayList<>();
        Map<ResourceLocation, JsonElement> merged = new LinkedHashMap<>();
        TRData.get().rawJson().forEach((id, s) -> merged.put(id, JsonParser.parseString(s)));
        for (Map.Entry<ResourceLocation, String> e : changes.entrySet()) {
            ResourceLocation id = e.getKey();
            boolean special = isTag(id) || id.equals(CONFIG_FILE);
            if (e.getValue().isEmpty()) {
                merged.remove(id);
                continue;
            }
            try {
                JsonElement json = JsonParser.parseString(e.getValue());
                if (!json.isJsonObject()) throw new IllegalStateException("must be a JSON object");
                if (isTag(id)) checkTag(id, json.getAsJsonObject(), errors);
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
        if (!errors.isEmpty()) {
            TRNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new TRNetwork.EditorResult(false, errors));
            return;
        }

        // 2. write: config values to the toml, files to the chosen pack
        Path worldPack = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(PACK).toAbsolutePath().normalize();
        Path modpackPack = ModpackPack.folder().toAbsolutePath().normalize();
        boolean packFiles = changes.keySet().stream().anyMatch(id -> !id.equals(CONFIG_FILE));
        try {
            if (packFiles && !modpack) writePackMeta(worldPack);
            if (packFiles && modpack) ModpackPack.ensure();
            for (Map.Entry<ResourceLocation, String> e : changes.entrySet()) {
                ResourceLocation id = e.getKey();
                if (id.equals(CONFIG_FILE)) {
                    ConfigFile.write(JsonParser.parseString(e.getValue()).getAsJsonObject());
                    continue;
                }
                if (e.getValue().isEmpty()) {
                    remove(server, worldPack, modpackPack, id);
                    continue;
                }
                String json = isTag(id) ? tagToWrite(server, id, JsonParser.parseString(e.getValue()).getAsJsonObject()) : e.getValue();
                write(modpack ? modpackPack : worldPack, id, json);
                // saved for the whole modpack: the world's own copy would hide it, so it goes
                if (modpack) Files.deleteIfExists(file(worldPack, id));
            }
        } catch (IOException | RuntimeException ex) {
            TarnishedRelics.LOGGER.error("[Tarnished Relics] editor couldn't save", ex);
            TRNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new TRNetwork.EditorResult(false, List.of("Couldn't write the datapack: " + ex.getMessage())));
            return;
        }
        TarnishedRelics.LOGGER.info("[Tarnished Relics] {} saved {} file(s) in the editor ({})", player.getGameProfile().getName(), changes.size(),
                modpack ? "whole modpack: " + modpackPack : "this world");

        Runnable done = () -> {
            player.sendSystemMessage(Component.translatable(modpack ? "tarnishedrelics.editor.saved_chat_modpack" : "tarnishedrelics.editor.saved_chat", changes.size()));
            TRNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new TRNetwork.EditorResult(true, TRData.get().errors()));
            open(player, true);
        };
        if (!packFiles) {
            // only the config changed: nothing to reload
            done.run();
            return;
        }
        // 3. turn the world pack on (at the top), make sure the modpack pack is on, reload, then refresh the editor
        PackRepository repo = server.getPackRepository();
        repo.reload();
        List<String> selected = new ArrayList<>(repo.getSelectedIds());
        if (repo.getAvailableIds().contains(ModpackPack.ID) && !selected.contains(ModpackPack.ID)) selected.add(ModpackPack.ID);
        if (repo.getAvailableIds().contains(PACK_ID)) {
            selected.remove(PACK_ID);
            selected.add(PACK_ID);
        }
        server.reloadResources(selected).thenRunAsync(done, server).exceptionally(ex -> {
            // a failed reload (another pack broke it): the files are written, but say so instead of waiting forever
            TarnishedRelics.LOGGER.error("[Tarnished Relics] reload after the editor's save failed", ex);
            server.execute(() -> TRNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new TRNetwork.EditorResult(false, List.of("Saved, but the reload failed: " + ex.getMessage()))));
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
        com.flankrat.tarnishedrelics.data.Legendary legendary = legendaryId == null ? null : data.legendaries().get(legendaryId);
        List<ItemStack> relics = Roller.generateAll(player.getRandom(), rarity, category, legendary,
                com.flankrat.tarnishedrelics.api.RelicGeneratedEvent.Source.COMMAND, null);
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

    private static void checkTag(ResourceLocation id, JsonObject o, List<String> errors) {
        if (id.getPath().split("/").length < 3) errors.add(id + ": a tag needs a name like tags/entity_type/dragons");
        JsonElement values = o.get("values");
        if (values == null || !values.isJsonArray()) {
            errors.add(id + ": needs a \"values\" list");
            return;
        }
        for (JsonElement v : values.getAsJsonArray()) {
            String s = v.isJsonObject() && v.getAsJsonObject().has("id") ? v.getAsJsonObject().get("id").getAsString()
                    : v.isJsonPrimitive() ? v.getAsString() : null;
            if (s == null || ResourceLocation.tryParse(s.startsWith("#") ? s.substring(1) : s) == null) errors.add(id + ": bad value " + v);
        }
    }

    /**
     * What to write for an edited tag. Tags add up across packs, so if the new list still has everything the other
     * packs give, "replace": false keeps working when a mod adds to it later. If something from another pack was
     * taken out, only "replace": true can remove it.
     */
    private static String tagToWrite(MinecraftServer server, ResourceLocation id, JsonObject edited) {
        ResourceLocation loc = new ResourceLocation(id.getNamespace(), id.getPath() + ".json");
        JsonArray others = mergedValues(server.getResourceManager().getResourceStack(loc), Set.of(PACK_ID, ModpackPack.ID));
        JsonArray now = edited.getAsJsonArray("values");
        boolean keepsAll = true;
        for (JsonElement v : others) if (!now.contains(v)) keepsAll = false;
        JsonObject out = new JsonObject();
        out.addProperty("replace", !keepsAll);
        out.add("values", now);
        return out.toString();
    }

    // removed: delete the copy in use if it's one of ours, so the version underneath is used again
    private static void remove(MinecraftServer server, Path worldPack, Path modpackPack, ResourceLocation id) throws IOException {
        List<String> packs = isTag(id)
                ? server.getResourceManager().getResourceStack(new ResourceLocation(id.getNamespace(), id.getPath() + ".json")).stream().map(Resource::sourcePackId).toList()
                : TRDataLoader.origins().getOrDefault(id, List.of());
        String top = packs.isEmpty() ? "" : packs.get(packs.size() - 1);
        if (top.equals(PACK_ID)) Files.deleteIfExists(file(worldPack, id));
        else if (top.equals(ModpackPack.ID)) Files.deleteIfExists(file(modpackPack, id));
    }

    private static void writePackMeta(Path pack) throws IOException {
        Files.createDirectories(pack);
        Path meta = pack.resolve("pack.mcmeta");
        if (!Files.exists(meta)) {
            Files.writeString(meta, "{\n  \"pack\": {\n    \"pack_format\": 15,\n    \"description\": \"Tarnished Relics changes made in the in-game editor\"\n  }\n}\n");
        }
    }

    /** Where a file lives inside a pack: tags in data/ns/tags/, the rest in data/ns/tarnished_relics/. */
    private static Path file(Path pack, ResourceLocation id) throws IOException {
        Path data = pack.resolve("data").resolve(id.getNamespace());
        Path file = isTag(id) ? data.resolve(id.getPath() + ".json") : data.resolve(TRDataLoader.FOLDER).resolve(id.getPath() + ".json");
        if (!file.normalize().startsWith(pack)) throw new IOException("bad file id " + id);   // no ../ tricks
        return file;
    }

    private static void write(Path pack, ResourceLocation id, String json) throws IOException {
        Path file = file(pack, id);
        Files.createDirectories(file.getParent());
        // pretty-print so the saved pack is readable and diffs nicely
        String pretty = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(JsonParser.parseString(json));
        Files.writeString(file, pretty + "\n", StandardCharsets.UTF_8);
    }
}
