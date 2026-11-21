package com.flankrat.tarnishedrelics.network;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Splitting data over several packets and putting it back together. A server-to-client packet can be at most 1 MB,
 * so the datapack files (and the editor's files) are sent in parts that each stay under a budget.
 */
final class PacketParts {

    private PacketParts() {
    }

    /** Roughly how many bytes an entry takes in a packet (UTF-8 text, plus length prefixes). */
    private static int size(ResourceLocation id, String value) {
        return id.toString().length() + value.getBytes(StandardCharsets.UTF_8).length + 10;
    }

    /**
     * Longest text one entry carries in a packet. FriendlyByteBuf refuses strings over the limit it's given
     * (262144 characters for these packets), and at up to 3 bytes a character this also keeps a piece under the
     * budget.
     */
    static final int MAX_PIECE_CHARS = 100_000;

    /**
     * Splits a map into parts of at most budget bytes each. A value too long for one packet (a huge datapack file)
     * is cut into pieces, each in a part of its own and in order; {@link #join} puts them back together.
     */
    static List<Map<ResourceLocation, String>> split(Map<ResourceLocation, String> map, int budget) {
        int pieceChars = Math.max(2, Math.min(MAX_PIECE_CHARS, (budget - 64) / 3));
        List<Map<ResourceLocation, String>> parts = new ArrayList<>();
        Map<ResourceLocation, String> part = new LinkedHashMap<>();
        int used = 0;
        for (Map.Entry<ResourceLocation, String> e : map.entrySet()) {
            ResourceLocation id = e.getKey();
            for (String piece : pieces(e.getValue(), pieceChars)) {
                int n = size(id, piece);
                // the same id can't be twice in one part, so each further piece starts a new part
                if (!part.isEmpty() && (used + n > budget || part.containsKey(id))) {
                    parts.add(part);
                    part = new LinkedHashMap<>();
                    used = 0;
                }
                part.put(id, piece);
                used += n;
            }
        }
        parts.add(part);   // always at least one part, so an empty datapack still syncs
        return parts;
    }

    /** The text cut into pieces of at most max characters (one piece if it fits), never between a surrogate pair. */
    static List<String> pieces(String text, int max) {
        if (text.length() <= max) return List.of(text);
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            int end = Math.min(text.length(), i + max);
            // don't split an emoji or other character made of two chars
            if (end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))) end--;
            out.add(text.substring(i, end));
            i = end;
        }
        return out;
    }

    /** Adds a part's entries to the whole; pieces of one value are joined in the order their parts came in. */
    static void join(Map<ResourceLocation, String> whole, Map<ResourceLocation, String> part) {
        part.forEach((id, piece) -> whole.merge(id, piece, String::concat));
    }

    /**
     * Puts split packets back together on the client. Keeps only the newest batch: if a new one starts (another
     * /reload) before the old one is complete, the old parts are dropped.
     */
    static final class Assembler {
        private int batch = -1;
        private final Map<Integer, Object[]> parts = new HashMap<>();

        /** Adds a part; returns all parts in order once every one is in, else null. */
        @Nullable
        synchronized List<Object[]> add(int batch, int index, int count, Object[] part) {
            if (batch != this.batch) {
                this.batch = batch;
                parts.clear();
            }
            parts.put(index, part);
            if (parts.size() < count) return null;
            List<Object[]> out = new ArrayList<>();
            for (int i = 0; i < count; i++) out.add(parts.get(i));
            parts.clear();
            this.batch = -1;
            return out;
        }
    }
}
