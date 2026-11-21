package com.flankrat.tarnishedrelics;

import com.flankrat.tarnishedrelics.data.TRData;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The mod's real built-in datapack files ({@code data/tarnishedrelics/tarnished_relics/}), read from the test
 * classpath, so tests check the shipped data instead of made-up objects.
 */
public final class BuiltInData {

    private static Map<ResourceLocation, JsonElement> files;

    static {
        // registry keys (Registries.*) need Minecraft's bootstrap, even without a game running
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private BuiltInData() {
    }

    /** Every built-in file, by "folder/name" id (as the game's loader names them). */
    public static synchronized Map<ResourceLocation, JsonElement> files() {
        if (files == null) {
            try {
                Path root = Path.of(BuiltInData.class.getClassLoader().getResource("data/tarnishedrelics/tarnished_relics").toURI());
                Map<ResourceLocation, JsonElement> out = new LinkedHashMap<>();
                try (Stream<Path> walk = Files.walk(root)) {
                    walk.filter(p -> p.toString().endsWith(".json")).sorted().forEach(p -> {
                        String rel = root.relativize(p).toString().replace('\\', '/');
                        try {
                            out.put(new ResourceLocation("tarnishedrelics", rel.substring(0, rel.length() - 5)), JsonParser.parseString(Files.readString(p)));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                }
                files = out;
            } catch (URISyntaxException | IOException e) {
                throw new RuntimeException(e);
            }
        }
        return files;
    }

    /** The built-in files parsed into a data set (not made current). */
    public static TRData data() {
        return TRData.parseOnly(files());
    }

    /** One built-in file's JSON. */
    public static JsonElement file(String id) {
        JsonElement e = files().get(new ResourceLocation("tarnishedrelics", id));
        if (e == null) throw new IllegalArgumentException("no built-in file " + id);
        return e;
    }
}
