package com.flankrat.tarnishedrelics.network;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Splitting the datapack over packets and joining it back (round 1 fix: one huge file broke the sync). */
class PacketPartsTest {

    private static Map<ResourceLocation, String> joinAll(List<Map<ResourceLocation, String>> parts) {
        Map<ResourceLocation, String> whole = new LinkedHashMap<>();
        for (Map<ResourceLocation, String> p : parts) PacketParts.join(whole, p);
        return whole;
    }

    @Test
    void emptyMapIsStillOnePart() {
        assertEquals(1, PacketParts.split(Map.of(), 1000).size());
    }

    @Test
    void smallFilesRoundTrip() {
        Map<ResourceLocation, String> map = new LinkedHashMap<>();
        for (int i = 0; i < 50; i++) map.put(new ResourceLocation("t", "affixes/a" + i), "{\"n\": " + i + "}");
        List<Map<ResourceLocation, String>> parts = PacketParts.split(map, 200);
        assertTrue(parts.size() > 1);
        assertEquals(map, joinAll(parts));
    }

    @Test
    void hugeFileIsCutIntoPiecesThatFitAndJoinBack() {
        String big = "x".repeat(250_000) + "😀".repeat(50_000) + "end";
        Map<ResourceLocation, String> map = new LinkedHashMap<>();
        map.put(new ResourceLocation("t", "a"), "small");
        map.put(new ResourceLocation("t", "big"), big);
        map.put(new ResourceLocation("t", "z"), "after");
        int budget = 1_000_000;
        List<Map<ResourceLocation, String>> parts = PacketParts.split(map, budget);
        for (Map<ResourceLocation, String> p : parts) {
            int bytes = 0;
            for (var e : p.entrySet()) {
                assertTrue(e.getValue().length() <= PacketParts.MAX_PIECE_CHARS);
                bytes += e.getValue().getBytes(StandardCharsets.UTF_8).length;
            }
            assertTrue(bytes <= budget);
        }
        assertEquals(map, joinAll(parts));
    }

    @Test
    void piecesNeverSplitASurrogatePair() {
        for (String piece : PacketParts.pieces("a😀".repeat(1000), 7)) {
            assertFalse(Character.isHighSurrogate(piece.charAt(piece.length() - 1)), piece);
        }
    }

    @Test
    void assemblerDropsAnOlderBatch() {
        PacketParts.Assembler a = new PacketParts.Assembler();
        assertNull(a.add(1, 0, 2, new Object[]{"old0"}));
        assertNull(a.add(2, 0, 2, new Object[]{"new0"}));
        List<Object[]> done = a.add(2, 1, 2, new Object[]{"new1"});
        assertNotNull(done);
        assertEquals("new0", done.get(0)[0]);
        assertEquals("new1", done.get(1)[0]);
    }
}
