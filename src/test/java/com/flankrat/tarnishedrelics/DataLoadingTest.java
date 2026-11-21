package com.flankrat.tarnishedrelics;

import com.flankrat.tarnishedrelics.data.TRData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The shipped datapack files load cleanly. */
class DataLoadingTest {

    @Test
    void builtInFilesLoadWithoutErrors() {
        TRData data = BuiltInData.data();
        assertEquals(java.util.List.of(), data.errors());
        assertTrue(BuiltInData.files().size() >= 80, "found " + BuiltInData.files().size() + " files");
        assertEquals(10, data.legendaries().size());
        assertTrue(data.rarities().size() >= 4);
    }
}
