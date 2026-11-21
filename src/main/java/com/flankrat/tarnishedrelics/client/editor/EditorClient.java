package com.flankrat.tarnishedrelics.client.editor;

import com.flankrat.tarnishedrelics.network.TRNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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

    /** Largest file that fits in one client packet (32767 bytes, minus the id and length). */
    private static final int MAX_FILE_BYTES = 32000;

    /**
     * Sends every change (one packet per file: client packets are limited to 32 KB), then asks the server to save.
     * Returns the files too big to send (nothing is sent then).
     */
    public static List<String> save(EditorModel model, boolean modpack) {
        List<String> tooBig = new ArrayList<>();
        for (Map.Entry<ResourceLocation, String> e : model.changes().entrySet()) {
            int bytes = e.getValue().getBytes(StandardCharsets.UTF_8).length;
            if (bytes > MAX_FILE_BYTES) tooBig.add(e.getKey() + " is " + bytes / 1024 + " KB; files up to 31 KB can be saved from the editor (split it into two files)");
        }
        if (!tooBig.isEmpty()) return tooBig;
        for (Map.Entry<ResourceLocation, String> e : model.changes().entrySet()) {
            TRNetwork.CHANNEL.sendToServer(new TRNetwork.EditorFile(e.getKey(), e.getValue()));
        }
        TRNetwork.CHANNEL.sendToServer(new TRNetwork.EditorCommit(true, modpack));
        return tooBig;
    }
}
