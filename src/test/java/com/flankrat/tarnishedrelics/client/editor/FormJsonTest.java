package com.flankrat.tarnishedrelics.client.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The editor form's JSON helpers: what a typed number, range, key or list entry is saved as. */
class FormJsonTest {

    // Bug (round 3): typed numbers were rounded to 4 decimals, so a loot chance of 0.00002 (1 in 50,000) was saved
    // as 0 and relics stopped dropping, and 0.12345 became 0.1235.
    @Test
    void smallAndPreciseNumbersAreKept() {
        assertEquals(0.00002, FormJson.parseNumber("0.00002", false).getAsDouble(), 0);
        assertEquals(0.12345, FormJson.parseNumber("0.12345", false).getAsDouble(), 0);
        assertEquals(-0.00005, FormJson.parseNumber("-0.00005", false).getAsDouble(), 0);
    }

    // what the rounding was for: no float noise (0.1 + 0.2 = 0.30000000000000004) and whole numbers stay whole
    @Test
    void floatNoiseIsCleanedAndWholeNumbersStayWhole() {
        assertEquals("0.3", FormJson.num(0.1 + 0.2).toString());
        assertEquals("3", FormJson.num(3.0).toString());
        assertEquals("[0.1,0.2]", FormJson.rangeJson(0.1, 0.2).toString());
        assertEquals("1.5", FormJson.parseNumber(" 1.5 ", false).toString());
    }

    // a box that parses to something else than what's typed shows red instead of saving that something else
    // (NaN was saved as 0, and 1e400 as 922337203685477.6)
    @Test
    void notFiniteNumbersAreNull() {
        assertNull(FormJson.parseNumber("NaN", false));
        assertNull(FormJson.parseNumber("1e400", false));
        assertNull(FormJson.parseNumber("-Infinity", false));
    }

    @Test
    void badNumbersAreNull() {
        assertNull(FormJson.parseNumber("abc", false));
        assertNull(FormJson.parseNumber("", false));
        assertNull(FormJson.parseNumber("3.5", true));
        assertEquals(3, FormJson.parseNumber("3", true).getAsInt());
    }

    @Test
    void fmtShowsWholeNumbersWithoutDecimals() {
        assertEquals("3", FormJson.fmt(new JsonPrimitive(3.0)));
        assertEquals("0.25", FormJson.fmt(new JsonPrimitive(0.25)));
        assertEquals("0", FormJson.fmt(null));
        assertEquals("0", FormJson.fmt(new JsonArray()));
    }

    @Test
    void renameKeyKeepsTheOrder() {
        JsonObject o = JsonParser.parseString("{\"a\": 1, \"b\": 2, \"c\": 3}").getAsJsonObject();
        FormJson.renameKey(o, "b", "x");
        assertEquals(List.of("a", "x", "c"), List.copyOf(o.keySet()));
        assertEquals(2, o.get("x").getAsInt());
    }

    @Test
    void uniqueKeyAddsANumber() {
        JsonObject o = JsonParser.parseString("{\"magic\": 1, \"magic2\": 2}").getAsJsonObject();
        assertEquals("magic3", FormJson.uniqueKey(o, "magic"));
        assertEquals("rare", FormJson.uniqueKey(o, "rare"));
    }

    @Test
    void firstNumberOfAnyValueShape() {
        assertEquals(2, FormJson.firstNumber(new JsonPrimitive(2)), 0);
        assertEquals(1, FormJson.firstNumber(JsonParser.parseString("[1, 4]")), 0);
        assertEquals(5, FormJson.firstNumber(JsonParser.parseString("{\"magic\": [5, 6]}")), 0);
        assertEquals(0, FormJson.firstNumber(new JsonPrimitive("x")), 0);
    }

    @Test
    void colors() {
        assertEquals(0x5555FF, FormJson.parseColor("#5555FF"));
        assertEquals(0x5555FF, FormJson.parseColor("5555ff"));
        assertEquals(-1, FormJson.parseColor("#55F"));
        assertEquals(-1, FormJson.parseColor("#GGGGGG"));
    }

    // tag files can have {"id": ..., "required": false}; typing one keeps it an object once it's valid JSON
    @Test
    void listEntries() {
        assertEquals(new JsonPrimitive("minecraft:zombie"), FormJson.listEntry("minecraft:zombie"));
        assertTrue(FormJson.listEntry("{\"id\": \"somemod:x\", \"required\": false}").isJsonObject());
        assertEquals(new JsonPrimitive("{\"id\": "), FormJson.listEntry("{\"id\": "));
        assertEquals("somemod:x", FormJson.entryId(FormJson.listEntry("{\"id\": \"somemod:x\", \"required\": false}")));
        assertEquals("minecraft:zombie", FormJson.entryId(new JsonPrimitive("minecraft:zombie")));
    }
}
