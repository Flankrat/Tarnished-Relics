package com.flankrat.tarnishedrelics.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads every JSON file under {@code data/<namespace>/tarnished_relics/} on world load and /reload.
 * Registered from AddReloadListenerEvent. Also remembers which packs supply each file, for the editor.
 * <p>
 * Reads the files itself instead of using the game's SimpleJsonResourceReloadListener: that one skips a file that
 * isn't valid JSON with only a log line, so /tarnishedrelics errors and the editor said all was fine while the file
 * did nothing. Here such a file is a load error like any other bad file.
 */
public class TRDataLoader extends SimplePreparableReloadListener<TRDataLoader.Scan> {

    public static final String FOLDER = "tarnished_relics";
    private static final Gson GSON = new Gson();

    /** File id ("ns:folder/name") -> ids of every pack that has it, lowest first (the last one wins). Server only. */
    private static volatile Map<ResourceLocation, List<String>> origins = Map.of();

    /** The files that read as JSON (by "folder/name" id) and the ones that didn't. */
    public record Scan(Map<ResourceLocation, JsonElement> files, List<String> errors) {
    }

    /** Reads every file the way the game's loader does (same ids, the top pack's copy), keeping the bad ones' errors. */
    public static Scan scan(ResourceManager manager) {
        FileToIdConverter converter = FileToIdConverter.json(FOLDER);
        Map<ResourceLocation, JsonElement> files = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Resource> e : converter.listMatchingResources(manager).entrySet()) {
            ResourceLocation id = converter.fileToId(e.getKey());
            try (Reader reader = e.getValue().openAsReader()) {
                JsonElement json = GsonHelper.fromJson(GSON, reader, JsonElement.class);
                if (json == null) errors.add(id + ": empty file");
                else files.put(id, json);
            } catch (IOException | RuntimeException ex) {
                // JsonParseException for bad JSON; the message says where ("line 3 column 7")
                errors.add(id + ": not valid JSON (" + (ex instanceof JsonParseException && ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage()) + ")");
            }
        }
        return new Scan(files, errors);
    }

    @Override
    protected Scan prepare(ResourceManager manager, ProfilerFiller profiler) {
        return scan(manager);
    }

    @Override
    protected void apply(Scan scan, ResourceManager manager, ProfilerFiller profiler) {
        TRData.load(scan.files(), scan.errors());
        Map<ResourceLocation, List<String>> map = new LinkedHashMap<>();
        for (ResourceLocation id : scan.files().keySet()) {
            ResourceLocation path = new ResourceLocation(id.getNamespace(), FOLDER + "/" + id.getPath() + ".json");
            map.put(id, manager.getResourceStack(path).stream().map(Resource::sourcePackId).toList());
        }
        origins = map;
    }

    public static Map<ResourceLocation, List<String>> origins() {
        return origins;
    }
}
