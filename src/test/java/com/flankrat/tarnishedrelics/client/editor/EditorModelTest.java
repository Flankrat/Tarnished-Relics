package com.flankrat.tarnishedrelics.client.editor;

import com.flankrat.tarnishedrelics.BuiltInData;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The editor's working copy, as the server sends it: the real built-in files, with where each comes from. What the
 * screen's buttons do to it, and what a save would send.
 */
class EditorModelTest {

    private static final String MOD = "mod:tarnishedrelics";
    private static final ResourceLocation SWORD = new ResourceLocation("tarnishedrelics", "categories/sword");
    private static final ResourceLocation MAGIC = new ResourceLocation("tarnishedrelics", "rarities/magic");

    /** The built-in files as the editor opens them; extra files with their packs ("pack1,pack2"). */
    private static EditorModel model(Map<ResourceLocation, String[]> extra) {
        Map<ResourceLocation, String> files = new LinkedHashMap<>();
        Map<ResourceLocation, String> origins = new LinkedHashMap<>();
        BuiltInData.files().forEach((id, json) -> {
            ResourceLocation fileId = new ResourceLocation(id.getNamespace(), id.getPath());
            files.put(fileId, json.toString());
            origins.put(fileId, MOD);
        });
        extra.forEach((id, v) -> {
            files.put(id, v[0]);
            origins.put(id, v[1]);
        });
        return new EditorModel(files, origins);
    }

    private static EditorModel model() {
        return model(Map.of());
    }

    @Test
    void opensWithNoChangesAndNoErrors() {
        EditorModel m = model();
        assertFalse(m.hasChanges());
        assertEquals(Map.of(), m.changes());
        assertEquals(List.of(), m.preview().errors());
        assertEquals(10, m.preview().legendaries().size());
    }

    @Test
    void editingAndUndoingTheEdit() {
        EditorModel m = model();
        JsonObject sword = m.get(SWORD);
        int weight = sword.has("weight") ? sword.get("weight").getAsInt() : 10;
        sword.addProperty("weight", weight + 5);
        m.edited();
        assertTrue(m.isChanged(SWORD));
        assertEquals(List.of(SWORD), List.copyOf(m.changes().keySet()));
        assertEquals(weight + 5, m.preview().categories().get(new ResourceLocation("tarnishedrelics", "sword")).weight());
        // typed back to what it was (the built-in file has no "weight": the default)
        if (BuiltInData.file("categories/sword").getAsJsonObject().has("weight")) sword.addProperty("weight", weight);
        else sword.remove("weight");
        m.edited();
        assertFalse(m.hasChanges());
    }

    @Test
    void revertThrowsAwayChanges() {
        EditorModel m = model();
        m.get(SWORD).addProperty("weight", 99);
        m.edited();
        m.revert(SWORD);
        assertFalse(m.hasChanges());
        assertEquals(BuiltInData.file("categories/sword"), m.get(SWORD));
    }

    // a built-in file can't be deleted (it's in the mod), only turned off, which the server then skips
    @Test
    void builtInFilesAreTurnedOffNotDeleted() {
        EditorModel m = model();
        assertFalse(m.editorOnly(SWORD));
        assertFalse(m.overridesAnother(SWORD));
        m.get(SWORD).addProperty("disabled", true);
        m.edited();
        assertTrue(m.isDisabled(SWORD));
        assertNull(m.preview().categories().get(new ResourceLocation("tarnishedrelics", "sword")));
        assertEquals(List.of(), m.preview().errors(), "turning a category off is not an error");
    }

    @Test
    void newFileThenDeletedIsNoChange() {
        EditorModel m = model();
        ResourceLocation id = new ResourceLocation("tarnishedrelics", "affixes/zz_new");
        m.put(id, Schema.template("affixes"));
        assertTrue(m.isNew(id) && m.hasChanges());
        m.remove(id);
        assertFalse(m.exists(id));
        assertFalse(m.isRemoved(id), "a file only made in this session just goes away");
        assertFalse(m.hasChanges());
    }

    // a file the editor saved over another pack's: removing it sends "" (the server deletes the editor's copy)
    @Test
    void editorCopyOverAnotherPack() {
        ResourceLocation id = new ResourceLocation("tarnishedrelics", "affixes/zz_over");
        EditorModel m = model(Map.of(id, new String[]{"{\"effects\": []}", "file/somepack,file/tarnishedrelics_editor"}));
        assertTrue(m.overridesAnother(id));
        assertEquals("file/tarnishedrelics_editor", m.origin(id));
        m.remove(id);
        assertTrue(m.isRemoved(id));
        assertEquals(Map.of(id, ""), m.changes());
    }

