package com.flankrat.tarnishedrelics.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Map;

/**
 * Reads every JSON file under {@code data/<namespace>/tarnished_relics/} on world load and /reload.
 * Registered from AddReloadListenerEvent.
 */
public class TRDataLoader extends SimpleJsonResourceReloadListener {

    public TRDataLoader() {
        super(new Gson(), "tarnished_relics");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        TRData.load(files);
    }
}
