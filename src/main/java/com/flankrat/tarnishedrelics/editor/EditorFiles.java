package com.flankrat.tarnishedrelics.editor;

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

    /** Where a file lives inside a pack: tags in data/ns/tags/, the rest in data/ns/tarnished_relics/. */
    static Path file(Path pack, ResourceLocation id) throws IOException {
        Path data = pack.resolve("data").resolve(id.getNamespace());
        Path file = EditorServer.isTag(id) ? data.resolve(id.getPath() + ".json") : data.resolve(TRDataLoader.FOLDER).resolve(id.getPath() + ".json");
        if (!file.normalize().startsWith(pack)) throw new IOException("bad file id " + id);   // no ../ tricks
        return file;
    }

    static void write(Path pack, ResourceLocation id, String json) throws IOException {
        Path file = file(pack, id);
        Files.createDirectories(file.getParent());
        // pretty-print so the saved pack is readable and diffs nicely
        String pretty = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(JsonParser.parseString(json));
        Files.writeString(file, pretty + "\n", StandardCharsets.UTF_8);
    }
}
