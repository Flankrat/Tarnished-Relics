package com.flankrat.tarnishedrelics.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads every JSON file under {@code data/<namespace>/tarnished_relics/} on world load and /reload.
 * Registered from AddReloadListenerEvent. Also remembers which packs supply each file, for the editor.
 */
public class TRDataLoader extends SimpleJsonResourceReloadListener {

    public static final String FOLDER = "tarnished_relics";

    /** File id ("ns:folder/name") -> ids of every pack that has it, lowest first (the last one wins). Server only. */
    private static volatile Map<ResourceLocation, List<String>> origins = Map.of();

    public TRDataLoader() {
        super(new Gson(), FOLDER);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        TRData.load(files);
        Map<ResourceLocation, List<String>> map = new LinkedHashMap<>();
        for (ResourceLocation id : files.keySet()) {
            ResourceLocation path = new ResourceLocation(id.getNamespace(), FOLDER + "/" + id.getPath() + ".json");
            map.put(id, manager.getResourceStack(path).stream().map(Resource::sourcePackId).toList());
        }
        origins = map;
    }

    public static Map<ResourceLocation, List<String>> origins() {
        return origins;
    }
}
