package com.flankrat.tarnishedrelics.client.editor;

import com.flankrat.tarnishedrelics.network.TRNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/** Client side of the editor's packets. Only loaded on the client (the packets call it through DistExecutor). */
public final class EditorClient {

    private EditorClient() {
    }

    public static void open(Map<ResourceLocation, String> files, Map<ResourceLocation, String> origins, boolean refresh) {
        EditorModel model = new EditorModel(files, origins);
        Minecraft mc = Minecraft.getInstance();
        // a refresh after saving keeps the open screen (folder, file, scroll); otherwise start fresh
        if (refresh && mc.screen instanceof EditorScreen screen) screen.replaceModel(model);
        else if (refresh && mc.screen != null && mc.screen.getClass().getPackageName().equals(EditorScreen.class.getPackageName())
                && EditorScreen.last != null) EditorScreen.last.replaceModel(model);
        else mc.setScreen(new EditorScreen(model));
    }

    public static void result(boolean saved, List<String> errors) {
        if (EditorScreen.last != null) EditorScreen.last.saveResult(saved, errors);
    }

    /** Sends every change (one packet per file: client packets are limited to 32 KB), then asks the server to save. */
    public static void save(EditorModel model) {
        for (Map.Entry<ResourceLocation, String> e : model.changes().entrySet()) {
            TRNetwork.CHANNEL.sendToServer(new TRNetwork.EditorFile(e.getKey(), e.getValue()));
        }
        TRNetwork.CHANNEL.sendToServer(new TRNetwork.EditorCommit(true));
    }
}
