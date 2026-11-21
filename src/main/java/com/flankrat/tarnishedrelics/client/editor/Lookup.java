package com.flankrat.tarnishedrelics.client.editor;

import com.flankrat.tarnishedrelics.data.Category;
import com.flankrat.tarnishedrelics.data.TRData;
import com.google.gson.JsonElement;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * What the game has loaded, for the editor's pickers and checks: items, mobs, blocks, attributes, potion effects,
 * damage types, biomes, dimensions and their tags, the loaded mods, and the categories/rarities/pools in the
 * working copy. Everything comes from the client's registries, which match the server's.
 */
public final class Lookup {

    /** One choice in a picker. icon may be EMPTY. */
    public record Choice(String value, String label, String namespace, ItemStack icon, String kind) {
    }

    private Lookup() {
    }

    public static String modName(String modId) {
        return ModList.get().getModContainerById(modId).map(c -> c.getModInfo().getDisplayName()).orElse(modId);
    }

    public static boolean modLoaded(String modId) {
        return modId.equals("minecraft") || ModList.get().isLoaded(modId);
    }

    public static List<Choice> choices(Schema.Pick pick, EditorModel model) {
        List<Choice> out = new ArrayList<>();
        switch (pick) {
            case ITEM, RARITY_ITEM -> {
                if (pick == Schema.Pick.RARITY_ITEM) out.add(new Choice("*", "Anything with a category", "", new ItemStack(Items.NETHER_STAR), "special"));
                for (ResourceLocation c : model.filesIn("categories")) {
                    ResourceLocation id = EditorModel.shortId(c);
                    out.add(new Choice("@" + id, "Category: " + id.getPath(), id.getNamespace(), new ItemStack(Items.CHEST), "category"));
                }
                Set<String> mods = new LinkedHashSet<>();
                for (var e : ForgeRegistries.ITEMS.getEntries()) {
                    ResourceLocation id = e.getKey().location();
                    mods.add(id.getNamespace());
                    out.add(new Choice(id.toString(), new ItemStack(e.getValue()).getHoverName().getString(), id.getNamespace(), new ItemStack(e.getValue()), "item"));
                }
                for (String mod : mods) out.add(new Choice(mod + ":*", "Every item from " + modName(mod), mod, ItemStack.EMPTY, "mod"));
                addTags(out, ForgeRegistries.ITEMS);
            }
            case TAG_ITEM -> {
                for (var e : ForgeRegistries.ITEMS.getEntries()) {
                    ResourceLocation id = e.getKey().location();
                    out.add(new Choice(id.toString(), new ItemStack(e.getValue()).getHoverName().getString(), id.getNamespace(), new ItemStack(e.getValue()), "item"));
                }
                addTags(out, ForgeRegistries.ITEMS);
            }
            case PROFESSION -> {
                for (var e : ForgeRegistries.VILLAGER_PROFESSIONS.getEntries()) {
                    ResourceLocation id = e.getKey().location();
                    out.add(new Choice(id.toString(), net.minecraft.network.chat.Component.translatable("entity.minecraft.villager." + id.getPath()).getString(),
                            id.getNamespace(), new ItemStack(Items.EMERALD), "profession"));
                }
                addTags(out, ForgeRegistries.VILLAGER_PROFESSIONS);
            }
            case ENCHANTMENT -> {
                for (var e : ForgeRegistries.ENCHANTMENTS.getEntries()) {
                    ResourceLocation id = e.getKey().location();
                    out.add(new Choice(id.toString(), net.minecraft.network.chat.Component.translatable(e.getValue().getDescriptionId()).getString()
                            + " (max " + e.getValue().getMaxLevel() + ")", id.getNamespace(), new ItemStack(Items.ENCHANTED_BOOK), "enchantment"));
                }
            }
            case LEGENDARY -> {
                for (ResourceLocation f : model.filesIn("legendaries")) {
                    ResourceLocation id = EditorModel.shortId(f);
                    var json = model.get(f);
                    String name = json != null && json.has("name") ? json.get("name").getAsString() : id.getPath();
                    out.add(new Choice(id.toString(), name, id.getNamespace(), new ItemStack(Items.NETHER_STAR), "legendary"));
                }
            }
            case LOOT_TYPE -> {
                for (String t : com.flankrat.tarnishedrelics.editor.ConfigFile.LOOT_TYPES) out.add(new Choice(t, t, "", ItemStack.EMPTY, "loot"));
            }
            case ENTITY -> {
                for (var e : ForgeRegistries.ENTITY_TYPES.getEntries()) {
                    ResourceLocation id = e.getKey().location();
                    out.add(new Choice(id.toString(), e.getValue().getDescription().getString(), id.getNamespace(), spawnEgg(id), "entity"));
                }
                addTags(out, ForgeRegistries.ENTITY_TYPES);
            }
            case BLOCK -> {
                for (var e : ForgeRegistries.BLOCKS.getEntries()) {
                    ResourceLocation id = e.getKey().location();
                    out.add(new Choice(id.toString(), e.getValue().getName().getString(), id.getNamespace(), new ItemStack(e.getValue()), "block"));
                }
                addTags(out, ForgeRegistries.BLOCKS);
            }
            case ATTRIBUTE -> {
                for (var e : ForgeRegistries.ATTRIBUTES.getEntries()) {
                    ResourceLocation id = e.getKey().location();
                    out.add(new Choice(id.toString(), net.minecraft.network.chat.Component.translatable(e.getValue().getDescriptionId()).getString(),
                            id.getNamespace(), ItemStack.EMPTY, "attribute"));
                }
            }
            case MOB_EFFECT -> {
                for (var e : ForgeRegistries.MOB_EFFECTS.getEntries()) {
                    ResourceLocation id = e.getKey().location();
                    out.add(new Choice(id.toString(), e.getValue().getDisplayName().getString(), id.getNamespace(), new ItemStack(Items.POTION), "effect"));
                }
            }
            case DAMAGE -> addDynamic(out, Registries.DAMAGE_TYPE, "damage");
            case BIOME -> addDynamic(out, Registries.BIOME, "biome");
            case DIMENSION -> {
                var conn = Minecraft.getInstance().getConnection();
                if (conn != null) for (var key : conn.levels()) {
                    ResourceLocation id = key.location();
                    out.add(new Choice(id.toString(), id.getPath(), id.getNamespace(), ItemStack.EMPTY, "dimension"));
                }
            }
            case CATEGORY -> {
                out.add(new Choice("*", "Any category", "", new ItemStack(Items.NETHER_STAR), "special"));
                for (ResourceLocation c : model.filesIn("categories")) {
                    ResourceLocation id = EditorModel.shortId(c);
                    out.add(new Choice(id.toString(), id.getPath(), id.getNamespace(), new ItemStack(Items.CHEST), "category"));
                }
            }
            case RARITY -> {
                out.add(new Choice("default", "Every other rarity", "", ItemStack.EMPTY, "special"));
                for (ResourceLocation r : model.filesIn("rarities")) {
                    ResourceLocation id = EditorModel.shortId(r);
                    out.add(new Choice(id.getPath(), id.getPath() + " (" + id + ")", id.getNamespace(), ItemStack.EMPTY, "rarity"));
                }
            }
            case AUTO -> {
                for (String k : Schema.autoKinds()) out.add(new Choice(k, k, "", ItemStack.EMPTY, "auto"));
            }
            case POOL -> {
                for (String p : pools(model)) out.add(new Choice(p, p, "", ItemStack.EMPTY, "pool"));
            }
            default -> { }
        }
        out.sort(Comparator.comparing((Choice c) -> order(c.kind())).thenComparing(Choice::value));
        return out;
    }

