package com.flankrat.tarnishedrelics.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;

import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.Set;

/**
 * The editor's tag files ({@code tags/<registry>/<name>} in the mod's namespace). Tags from every pack add up, so
 * the editor shows the merged list, checks an edited one, and works out whether saving it needs "replace".
 */
final class EditorTags {

    private EditorTags() {
    }

    /** A tag's values across packs, lowest first, honouring "replace". Packs in skip are left out. */
    static JsonArray mergedValues(List<Resource> stack, Set<String> skip) {
        JsonArray out = new JsonArray();
        for (Resource r : stack) {
            if (skip.contains(r.sourcePackId())) continue;
            try (Reader reader = r.openAsReader()) {
                JsonObject o = JsonParser.parseReader(reader).getAsJsonObject();
                if (o.has("replace") && o.get("replace").getAsBoolean()) out = new JsonArray();
                if (o.has("values")) for (JsonElement v : o.getAsJsonArray("values")) if (!out.contains(v)) out.add(v);
            } catch (IOException | RuntimeException e) {
                // a broken tag file: vanilla reports it, the editor just skips it
            }
        }
        return out;
    }

    static void check(ResourceLocation id, JsonObject o, List<String> errors) {
        if (id.getPath().split("/").length < 3) errors.add(id + ": a tag needs a name like tags/entity_type/dragons");
        JsonElement values = o.get("values");
        if (values == null || !values.isJsonArray()) {
            errors.add(id + ": needs a \"values\" list");
            return;
        }
        for (JsonElement v : values.getAsJsonArray()) {
            String s = v.isJsonObject() && v.getAsJsonObject().has("id") ? v.getAsJsonObject().get("id").getAsString()
                    : v.isJsonPrimitive() ? v.getAsString() : null;
            if (s == null || ResourceLocation.tryParse(s.startsWith("#") ? s.substring(1) : s) == null) errors.add(id + ": bad value " + v);
        }
    }

    /**
     * What to write for an edited tag. Tags add up across packs, so if the new list still has everything the other
     * packs give, "replace": false keeps working when a mod adds to it later. If something from another pack was
     * taken out, only "replace": true can remove it.
     */
    static String toWrite(MinecraftServer server, ResourceLocation id, JsonObject edited) {
        ResourceLocation loc = new ResourceLocation(id.getNamespace(), id.getPath() + ".json");
        JsonArray others = mergedValues(server.getResourceManager().getResourceStack(loc), Set.of(EditorServer.PACK_ID, ModpackPack.ID));
        JsonArray now = edited.getAsJsonArray("values");
        boolean keepsAll = true;
        for (JsonElement v : others) if (!now.contains(v)) keepsAll = false;
        JsonObject out = new JsonObject();
        out.addProperty("replace", !keepsAll);
        out.add("values", now);
        return out.toString();
    }
}
