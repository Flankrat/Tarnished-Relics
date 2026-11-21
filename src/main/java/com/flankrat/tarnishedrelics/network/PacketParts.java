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

    /** Splits a map into parts of at most budget bytes each (an entry bigger than that gets a part to itself). */
    static List<Map<ResourceLocation, String>> split(Map<ResourceLocation, String> map, int budget) {
        List<Map<ResourceLocation, String>> parts = new ArrayList<>();
        Map<ResourceLocation, String> part = new LinkedHashMap<>();
        int used = 0;
        for (Map.Entry<ResourceLocation, String> e : map.entrySet()) {
            int n = size(e.getKey(), e.getValue());
            if (!part.isEmpty() && used + n > budget) {
                parts.add(part);
                part = new LinkedHashMap<>();
                used = 0;
            }
            part.put(e.getKey(), e.getValue());
            used += n;
        }
        parts.add(part);   // always at least one part, so an empty datapack still syncs
        return parts;
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
