package com.flankrat.tarnishedrelics.client.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/** Small JSON helpers for the editor's form: numbers, ranges, lists and map keys. */
final class FormJson {

    private FormJson() {
    }

    static JsonArray asArray(@Nullable JsonElement e) {
        if (e == null || e.isJsonNull()) return new JsonArray();
        if (e.isJsonArray()) return e.getAsJsonArray();
        JsonArray a = new JsonArray();
        a.add(e);
        return a;
    }

    static String uniqueKey(JsonObject map, String k) {
        String out = k;
        for (int i = 2; map.has(out); i++) out = k + i;
        return out;
    }

    static void renameKey(JsonObject map, String from, String to) {
        if (from.equals(to)) return;
        Map<String, JsonElement> copy = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : map.entrySet()) copy.put(e.getKey().equals(from) ? to : e.getKey(), e.getValue());
        for (String k : new ArrayList<>(map.keySet())) map.remove(k);
        copy.forEach(map::add);
    }

    static double firstNumber(JsonElement v) {
        try {
            if (v.isJsonPrimitive()) return v.getAsDouble();
            if (v.isJsonArray() && !v.getAsJsonArray().isEmpty()) return v.getAsJsonArray().get(0).getAsDouble();
            if (v.isJsonObject()) for (JsonElement e : v.getAsJsonObject().asMap().values()) return firstNumber(e);
        } catch (RuntimeException ignored) {
            // not a number: start from 0
        }
        return 0;
    }

    static JsonObject newEffect(String type) {
        JsonObject e = new JsonObject();
        e.addProperty("type", type);
        for (String k : Schema.EFFECT_TYPES.getOrDefault(type, List.of())) e.add(k, Schema.EFFECT_FIELDS.get(k).def().get());
        return e;
    }

    static JsonArray pair(JsonPrimitive a, JsonPrimitive b) {
        JsonArray arr = new JsonArray();
        arr.add(a);
        arr.add(b);
        return arr;
    }

    static JsonArray rangeJson(double a, double b) {
        return pair(num(a), num(b));
    }

    static JsonPrimitive num(double d) {
        if (d == Math.rint(d) && Math.abs(d) < 1e9) return new JsonPrimitive((long) d);
        return new JsonPrimitive(Math.round(d * 10000) / 10000.0);
    }

    @Nullable
    static JsonPrimitive parseNumber(String s, boolean integer) {
        try {
            if (integer) return new JsonPrimitive(Integer.parseInt(s.trim()));
            return num(Double.parseDouble(s.trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String fmt(@Nullable JsonElement v) {
        if (v == null || !v.isJsonPrimitive()) return "0";
        try {
            double d = v.getAsDouble();
            if (d == Math.rint(d) && Math.abs(d) < 1e15) return Long.toString((long) d);
            return Double.toString(d);
        } catch (NumberFormatException e) {
            return v.getAsString();
        }
    }

    static int parseColor(String s) {
        try {
            String hex = s.startsWith("#") ? s.substring(1) : s;
            if (hex.length() != 6) return -1;
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * A list entry from its text. Most are plain strings, but tag files can have objects like
     * {"id": "somemod:thing", "required": false} (for optional mods): text that is a JSON object or list stays one.
     */
    static JsonElement listEntry(String text) {
        String t = text.trim();
        if (t.startsWith("{") || t.startsWith("[")) {
            try {
                JsonElement e = JsonParser.parseString(t);
                if (e.isJsonObject() || e.isJsonArray()) return e;
            } catch (RuntimeException ignored) {
                // not finished typing it yet: kept as text until it's valid JSON
            }
        }
        return new JsonPrimitive(text);
    }

    /** The id a list entry names: its text, or the "id" of an object entry. */
    static String entryId(JsonElement entry) {
        if (entry.isJsonObject() && entry.getAsJsonObject().get("id") instanceof JsonPrimitive id) return id.getAsString();
        return entry.isJsonPrimitive() ? entry.getAsString() : entry.toString();
    }
}
