package com.flankrat.tarnishedrelics.editor;

import com.flankrat.tarnishedrelics.TarnishedRelics;
import com.flankrat.tarnishedrelics.data.TRDataLoader;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Where the editor's saves go on disk, inside the world's or the modpack's pack. Split out of {@link EditorServer}. */
final class EditorFiles {

    private EditorFiles() {
    }

    // removed: delete the copy in use if it's one of ours, so the version underneath is used again
    static void remove(MinecraftServer server, Path worldPack, Path modpackPack, ResourceLocation id) throws IOException {
        List<String> packs = EditorServer.isTag(id)
                ? server.getResourceManager().getResourceStack(new ResourceLocation(id.getNamespace(), id.getPath() + ".json")).stream().map(Resource::sourcePackId).toList()
                : TRDataLoader.origins().getOrDefault(id, List.of());
        String top = packs.isEmpty() ? "" : packs.get(packs.size() - 1);
        if (top.equals(EditorServer.PACK_ID)) Files.deleteIfExists(file(worldPack, id));
        else if (top.equals(ModpackPack.ID)) Files.deleteIfExists(file(modpackPack, id));
    }

    static void writePackMeta(Path pack) throws IOException {
        Files.createDirectories(pack);
        Path meta = pack.resolve("pack.mcmeta");
        if (!Files.exists(meta)) {
            Files.writeString(meta, "{\n  \"pack\": {\n    \"pack_format\": 15,\n    \"description\": \"Tarnished Relics changes made in the in-game editor\"\n  }\n}\n");
        }
    }

    /**
     * Where a file lives inside a pack: tags in data/ns/tags/, the rest in data/ns/tarnished_relics/. Ids can contain
     * "..": the file has to stay in its own folder, not just in the pack, or "minecraft:tags/../loot_tables/chests/x"
     * wrote a chest's loot table. A namespace may be "." or ".." too, which pointed data/ns/ at the pack's own folder.
     * Tags are only the mod's own (the editor shows nothing else): another mod's or vanilla's list could otherwise be
     * replaced.
     */
    static Path file(Path pack, ResourceLocation id) throws IOException {
        String ns = id.getNamespace();
        if (ns.equals(".") || ns.equals("..")) throw new IOException("bad file id " + id);
        if (EditorServer.isTag(id) && !ns.equals(TarnishedRelics.MODID)) throw new IOException("not one of the mod's tags: " + id);
        Path data = pack.resolve("data").resolve(ns);
        Path root = EditorServer.isTag(id) ? data.resolve("tags") : data.resolve(TRDataLoader.FOLDER);
        Path file = EditorServer.isTag(id) ? data.resolve(id.getPath() + ".json") : root.resolve(id.getPath() + ".json");
        if (!file.normalize().startsWith(root.normalize()) || !root.normalize().startsWith(pack.resolve("data").normalize()))
            throw new IOException("bad file id " + id);
        return file;
    }

    /** True if the id has a place in a pack (see file). */
    static boolean isWritable(ResourceLocation id) {
        try {
            file(Path.of("pack"), id);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    static void write(Path pack, ResourceLocation id, String json) throws IOException {
        Path file = file(pack, id);
        Files.createDirectories(file.getParent());
        // pretty-print so the saved pack is readable and diffs nicely
        String pretty = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(JsonParser.parseString(json));
        Files.writeString(file, pretty + "\n", StandardCharsets.UTF_8);
    }
}
