package com.flankrat.tarnishedrelics.data;

import com.flankrat.tarnishedrelics.BuiltInData;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.util.profiling.InactiveProfiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The real loader, reading a real datapack folder (the game's own pack and resource manager classes). */
class LoaderTest {

    static {
        BuiltInData.files();   // bootstrap
    }

    // runs the loader's two steps the way a /reload does
    private static final class Loader extends TRDataLoader {
        TRData load(Path pack) {
            var manager = new MultiPackResourceManager(PackType.SERVER_DATA, List.of(new PathPackResources("test", pack, false)));
            apply(prepare(manager, InactiveProfiler.INSTANCE), manager, InactiveProfiler.INSTANCE);
            manager.close();
            return TRData.get();
        }
    }

    private static void write(Path pack, String file, String text) throws IOException {
        Path p = pack.resolve("data/testpack/tarnished_relics/" + file);
        Files.createDirectories(p.getParent());
        Files.writeString(p, text);
    }

    @Test
    void goodFilesLoad(@TempDir Path pack) throws IOException {
        write(pack, "affixes/ok.json", "{\"kind\": \"prefix\", \"effects\": []}");
        TRData data = new Loader().load(pack);
        assertTrue(data.errors().isEmpty(), "errors: " + data.errors());
        assertTrue(data.affixes().containsKey(new net.minecraft.resources.ResourceLocation("testpack", "ok")));
    }

    // Bug (round 2): a file that isn't valid JSON was skipped by the game's JSON loader with only a log line, so the
    // editor and /tarnishedrelics errors said everything was fine while the file did nothing.
    @Test
    void malformedJsonIsReported(@TempDir Path pack) throws IOException {
        write(pack, "affixes/ok.json", "{\"kind\": \"prefix\", \"effects\": []}");
        write(pack, "affixes/broken.json", "{\"kind\": \"prefix\", \"effects\": [}");
        TRData data = new Loader().load(pack);
        assertTrue(data.errors().stream().anyMatch(e -> e.startsWith("testpack:affixes/broken")), "errors: " + data.errors());
        assertTrue(data.affixes().containsKey(new net.minecraft.resources.ResourceLocation("testpack", "ok")), "the good file still loads");
    }
}
