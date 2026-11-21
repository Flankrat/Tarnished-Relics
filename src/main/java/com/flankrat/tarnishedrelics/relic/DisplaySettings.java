package com.flankrat.tarnishedrelics.relic;

import com.flankrat.tarnishedrelics.TRConfig;

import javax.annotation.Nullable;

/**
 * Display options that belong to the server, not each player's own config. The server sends them when a player
 * joins and whenever they change in the editor, so every player sees what the server set; the game's own config
 * is used until then (and in a world without the server's value, e.g. the main menu).
 */
public final class DisplaySettings {

    // the server's value, while connected to one (null: none received)
    @Nullable
    private static volatile Boolean serverShowRarityColor;

    private DisplaySettings() {
    }

    /** Unidentified relics show their rarity's color. */
    public static boolean showRarityColor() {
        Boolean server = serverShowRarityColor;
        return server != null ? server : TRConfig.SHOW_RARITY_COLOR.get();
    }

    /** Client: the values the server sent. */
    public static void fromServer(boolean showRarityColor) {
        serverShowRarityColor = showRarityColor;
    }

    /** Client: left the server, back to the game's own config. */
    public static void clear() {
        serverShowRarityColor = null;
    }
}
