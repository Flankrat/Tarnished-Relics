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

    /**
     * Characters per packet when sending a file: a client packet can be at most 32767 bytes, and a character is up
     * to 3 bytes in UTF-8 (plus the file id).
     */
    private static final int PIECE_CHARS = 8000;

    /**
     * Sends every change, then asks the server to save. Files bigger than one client packet go in pieces (the
     * server joins them back together), so any size can be saved.
     */
    public static void save(EditorModel model, boolean modpack) {
        for (Map.Entry<ResourceLocation, String> e : model.changes().entrySet()) {
            String json = e.getValue();
            int i = 0;
            // at least one packet: "" (a removed file) is sent too
            do {
                int end = Math.min(json.length(), i + PIECE_CHARS);
                if (end < json.length() && Character.isHighSurrogate(json.charAt(end - 1))) end--;   // keep a 2-char symbol together
                TRNetwork.CHANNEL.sendToServer(new TRNetwork.EditorFile(e.getKey(), json.substring(i, end), i > 0));
                i = end;
            } while (i < json.length());
        }
        TRNetwork.CHANNEL.sendToServer(new TRNetwork.EditorCommit(true, modpack));
    }
}
