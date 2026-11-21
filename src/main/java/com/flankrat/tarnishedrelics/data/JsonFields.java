package com.flankrat.tarnishedrelics.data;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/** Reading optional fields of a datapack file: the value if the key is there, otherwise the fallback. */
final class JsonFields {

    private JsonFields() {
    }

    static int intOr(JsonObject o, String key, int fallback) {
        return o.has(key) ? o.get(key).getAsInt() : fallback;
    }

    static double doubleOr(JsonObject o, String key, double fallback) {
        return o.has(key) ? o.get(key).getAsDouble() : fallback;
    }

    static boolean boolOr(JsonObject o, String key, boolean fallback) {
        return o.has(key) ? o.get(key).getAsBoolean() : fallback;
    }

    @Nullable
    static String stringOr(JsonObject o, String key, @Nullable String fallback) {
        return o.has(key) ? o.get(key).getAsString() : fallback;
    }

    /** An id field, or null when it's missing. */
    @Nullable
    static ResourceLocation idOrNull(JsonObject o, String key) {
        return o.has(key) ? new ResourceLocation(o.get(key).getAsString()) : null;
    }
}