    @Test
    void excludeAndIncludeAMod() {
        EditorModel m = model();
        assertFalse(m.excludedEntry("minecraft:*"));
        m.setExcluded("minecraft:*", true);
        assertTrue(m.excludedEntry("minecraft:*"));
        assertNull(m.preview().categoryOf(net.minecraft.world.item.Items.IRON_SWORD), "excluded items have no category");
        assertEquals(List.of(EditorModel.EXCLUSIONS), List.copyOf(m.changes().keySet()));
        m.setExcluded("minecraft:*", false);
        assertFalse(m.excludedEntry("minecraft:*"));
        assertNotNull(m.preview().categoryOf(net.minecraft.world.item.Items.IRON_SWORD));
    }

    // Bug (round 4, found by clicking through the Mods tab): excluding a mod made the editor's exclusions file, and
    // including it again left that new, empty file behind, so Save stayed lit and would write an empty file
    @Test
    void excludeThenIncludeIsNoChange() {
        EditorModel m = model();
        m.setExcluded("somemod:*", true);
        m.setExcluded("somemod:*", false);
        assertFalse(m.hasChanges(), "changes: " + m.changes());
    }

    // an editor exclusions file that was already saved stays (with its other entries) when one entry goes
    @Test
    void savedEditorExclusionsFileStays() {
        EditorModel m = model(Map.of(EditorModel.EXCLUSIONS, new String[]{"{\"items\": [\"othermod:*\"]}", "file/tarnishedrelics_editor"}));
        m.setExcluded("somemod:*", true);
        m.setExcluded("somemod:*", false);
        assertFalse(m.hasChanges(), "changes: " + m.changes());
        m.setExcluded("othermod:*", false);
        assertEquals("{\"items\":[]}", m.changes().get(EditorModel.EXCLUSIONS));
    }

    // taking an entry out removes it from every exclusions file, also one that has it as a single string
    @Test
    void includeRemovesTheEntryEverywhere() {
        ResourceLocation other = new ResourceLocation("somepack", "exclusions/other");
        ResourceLocation single = new ResourceLocation("somepack", "exclusions/single");
        EditorModel m = model(Map.of(
                other, new String[]{"{\"items\": [\"minecraft:iron_sword\", \"minecraft:stick\"]}", "file/somepack"},
                single, new String[]{"{\"items\": \"minecraft:iron_sword\"}", "file/somepack"}));
        assertTrue(m.excludedEntry("minecraft:iron_sword"));
        m.setExcluded("minecraft:iron_sword", false);
        assertFalse(m.excludedEntry("minecraft:iron_sword"));
        assertEquals("[\"minecraft:stick\"]", m.get(other).get("items").toString());
        assertEquals(new JsonArray(), m.get(single).get("items"));
    }

    // a file the preview finds an error in: the screen lists it before sending anything
    @Test
    void errorsForTheChangedFile() {
        EditorModel m = model();
        m.put(MAGIC, JsonParser.parseString("{\"items\": [\"Minecraft:Iron_Sword\"]}").getAsJsonObject());
        assertEquals(1, m.errorsFor(MAGIC).size(), "errors: " + m.preview().errors());
        assertTrue(m.errorsFor(SWORD).isEmpty());
    }

    // Bug (round 4): "+ New" in the Legendary tab made a file with no "name", which a legendary needs, so it
    // showed an error and couldn't be saved until a name field was found and added. Every new file (as the screen
    // makes it) must be valid from the start.
    @Test
    void newFilesHaveNoErrors() {
        for (String folder : List.of("categories", "rarities", "affixes", "legendaries", "names", "levels", "exclusions")) {
            EditorModel m = model();
            ResourceLocation id = new ResourceLocation("tarnishedrelics", folder + "/zz_new_thing");
            m.put(id, Schema.newFile(folder, "zz_new_thing", null));
            assertEquals(List.of(), m.errorsFor(id), folder + ": " + m.get(id));
        }
    }

    // a copy keeps the original's fields, but is switched on and gets its own name
    @Test
    void copyOfATurnedOffFile() {
        JsonObject off = BuiltInData.file("affixes/vital").getAsJsonObject().deepCopy();
        off.addProperty("disabled", true);
        JsonObject copy = Schema.newFile("affixes", "vital_copy", off);
        assertFalse(copy.has("disabled"));
        assertEquals("Vital Copy", copy.get("name").getAsString());
        assertEquals(off.get("effects"), copy.get("effects"));
    }
}