    // specials and categories first, then plain entries, then mods and tags
    private static int order(String kind) {
        return switch (kind) {
            case "special" -> 0;
            case "category" -> 1;
            case "mod" -> 3;
            case "tag" -> 4;
            default -> 2;
        };
    }

    private static <T> void addTags(List<Choice> out, IForgeRegistry<T> registry) {
        var tags = registry.tags();
        if (tags == null) return;
        tags.getTagNames().forEach(t -> out.add(new Choice("#" + t.location(), "Tag: " + t.location().getPath(), t.location().getNamespace(), ItemStack.EMPTY, "tag")));
    }

    private static <T> void addDynamic(List<Choice> out, ResourceKey<? extends Registry<T>> key, String kind) {
        Optional<? extends Registry<T>> reg = registry(key);
        if (reg.isEmpty()) return;
        for (ResourceLocation id : reg.get().keySet()) out.add(new Choice(id.toString(), id.getPath(), id.getNamespace(), ItemStack.EMPTY, kind));
        reg.get().getTagNames().forEach(t -> out.add(new Choice("#" + t.location(), "Tag: " + t.location().getPath(), t.location().getNamespace(), ItemStack.EMPTY, "tag")));
    }

    private static <T> Optional<? extends Registry<T>> registry(ResourceKey<? extends Registry<T>> key) {
        var level = Minecraft.getInstance().level;
        return level == null ? Optional.empty() : level.registryAccess().registry(key);
    }

