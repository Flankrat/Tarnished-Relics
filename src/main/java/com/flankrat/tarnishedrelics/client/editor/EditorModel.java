package com.flankrat.tarnishedrelics.client.editor;

import com.flankrat.tarnishedrelics.data.TRData;
import com.flankrat.tarnishedrelics.editor.EditorServer;
import com.flankrat.tarnishedrelics.editor.ModpackPack;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * The editor's working copy of every Tarnished Relics file. File ids are "namespace:folder/name", the same ids the
 * data loader uses. Edits change the JSON objects in place; a file is "changed" when its JSON no longer matches
 * what the server sent. {@link #preview()} parses the working copy with the same code the server uses, so the
 * editor can show errors and item counts before anything is saved.
 */
public final class EditorModel {

    /** Namespace new files go in. */
    public static final String NAMESPACE = "tarnishedrelics";
    /** The editor's own exclusions file (the Mods tab and item toggles write here). */
    public static final ResourceLocation EXCLUSIONS = new ResourceLocation(NAMESPACE, "exclusions/editor");

    private final Map<ResourceLocation, String> original = new LinkedHashMap<>();
    private final Map<ResourceLocation, JsonObject> files = new LinkedHashMap<>();
    private final Map<ResourceLocation, String> origins = new LinkedHashMap<>();
    private final Set<ResourceLocation> removed = new LinkedHashSet<>();
    private TRData preview;

    public EditorModel(Map<ResourceLocation, String> files, Map<ResourceLocation, String> origins) {
        files.forEach((id, s) -> {
            original.put(id, normalize(s));
            this.files.put(id, JsonParser.parseString(s).getAsJsonObject());
        });
        this.origins.putAll(origins);
    }

    private static String normalize(String json) {
        return JsonParser.parseString(json).toString();
    }

    public static String folderOf(ResourceLocation id) {
        int slash = id.getPath().indexOf('/');
        return slash < 0 ? "" : id.getPath().substring(0, slash);
    }

    /** "tarnishedrelics:sword" for "tarnishedrelics:categories/sword": the id other files use to point at it. */
    public static ResourceLocation shortId(ResourceLocation id) {
        int slash = id.getPath().indexOf('/');
        return new ResourceLocation(id.getNamespace(), id.getPath().substring(slash + 1));
    }

    public static ResourceLocation fileId(String folder, ResourceLocation shortId) {
        return new ResourceLocation(shortId.getNamespace(), folder + "/" + shortId.getPath());
    }

    public List<ResourceLocation> filesIn(String folder) {
        List<ResourceLocation> out = new ArrayList<>();
        for (ResourceLocation id : files.keySet()) if (folderOf(id).equals(folder)) out.add(id);
        out.sort(Comparator.comparing(ResourceLocation::toString));
        return out;
    }

    @Nullable
    public JsonObject get(ResourceLocation id) {
        return files.get(id);
    }

    public boolean exists(ResourceLocation id) {
        return files.containsKey(id);
    }

    /** Packs that have the file, lowest first (the last is the copy in use). Empty for new files. */
    public List<String> origins(ResourceLocation id) {
        String s = origins.getOrDefault(id, "");
        return s.isEmpty() ? List.of() : List.of(s.split(","));
    }

    /** The pack whose copy is in use ("" for new files). */
    public String origin(ResourceLocation id) {
        List<String> o = origins(id);
        return o.isEmpty() ? "" : o.get(o.size() - 1);
    }

    /** The packs the editor saves to: the world's editor pack and the modpack folder. */
    public static boolean isEditorPack(String packId) {
        return packId.equals(EditorServer.PACK_ID) || packId.equals(ModpackPack.ID);
    }

    /** True if the copy in use is one the editor saved and some other pack has the file too. */
    public boolean overridesAnother(ResourceLocation id) {
        List<String> o = origins(id);
        return o.size() > 1 && isEditorPack(o.get(o.size() - 1));
    }

    /** True if only the editor's packs have the file (or it's new). */
    public boolean editorOnly(ResourceLocation id) {
        List<String> o = origins(id);
        return o.isEmpty() || o.stream().allMatch(EditorModel::isEditorPack);
    }

    /** Tags and the config aren't Tarnished Relics data files (the preview skips them). */
    public static boolean isSpecial(ResourceLocation id) {
        String folder = folderOf(id);
        return folder.equals("tags") || folder.equals("config");
    }

    public boolean isRemoved(ResourceLocation id) {
        return removed.contains(id);
    }

    public boolean isChanged(ResourceLocation id) {
        JsonObject o = files.get(id);
        return o == null ? original.containsKey(id) : !o.toString().equals(original.get(id));
    }

    public boolean isNew(ResourceLocation id) {
        return files.containsKey(id) && !original.containsKey(id);
    }

    public boolean isDisabled(ResourceLocation id) {
        JsonObject o = files.get(id);
        return o != null && o.has("disabled") && o.get("disabled").isJsonPrimitive() && o.get("disabled").getAsBoolean();
    }

    public boolean hasChanges() {
        if (!removed.isEmpty()) return true;
        for (ResourceLocation id : files.keySet()) if (isChanged(id)) return true;
        return false;
    }

    /** Every changed file: id -> JSON text ("" = removed). */
    public Map<ResourceLocation, String> changes() {
        Map<ResourceLocation, String> out = new LinkedHashMap<>();
        for (ResourceLocation id : files.keySet()) if (isChanged(id)) out.put(id, files.get(id).toString());
        for (ResourceLocation id : removed) out.put(id, "");
        return out;
    }

    public void put(ResourceLocation id, JsonObject json) {
        files.put(id, json);
        removed.remove(id);
        edited();
    }

    /** Takes a file out. One that only existed in this session just disappears; others are removed on save. */
    public void remove(ResourceLocation id) {
        files.remove(id);
        if (original.containsKey(id)) removed.add(id);
        edited();
    }

    public void revert(ResourceLocation id) {
        removed.remove(id);
        if (original.containsKey(id)) files.put(id, JsonParser.parseString(original.get(id)).getAsJsonObject());
        else files.remove(id);
        edited();
    }

    /** Call after any change, so the preview is worked out again. */
    public void edited() {
        preview = null;
    }

    /** The working copy parsed like the server would (disabled files skipped, errors listed). */
    public TRData preview() {
        if (preview == null) {
            Map<ResourceLocation, JsonElement> map = new LinkedHashMap<>();
            files.forEach((id, o) -> {
                if (!isSpecial(id)) map.put(id, o.deepCopy());
            });
            preview = TRData.parseOnly(map);
        }
        return preview;
    }

    /** Errors the preview found in one file. */
    public List<String> errorsFor(ResourceLocation id) {
        List<String> out = new ArrayList<>();
        String head = id + ": ";
        for (String e : preview().errors()) if (e.startsWith(head)) out.add(e.substring(head.length()));
        return out;
    }

    // ---- the editor's exclusions file ------------------------------------------------------------------------

    /** True if any exclusions file names this entry ("mod:*", an id or a #tag) directly. */
    public boolean excludedEntry(String entry) {
        for (ResourceLocation id : filesIn("exclusions")) {
            if (isDisabled(id)) continue;
            for (JsonElement e : entries(files.get(id).get("items"))) if (isEntry(e, entry)) return true;
        }
        return false;
    }

    /** Adds or takes out an entry. Adding goes to the editor's file; taking out removes it from every file. */
    public void setExcluded(String entry, boolean excluded) {
        if (excluded) {
            if (excludedEntry(entry)) return;
            JsonObject o = files.get(EXCLUSIONS);
            if (o == null) {
                o = Schema.template("exclusions");
                files.put(EXCLUSIONS, o);
                removed.remove(EXCLUSIONS);
            }
            if (!o.has("items") || !o.get("items").isJsonArray()) o.add("items", new JsonArray());
            o.getAsJsonArray("items").add(entry);
        } else {
            for (ResourceLocation id : filesIn("exclusions")) {
                JsonObject o = files.get(id);
                JsonElement items = o.get("items");
                if (items == null) continue;
                // a single string counts as a one-entry list (like the loader reads it); written back as a list
                JsonArray arr = entries(items);
                boolean removed = false;
                for (int i = arr.size() - 1; i >= 0; i--) {
                    if (isEntry(arr.get(i), entry)) {
                        arr.remove(i);
                        removed = true;
                    }
                }
                if (removed) o.add("items", arr);   // files that don't have the entry stay untouched
            }
            // the editor's file was only made for that entry in this session: gone again with it, so taking the
            // entry back out is no change (it used to stay as a new, empty file and keep Save lit)
            JsonObject own = files.get(EXCLUSIONS);
            if (own != null && isNew(EXCLUSIONS) && own.equals(Schema.template("exclusions"))) files.remove(EXCLUSIONS);
        }
        edited();
    }

    // an "items" value as a list: a single string is a one-entry list (as the loader reads it)
    private static JsonArray entries(@Nullable JsonElement items) {
        if (items != null && items.isJsonArray()) return items.getAsJsonArray();
        JsonArray a = new JsonArray();
        if (items != null && items.isJsonPrimitive()) a.add(items);
        return a;
    }

    // true if a list entry is this text (entries of another shape never are)
    private static boolean isEntry(JsonElement e, String entry) {
        return e.isJsonPrimitive() && e.getAsString().equals(entry);
    }
}
