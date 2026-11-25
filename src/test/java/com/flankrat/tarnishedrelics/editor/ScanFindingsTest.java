package com.flankrat.tarnishedrelics.editor;

import com.flankrat.tarnishedrelics.BuiltInData;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Findings of the Nov 24 2026 bug scan that plain JUnit can reproduce (the in-world ones are in ScanFindingsGameTests). */
class ScanFindingsTest {

    @TempDir
    Path pack;

    @BeforeAll
    static void bootstrap() {
        BuiltInData.files();
    }

    // #1: an empty value means "remove this file", but the config isn't a file: "" passed the check and then
    // crashed the write, after the save's other files were already on disk
    @Test
    void emptyConfigIsRefusedByTheCheck() {
        List<String> errors = EditorServer.check(Map.of(EditorServer.CONFIG_FILE, ""));
        assertFalse(errors.isEmpty(), "an empty config passed the check");
    }

    // #2: the reload worked, but something after it (refreshing the editor) threw: that must not be reported as a
    // failed reload
    @Test
    void anErrorAfterTheReloadIsNotAFailedReload() {
        AtomicReference<Throwable> failed = new AtomicReference<>();
        CompletableFuture<Void> f = EditorServer.afterReload(CompletableFuture.completedFuture(null), Runnable::run,
                () -> {
                    throw new IllegalStateException("refresh broke");
                }, failed::set);
        f.handle((v, ex) -> null).join();
        assertNull(failed.get(), "an error after a successful reload was reported as the reload failing");
    }

    @Test
    void aFailedReloadIsReported() {
        AtomicReference<Throwable> failed = new AtomicReference<>();
        EditorServer.afterReload(CompletableFuture.failedFuture(new IllegalStateException("bad pack")), Runnable::run,
                () -> fail("done ran after a failed reload"), failed::set).handle((v, ex) -> null).join();
        assertNotNull(failed.get());
    }

    // #3: a config from an editor that doesn't know luckRollBonus (an older client) got "missing or wrong value
    // (Cannot invoke ... because ... is null)" instead of saying which value is missing
    @Test
    void aMissingConfigValueIsNamed() {
        // a 0.6.0 editor's config: every value but luckRollBonus
        JsonObject o = JsonParser.parseString("{\"enabled\": true, \"chance\": 0.1, \"lootTypes\": [\"chest\"], "
                + "\"entityNeedsPlayerKill\": true, \"excludeTables\": [], \"confirmSeconds\": 10, "
                + "\"showRarityColorUnidentified\": true}").getAsJsonObject();
        List<String> errors = ConfigFile.check(o);
        assertTrue(errors.stream().anyMatch(e -> e.contains("luckRollBonus")), "the error doesn't name the missing value: " + errors);
    }

    // #3: the editor's config format changed in 0.7.0 (luckRollBonus), so the network version must have changed
    // too, or a 0.6.0 client can join and its editor saves are refused
    @Test
    void networkVersionWasRaisedForTheNewConfigFormat() throws Exception {
        // read from the source: loading TRNetwork needs Forge's networking, which unit tests don't start
        String src = java.nio.file.Files.readString(Path.of("src/main/java/com/flankrat/tarnishedrelics/network/TRNetwork.java"));
        var m = java.util.regex.Pattern.compile("String VERSION = \"(\\d+)\"").matcher(src);
        assertTrue(m.find(), "no VERSION in TRNetwork");
        assertNotEquals("6", m.group(1), "still the 0.6.0 network version");
    }

    // #5: a namespace may be "..", which resolves to the pack's own folder: the file landed outside data/
    @Test
    void dotDotNamespaceStaysInData() {
        assertThrows(IOException.class, () -> EditorFiles.file(pack, new ResourceLocation("..", "tags/x")));
        assertThrows(IOException.class, () -> EditorFiles.file(pack, new ResourceLocation(".", "affixes/x")));
        assertFalse(EditorFiles.isWritable(new ResourceLocation("..", "tags/x")));
    }

    // #4: the editor only works on the mod's own tags: another namespace's tag (vanilla's pickaxe-mineable list
    // with "replace": true) could be written
    @Test
    void otherNamespacesTagsAreRefused() {
        assertFalse(EditorFiles.isWritable(new ResourceLocation("minecraft", "tags/blocks/mineable/pickaxe")));
        assertTrue(EditorFiles.isWritable(new ResourceLocation("tarnishedrelics", "tags/entity_type/undead")));
    }
}