    private static ItemStack spawnEgg(ResourceLocation entity) {
        Item egg = ForgeRegistries.ITEMS.getValue(new ResourceLocation(entity.getNamespace(), entity.getPath() + "_spawn_egg"));
        return egg == null || egg == Items.AIR ? ItemStack.EMPTY : new ItemStack(egg);
    }

    /** Every pool named in the working copy's rarities and affixes, plus the two built-in ones. */
    public static Set<String> pools(EditorModel model) {
        Set<String> out = new LinkedHashSet<>(List.of("normal", "unique"));
        for (ResourceLocation r : model.filesIn("rarities")) {
            JsonElement rolls = model.get(r).get("rolls");
            if (rolls != null && rolls.isJsonObject()) out.addAll(rolls.getAsJsonObject().keySet());
        }
        for (ResourceLocation a : model.filesIn("affixes")) {
            JsonElement p = model.get(a).get("pool");
            if (p != null && p.isJsonPrimitive()) out.add(p.getAsString());
        }
        return out;
    }

    /**
     * Problem with a picker field's value, or null if it's fine. Values naming a mod that isn't loaded are allowed
     * (a pack can support optional mods) but flagged, since they do nothing here.
     */
    @Nullable
    public static String check(Schema.Pick pick, String value, EditorModel model) {
        if (value.isEmpty()) return "Empty";
        switch (pick) {
            case ITEM, RARITY_ITEM -> {
                if (value.equals("*")) return pick == Schema.Pick.RARITY_ITEM ? null : "* only works in rarities";
                if (value.startsWith("@")) return model.exists(EditorModel.fileId("categories", rl(value.substring(1)))) ? null : "No category " + value.substring(1);
                if (value.endsWith(":*")) {
                    String mod = value.substring(0, value.length() - 2);
                    return modLoaded(mod) ? null : "Mod \"" + mod + "\" isn't loaded, so this does nothing here";
                }
                return checkRegistry(value, ForgeRegistries.ITEMS);
            }
            case ENTITY -> {
                return checkRegistry(value, ForgeRegistries.ENTITY_TYPES);
            }
            case TAG_ITEM -> {
                return checkRegistry(value, ForgeRegistries.ITEMS);
            }
            case PROFESSION -> {
                return checkRegistry(value, ForgeRegistries.VILLAGER_PROFESSIONS);
            }
            case ENCHANTMENT -> {
                return checkRegistry(value, ForgeRegistries.ENCHANTMENTS);
            }
            case LEGENDARY -> {
                return model.exists(EditorModel.fileId("legendaries", rl(value))) ? null : "No legendary " + value;
            }
            case LOOT_TYPE -> {
                return com.flankrat.tarnishedrelics.editor.ConfigFile.LOOT_TYPES.contains(value) ? null : "Not a loot type";
            }
            case BLOCK -> {
                return checkRegistry(value, ForgeRegistries.BLOCKS);
            }
            case ATTRIBUTE -> {
                return checkRegistry(value, ForgeRegistries.ATTRIBUTES);
            }
            case MOB_EFFECT -> {
                return checkRegistry(value, ForgeRegistries.MOB_EFFECTS);
            }
            case DAMAGE -> {
                return checkDynamic(value, Registries.DAMAGE_TYPE);
            }
            case BIOME -> {
                return checkDynamic(value, Registries.BIOME);
            }
            case CATEGORY -> {
                if (value.equals("*")) return null;
                return model.exists(EditorModel.fileId("categories", rl(value))) ? null : "No category " + value;
            }
            case RARITY -> {
                if (value.equals("default")) return null;
                for (ResourceLocation r : model.filesIn("rarities")) {
                    ResourceLocation id = EditorModel.shortId(r);
                    if (id.toString().equals(value) || id.getPath().equals(value)) return null;
                }
                return "No rarity " + value;
            }
            case AUTO -> {
                return Schema.autoKinds().contains(value) ? null : "Unknown kind";
            }
            default -> {
                return null;
            }
        }
    }

