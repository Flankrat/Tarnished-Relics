package com.flankrat.tarnishedrelics.editor;

import com.flankrat.tarnishedrelics.BuiltInData;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Where the editor writes a file it was sent. Only the mod's own folders of its pack: data/<ns>/tarnished_relics/ and
 * data/<ns>/tags/. Ids may contain "..", and only leaving the pack was refused, so a tag id like
 * "minecraft:tags/../loot_tables/chests/simple_dungeon" was written as that chest's loot table (an empty one).
 */
class EditorFilePathTest {

    @TempDir
    Path pack;

    @BeforeAll
    static void bootstrap() {
        BuiltInData.files();
    }

    @ParameterizedTest
    @ValueSource(strings = {"minecraft:tags/../loot_tables/chests/simple_dungeon", "minecraft:tags/../../other/data/x",
            "tarnishedrelics:affixes/../../recipes/stick", "tarnishedrelics:../tags/items/x"})
    void outsideTheModsFoldersIsRefused(String id) {
        assertThrows(IOException.class, () -> EditorFiles.file(pack, new ResourceLocation(id)));
    }

    // the save is refused before anything is written
    @ParameterizedTest
    @ValueSource(strings = {"minecraft:tags/../loot_tables/chests/simple_dungeon", "tarnishedrelics:affixes/../../recipes/stick"})
    void notWritable(String id) {
        assertFalse(EditorFiles.isWritable(new ResourceLocation(id)));
    }

    @Test
    void normalFilesAndTags() throws IOException {
        assertEquals(pack.resolve("data/tarnishedrelics/tarnished_relics/affixes/keen.json"),
                EditorFiles.file(pack, new ResourceLocation("tarnishedrelics:affixes/keen")));
        assertEquals(pack.resolve("data/tarnishedrelics/tags/entity_type/undead.json"),
                EditorFiles.file(pack, new ResourceLocation("tarnishedrelics:tags/entity_type/undead")));
    }
}
