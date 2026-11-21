package com.flankrat.tarnishedrelics.client.editor;

import com.flankrat.tarnishedrelics.client.TRClient;
import com.flankrat.tarnishedrelics.data.AutoMatch;
import com.flankrat.tarnishedrelics.name.NameFallback;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * What the editor knows about every datapack file: the fields each folder's files can have, how to edit each one,
 * and a short help line (shown when hovering the field's name). It mirrors the formats documented on the data
 * classes (Category, Rarity, Affix, Effect, Conditions, ValueSpec, NameList, Levels) and the README; a field the
 * editor doesn't know still shows, as raw JSON, so nothing in a hand-written file is lost.
 */
public final class Schema {

    /** How a field is edited. */
    public enum Kind {
        TEXT, NUMBER, INT, BOOL, ENUM, COLOR,
        /** list of strings (or a single string) */
        LIST,
        /** [min, max] or a single number */
        RANGE,
        /** ValueSpec: number, [min, max], or per rarity */
        VALUE,
        /** int, or per rarity { "magic": 10 } */
        WEIGHT,
        /** { key: int } */
        MAP_INT,
        /** { key: [min, max] } (a rarity's rolls) */
        MAP_RANGE,
        /** an effect's conditions object */
        CONDITIONS,
        /** an affix's effects list */
        EFFECTS
    }

    /** What the "..." picker next to a field offers. */
    public enum Pick {
        NONE, ITEM, RARITY_ITEM, ENTITY, DAMAGE, BIOME, DIMENSION, BLOCK, ATTRIBUTE, MOB_EFFECT, CATEGORY, RARITY, AUTO, POOL,
        PROFESSION, LOOT_TYPE, TAG_ITEM, ENCHANTMENT, LEGENDARY
    }

    public record Field(String key, Kind kind, Pick pick, List<String> options, String help, Supplier<JsonElement> def) {
    }

    private static Field f(String key, Kind kind, Pick pick, String help, Supplier<JsonElement> def, String... options) {
        return new Field(key, kind, pick, Arrays.asList(options), help, def);
    }

    private static JsonElement num(Number n) {
        return new JsonPrimitive(n);
    }

    private static JsonElement str(String s) {
        return new JsonPrimitive(s);
    }

    private static JsonArray arr(Object... values) {
        JsonArray a = new JsonArray();
        for (Object v : values) {
            if (v instanceof Number n) a.add(n);
            else a.add(String.valueOf(v));
        }
        return a;
    }

    private static final String[] SLOTS = {"mainhand", "offhand", "head", "chest", "legs", "feet"};

    public static final List<String> FOLDERS = List.of("categories", "rarities", "affixes", "legendaries", "names", "levels", "exclusions", "tags", "config");

    /** The config file's options (config/tarnishedrelics-common.toml), shown in the Settings tab. */
    public static final List<Field> CONFIG = List.of(
            f("enabled", Kind.BOOL, Pick.NONE, "Relics can turn up in loot at all", () -> new JsonPrimitive(true)),
            f("chance", Kind.NUMBER, Pick.NONE, "Chance (0-1) that one relic is added each time loot of the kinds below is generated", () -> num(0.03)),
            f("lootTypes", Kind.LIST, Pick.LOOT_TYPE, "Kinds of loot that can get one: chest, entity (mob drops: rolled on every kill, so it adds up fast), fishing, archaeology, gift, barter", () -> arr("chest")),
            f("entityNeedsPlayerKill", Kind.BOOL, Pick.NONE, "If entity is on: mob drops only count for kills by a player (stops mob farms)", () -> new JsonPrimitive(true)),
            f("excludeTables", Kind.LIST, Pick.NONE, "Loot table ids that never get relics (* works as a wildcard)", JsonArray::new),
            f("confirmSeconds", Kind.INT, Pick.NONE, "Seconds to use the relic on the smith again to pay (1-120)", () -> num(10)),
            f("showRarityColorUnidentified", Kind.BOOL, Pick.NONE, "Unidentified relics show their rarity's color as a hint", () -> new JsonPrimitive(false)));

    /** A tag file's fields; the picker depends on the tag's registry (tags/<registry>/<name>). */
    public static List<Field> tagFields(String registry) {
        Pick pick = switch (registry) {
            // 1.20.1 tag folders: entity_types, items, blocks, and the registry name for the rest
            case "villager_profession" -> Pick.PROFESSION;
            case "entity_types", "entity_type" -> Pick.ENTITY;
            case "blocks", "block" -> Pick.BLOCK;
            case "items", "item" -> Pick.TAG_ITEM;
            default -> Pick.NONE;
        };
        String help = switch (registry) {
            case "villager_profession" -> "Villager professions (ids or #tags)";
            case "entity_types", "entity_type" -> "Mobs (ids or #tags)";
            default -> "Ids or #tags";
        };
        return List.of(f("values", Kind.LIST, pick, help, JsonArray::new));
    }

    public static final Map<String, List<Field>> FILES = new LinkedHashMap<>();
    public static final List<Field> CONDITIONS;
    public static final Map<String, Field> EFFECT_FIELDS = new LinkedHashMap<>();
    /** The fields each effect type uses (besides "type" and "conditions"), first ones added to a new effect. */
    public static final Map<String, List<String>> EFFECT_TYPES = new LinkedHashMap<>();

    static {
        FILES.put("categories", List.of(
                f("name", Kind.TEXT, Pick.NONE, "Name while unidentified (lang key or plain text). Default: item.<ns>.unidentified.<file>", () -> str("Unidentified Thing")),
                f("auto", Kind.LIST, Pick.AUTO, "Item kinds picked up from every loaded mod", () -> arr("sword")),
                f("items", Kind.LIST, Pick.ITEM, "Items: ids, #tags, mod:* (whole mod) or @category", JsonArray::new),
                f("exclude", Kind.LIST, Pick.ITEM, "Items that never count for this category", JsonArray::new),
                f("slot", Kind.ENUM, Pick.NONE, "Slot the affixes work in", () -> str("mainhand"), SLOTS),
                f("silhouette", Kind.ENUM, Pick.NONE, "Shape shown while unidentified", () -> str("generic"), TRClient.SILHOUETTES.toArray(String[]::new)),
                f("priority", Kind.INT, Pick.NONE, "When an item fits several categories, the highest priority wins", () -> num(0)),
                f("weight", Kind.INT, Pick.NONE, "How often a relic is this category, relative to the others (default 10)", () -> num(10)),
                f("smiths", Kind.LIST, Pick.PROFESSION, "Villagers who identify its relics: professions or #tags like #tarnishedrelics:toolsmiths (none = any blacksmith)", () -> arr("#tarnishedrelics:weaponsmiths"))));
        FILES.put("rarities", List.of(
                f("name", Kind.TEXT, Pick.NONE, "Shown name (lang key or plain text). Default: tarnishedrelics.rarity.<ns>.<file>", () -> str("New Rarity")),
                f("weight", Kind.INT, Pick.NONE, "How often it drops, relative to the other rarities", () -> num(10)),
                f("items", Kind.LIST, Pick.RARITY_ITEM, "Base items: * (anything with a category), @category, ids, #tags, mod:*", () -> arr("*")),
                f("levels", Kind.RANGE, Pick.NONE, "Material levels * and @category items must have (0 wood/leather ... 4 netherite)", () -> arr(1, 2)),
                f("exclude", Kind.LIST, Pick.ITEM, "Items this rarity never uses", JsonArray::new),
                f("rolls", Kind.MAP_RANGE, Pick.POOL, "How many affixes it rolls from each pool", () -> {
                    JsonObject o = new JsonObject();
                    o.add("normal", arr(1, 2));
                    return o;
                }),
                f("identify_level", Kind.INT, Pick.NONE, "Blacksmith level needed: 1 novice, 2 apprentice, 3 journeyman, 4 expert, 5 master", () -> num(1)),
                f("cost", Kind.RANGE, Pick.NONE, "Emeralds the blacksmith asks", () -> arr(2, 4)),
                f("villager_xp", Kind.INT, Pick.NONE, "Trade XP the blacksmith gets for identifying it", () -> num(5)),
                f("color", Kind.COLOR, Pick.NONE, "Name color once identified (#RRGGBB)", () -> str("#FFFFFF")),
                f("unique_name", Kind.BOOL, Pick.NONE, "Ends the name with \"of <generated name>\"", () -> new JsonPrimitive(false)),
                f("legendary", Kind.BOOL, Pick.NONE, "A legendary: generated (generated name and lore, signature enchantments, maybe a drawback) or, by handmade_chance, one from the Legendary tab", () -> new JsonPrimitive(true)),
                f("handmade_chance", Kind.NUMBER, Pick.NONE, "Legendary: share (0-1) that are hand-made ones from the Legendary tab", () -> num(0.3)),
                f("signature_enchantments", Kind.RANGE, Pick.NONE, "Legendary: how many enchantments that fit the item it gets, above their max", () -> arr(2, 3)),
                f("enchantment_bonus", Kind.RANGE, Pick.NONE, "Legendary: levels above the enchantment's normal max", () -> arr(1, 3)),
                f("drawback_chance", Kind.NUMBER, Pick.NONE, "Legendary: chance (0-1) of one affix from the drawback pool", () -> num(0.35)),
                f("drawback_pool", Kind.TEXT, Pick.POOL, "Legendary: the pool drawbacks come from", () -> str("drawback")),
                f("lore_lines", Kind.RANGE, Pick.NONE, "Legendary: generated lore lines (from the Names tab's lore)", () -> arr(1, 2)),
                f("quality", Kind.INT, Pick.NONE, "Extra weight per point of the looter's Luck (Luck potions, luck gear)", () -> num(1)),
                f("loot_tables", Kind.LIST, Pick.NONE, "Only drops in these loot tables (* wildcards, e.g. somemod:chests/boss_*); empty = any", JsonArray::new)));
        FILES.put("legendaries", List.of(
                f("name", Kind.TEXT, Pick.NONE, "Its name (lang key or plain text)", () -> str("Nameless Blade")),
                f("lore", Kind.LIST, Pick.NONE, "Lines under the name (lang keys or plain text)", JsonArray::new),
                f("items", Kind.LIST, Pick.ITEM, "Base items, one is picked: ids, #tags, mod:*", JsonArray::new),
                f("exclude", Kind.LIST, Pick.ITEM, "Items it's never made on (taken out of the items above)", JsonArray::new),
                f("category", Kind.TEXT, Pick.CATEGORY, "Category (slot, silhouette) if the base item has none of its own", () -> str("tarnishedrelics:sword")),
                f("enchantments", Kind.MAP_INT, Pick.ENCHANTMENT, "Put on as they are: above the normal max level, and any combination", JsonObject::new),
                f("effects", Kind.EFFECTS, Pick.NONE, "What it does; value ranges are rolled once per copy", JsonArray::new),
                f("weight", Kind.INT, Pick.NONE, "How often it's picked among the legendaries (0 = only drops with its set)", () -> num(10)),
                f("companions", Kind.LIST, Pick.LEGENDARY, "Other legendaries that drop with it, as a set", JsonArray::new)));
        FILES.put("affixes", List.of(
                f("kind", Kind.ENUM, Pick.NONE, "prefix/suffix put a word in the name; hidden adds none", () -> str("prefix"), "prefix", "suffix", "hidden"),
                f("name", Kind.TEXT, Pick.NONE, "Name word (lang key or plain text), e.g. Champion's / of the Bear", () -> str("New")),
                f("pool", Kind.TEXT, Pick.POOL, "Pool rarities roll it from (normal, unique, or your own)", () -> str("normal")),
                f("applies_to", Kind.LIST, Pick.CATEGORY, "Categories it can roll on (* = any)", () -> arr("*")),
                f("weight", Kind.WEIGHT, Pick.RARITY, "How often it rolls, the same for every rarity or per rarity", () -> num(10)),
                f("group", Kind.TEXT, Pick.NONE, "Only one affix of a group per item", () -> str("")),
                f("effects", Kind.EFFECTS, Pick.NONE, "What it does", JsonArray::new)));
        FILES.put("names", List.of(
                f("names", Kind.LIST, Pick.NONE, "Whole names for Uniques, used as they are", JsonArray::new),
                f("starts", Kind.LIST, Pick.NONE, "Name starts (a start + an end makes a name)", JsonArray::new),
                f("ends", Kind.LIST, Pick.NONE, "Name ends", JsonArray::new),
                f("titles", Kind.LIST, Pick.NONE, "Generated legendaries: \"<name>, <title>\" half the time", JsonArray::new),
                f("lore", Kind.LIST, Pick.NONE, "Generated legendaries: lore lines ({name} = its name)", JsonArray::new)));
        FILES.put("levels", List.of(
                f("items", Kind.MAP_INT, Pick.ITEM, "Material level of items: ids, #tags, mod:* (beats every rule below)", JsonObject::new),
                f("keywords", Kind.MAP_INT, Pick.NONE, "Level for items whose id has the word (iron_longbow has iron)", JsonObject::new),
                f("default", Kind.INT, Pick.NONE, "Level of anything no rule covers", () -> num(2))));
        FILES.put("exclusions", List.of(
                f("items", Kind.LIST, Pick.ITEM, "Items that are never relics: ids, #tags, mod:* (whole mod)", JsonArray::new)));

        CONDITIONS = List.of(
                f("chance", Kind.NUMBER, Pick.NONE, "Chance it happens (0-1); break speed ignores it (the client works the speed out too)", () -> num(0.25)),
                f("health_below", Kind.NUMBER, Pick.NONE, "Only below this share of max health (0-1)", () -> num(0.5)),
                f("health_above", Kind.NUMBER, Pick.NONE, "Only above this share of max health (0-1)", () -> num(0.5)),
                f("in_water", Kind.BOOL, Pick.NONE, "In water", () -> new JsonPrimitive(true)),
                f("is_night", Kind.BOOL, Pick.NONE, "At night", () -> new JsonPrimitive(true)),
                f("is_day", Kind.BOOL, Pick.NONE, "In the day", () -> new JsonPrimitive(true)),
                f("sneaking", Kind.BOOL, Pick.NONE, "Sneaking", () -> new JsonPrimitive(true)),
                f("on_fire", Kind.BOOL, Pick.NONE, "On fire", () -> new JsonPrimitive(true)),
                f("y_below", Kind.NUMBER, Pick.NONE, "Below this height", () -> num(0)),
                f("y_above", Kind.NUMBER, Pick.NONE, "Above this height", () -> num(100)),
                f("dimensions", Kind.LIST, Pick.DIMENSION, "Only in these dimensions", JsonArray::new),
                f("biomes", Kind.LIST, Pick.BIOME, "Only in these biomes (ids or #tags)", JsonArray::new),
                f("target", Kind.LIST, Pick.ENTITY, "Only against these mobs (ids or #tags)", JsonArray::new));

        for (Field fl : List.of(
                f("value", Kind.VALUE, Pick.NONE, "Strength: a number, a [min, max] roll, or per rarity", () -> num(0.1)),
                f("attribute", Kind.TEXT, Pick.ATTRIBUTE, "Attribute id", () -> str("minecraft:generic.attack_damage")),
                f("operation", Kind.ENUM, Pick.NONE, "How the value is applied", () -> str("add"), "add", "multiply_base", "multiply_total"),
                f("target", Kind.LIST, Pick.ENTITY, "Mobs it works against (ids or #tags; none = all)", JsonArray::new),
                f("damage", Kind.LIST, Pick.DAMAGE, "Damage types (ids or #tags; none = all)", JsonArray::new),
                f("effect", Kind.TEXT, Pick.MOB_EFFECT, "Potion effect id", () -> str("minecraft:slowness")),
                f("duration", Kind.INT, Pick.NONE, "Ticks (20 = 1 second)", () -> num(100)),
                f("amplifier", Kind.INT, Pick.NONE, "Level - 1 (0 = level I)", () -> num(0)),
                f("apply_to", Kind.ENUM, Pick.NONE, "Who gets the effect", () -> str("target"), "target", "attacker", "self"),
                f("blocks", Kind.TEXT, Pick.BLOCK, "Only for this block or #tag (optional)", () -> str("#minecraft:mineable/pickaxe")),
                f("function", Kind.TEXT, Pick.NONE, "Datapack function id", () -> str("mypack:on_hit")),
                f("trigger", Kind.ENUM, Pick.NONE, "When the function runs", () -> str("hit"), "hit", "kill", "hurt", "tick"),
                f("as", Kind.ENUM, Pick.NONE, "Who the function runs as", () -> str("wielder"), "wielder", "target"),
                f("conditions", Kind.CONDITIONS, Pick.NONE, "Only when all of these are true", JsonObject::new),
                f("tooltip", Kind.TEXT, Pick.NONE, "Own tooltip line instead of the built-in one (lang key or text; %1$s = value as a percent, %2$s = plain number)", () -> str("%1$s more of something")),
                f("hidden", Kind.BOOL, Pick.NONE, "No tooltip line for this effect", () -> new JsonPrimitive(true)),
                f("slot", Kind.ENUM, Pick.NONE, "Works while the item is in this slot instead of its category's (e.g. a dagger that helps from the offhand)", () -> str("offhand"), SLOTS))) {
            EFFECT_FIELDS.put(fl.key(), fl);
        }
        EFFECT_TYPES.put("attribute", List.of("attribute", "operation", "value"));
        EFFECT_TYPES.put("damage_vs", List.of("value", "target"));
        EFFECT_TYPES.put("damage_taken", List.of("value", "damage"));
        EFFECT_TYPES.put("on_hit_effect", List.of("effect", "duration", "amplifier", "apply_to"));
        EFFECT_TYPES.put("on_hit_fire", List.of("value"));
        EFFECT_TYPES.put("lifesteal", List.of("value"));
        EFFECT_TYPES.put("thorns", List.of("value"));
        EFFECT_TYPES.put("on_hurt_effect", List.of("effect", "duration", "amplifier", "apply_to"));
        EFFECT_TYPES.put("passive_effect", List.of("effect", "amplifier"));
        EFFECT_TYPES.put("crit_chance", List.of("value"));
        EFFECT_TYPES.put("crit_damage", List.of("value"));
        EFFECT_TYPES.put("break_speed", List.of("value", "blocks"));
        EFFECT_TYPES.put("durability", List.of("value"));
        EFFECT_TYPES.put("xp_bonus", List.of("value"));
        EFFECT_TYPES.put("looting", List.of("value"));
        EFFECT_TYPES.put("function", List.of("function", "trigger", "as"));
    }

    /** Help shown for an effect type. */
    public static String effectHelp(String type) {
        return switch (type) {
            case "attribute" -> "Changes an attribute while held/worn";
            case "damage_vs" -> "More (or less) damage against some mobs";
            case "damage_taken" -> "Take less (or more) damage of some types";
            case "on_hit_effect" -> "Potion effect when hitting";
            case "on_hit_fire" -> "Sets the target on fire (value = seconds)";
            case "lifesteal" -> "Heals a share of melee damage dealt";
            case "thorns" -> "Sends a share of damage taken back";
            case "on_hurt_effect" -> "Potion effect when getting hit";
            case "passive_effect" -> "Potion effect kept up while held/worn";
            case "crit_chance" -> "Extra chance to crit";
            case "crit_damage" -> "Crits hit harder";
            case "break_speed" -> "Mines faster";
            case "durability" -> "Chance each point of wear is ignored";
            case "xp_bonus" -> "More XP from kills";
            case "looting" -> "Extra Looting levels";
            case "function" -> "Runs a datapack function";
            default -> "";
        };
    }

    /** The starting JSON of a new file in a folder. */
    /**
     * What a new file starts as: a copy of another file (copy, may be null) or the folder's template, switched on,
     * with a readable name from the file name so it never shows a raw lang key.
     */
    public static JsonObject newFile(String folder, String name, @Nullable JsonObject copy) {
        JsonObject start = copy != null ? copy.deepCopy() : template(folder);
        start.remove("disabled");
        String title = NameFallback.title(name);
        switch (folder) {
            case "categories" -> start.addProperty("name", "Unidentified " + title);
            case "rarities" -> start.addProperty("name", title);
            case "affixes" -> start.addProperty("name", title);
            case "legendaries" -> start.addProperty("name", title);   // a legendary can't load without one
            default -> { }
        }
        return start;
    }

    public static JsonObject template(String folder) {
        JsonObject o = new JsonObject();
        switch (folder) {
            case "categories" -> {
                o.add("auto", arr("sword"));
                o.addProperty("slot", "mainhand");
                o.addProperty("silhouette", "sword");
            }
            case "rarities" -> {
                o.addProperty("weight", 10);
                o.add("items", arr("*"));
                o.add("levels", arr(1, 2));
                JsonObject rolls = new JsonObject();
                rolls.add("normal", arr(1, 2));
                o.add("rolls", rolls);
                o.addProperty("identify_level", 1);
                o.add("cost", arr(2, 4));
                o.addProperty("villager_xp", 5);
                o.addProperty("color", "#FFFFFF");
            }
            case "affixes" -> {
                o.addProperty("kind", "prefix");
                o.addProperty("name", "New");
                o.addProperty("pool", "normal");
                o.add("applies_to", arr("*"));
                o.addProperty("weight", 10);
                o.add("effects", new JsonArray());
            }
            case "names" -> o.add("names", new JsonArray());
            case "levels" -> o.add("items", new JsonObject());
            case "exclusions" -> o.add("items", new JsonArray());
            case "tags" -> o.add("values", new JsonArray());
            case "legendaries" -> {
                o.add("lore", new JsonArray());
                o.add("items", arr("minecraft:diamond_sword"));
                o.add("enchantments", new JsonObject());
                o.add("effects", new JsonArray());
                o.addProperty("weight", 10);
            }
            default -> { }
        }
        return o;
    }

    public static List<String> autoKinds() {
        return Arrays.stream(AutoMatch.values()).map(AutoMatch::key).toList();
    }

    private Schema() {
    }
}