    @Nullable
    private static ResourceLocation rlOrNull(String s) {
        return ResourceLocation.tryParse(s);
    }

    private static ResourceLocation rl(String s) {
        ResourceLocation r = rlOrNull(s);
        return r == null ? new ResourceLocation("minecraft", "invalid") : r;
    }

    @Nullable
    private static <T> String checkRegistry(String value, IForgeRegistry<T> registry) {
        boolean tag = value.startsWith("#");
        ResourceLocation id = rlOrNull(tag ? value.substring(1) : value);
        if (id == null) return "Not a valid id";
        if (!modLoaded(id.getNamespace())) return "Mod \"" + id.getNamespace() + "\" isn't loaded, so this does nothing here";
        if (tag) {
            var tags = registry.tags();
            boolean known = tags != null && tags.isKnownTagName(net.minecraft.tags.TagKey.create(registry.getRegistryKey(), id));
            return known ? null : "No such tag (yet)";
        }
        return registry.containsKey(id) ? null : "Nothing with this id";
    }

    @Nullable
    private static <T> String checkDynamic(String value, ResourceKey<? extends Registry<T>> key) {
        boolean tag = value.startsWith("#");
        ResourceLocation id = rlOrNull(tag ? value.substring(1) : value);
        if (id == null) return "Not a valid id";
        if (!modLoaded(id.getNamespace())) return "Mod \"" + id.getNamespace() + "\" isn't loaded, so this does nothing here";
        Optional<? extends Registry<T>> reg = registry(key);
        if (reg.isEmpty()) return null;
        if (tag) return reg.get().getTagNames().anyMatch(t -> t.location().equals(id)) ? null : "No such tag";
        return reg.get().containsKey(id) ? null : "Nothing with this id";
    }

    // ---- relic candidates per mod, for the Mods tab ------------------------------------------------------------

    /** Per loaded mod: its items that fit a category (ignoring exclusions), in the working copy. */
    public static Map<String, List<Item>> candidatesByMod(TRData data) {
        Map<String, List<Item>> out = new TreeMap<>();
        for (var e : ForgeRegistries.ITEMS.getEntries()) {
            Item item = e.getValue();
            boolean fits = false;
            for (Category c : data.categories().values()) {
                if (c.matches(item)) {
                    fits = true;
                    break;
                }
            }
            if (fits) out.computeIfAbsent(e.getKey().location().getNamespace(), k -> new ArrayList<>()).add(item);
        }
        return out;
    }
}
