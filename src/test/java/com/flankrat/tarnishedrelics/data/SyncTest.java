package com.flankrat.tarnishedrelics.data;

import com.flankrat.tarnishedrelics.BuiltInData;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The client rebuilding the data from the server's sync. */
class SyncTest {

    // Bug (round 3): in single player the client and the integrated server share the current data. The client
    // rebuilt it from the synced JSON, which only has the files that loaded, so the server's "not valid JSON" errors
    // vanished from /tarnishedrelics errors and the editor right after joining or /reload.
    @Test
    void syncOfTheSameDataKeepsTheServersErrors() {
        TRData.load(BuiltInData.files(), List.of("testpack:affixes/broken: not valid JSON (line 3)"));
        TRData.loadFromSync(new LinkedHashMap<>(TRData.get().rawJson()));
        assertTrue(TRData.get().errors().contains("testpack:affixes/broken: not valid JSON (line 3)"), "errors: " + TRData.get().errors());
    }

    // a dedicated server's data (different from what the client has) is loaded
    @Test
    void syncOfOtherDataLoadsIt() {
        TRData.load(java.util.Map.of());
        TRData.loadFromSync(new LinkedHashMap<>(TRData.parseOnly(BuiltInData.files()).rawJson()));
        assertEquals(10, TRData.get().legendaries().size());
    }
}
