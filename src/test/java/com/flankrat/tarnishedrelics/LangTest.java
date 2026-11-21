package com.flankrat.tarnishedrelics;

import com.flankrat.tarnishedrelics.data.Affix;
import com.flankrat.tarnishedrelics.data.Category;
import com.flankrat.tarnishedrelics.data.Effect;
import com.flankrat.tarnishedrelics.data.Legendary;
import com.flankrat.tarnishedrelics.data.Rarity;
import com.flankrat.tarnishedrelics.data.TRData;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.world.entity.EquipmentSlot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every lang key the mod shows has an English text, so nothing in game shows a raw key like
 * "affix.tarnishedrelics.vital". Keys come from the real built-in data (names of categories, rarities, affixes,
 * legendaries and their lore, effect lines) and from every Component.translatable("tarnishedrelics...") in the code.
 */
class LangTest {

    private static JsonObject lang() throws IOException {
        try (var in = LangTest.class.getClassLoader().getResourceAsStream("assets/tarnishedrelics/lang/en_us.json")) {
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    // a data value that is a lang key rather than plain text (plain text is allowed and shown as it is)
    private static boolean isKey(String s) {
        return s.contains(".") && !s.contains(" ");
    }

    private static void need(Set<String> missing, JsonObject lang, String key, String where) {
        if (!lang.has(key)) missing.add(key + "  (" + where + ")");
    }

    @Test
    void builtInDataHasEveryText() throws IOException {
        JsonObject lang = lang();
        TRData data = BuiltInData.data();
        Set<String> missing = new TreeSet<>();
        for (Category c : data.categories().values()) {
            String key = c.unidentifiedKey();
            if (isKey(key)) need(missing, lang, key, "category " + c.id());
        }
        for (Rarity r : data.rarities().values()) if (isKey(r.nameKey())) need(missing, lang, r.nameKey(), "rarity " + r.id());
        for (Affix a : data.affixes().values()) {
            if (!a.kind().equals("hidden") && a.nameKey() != null && isKey(a.nameKey())) need(missing, lang, a.nameKey(), "affix " + a.id());
            if ((a.kind().equals("prefix") || a.kind().equals("suffix")) && a.nameKey() == null) missing.add("(no name) affix " + a.id() + " is a " + a.kind());
        }
        for (Legendary l : data.legendaries().values()) {
            if (isKey(l.name())) need(missing, lang, l.name(), "legendary " + l.id());
            for (String line : l.lore()) if (isKey(line)) need(missing, lang, line, "legendary lore " + l.id());
        }
        assertEquals(Set.of(), missing);
    }

    @Test
    void everyEffectLineHasItsText() throws IOException {
        JsonObject lang = lang();
        Set<String> missing = new TreeSet<>();
        for (Effect.Type t : Effect.Type.values()) {
            if (t == Effect.Type.ATTRIBUTE || t == Effect.Type.FUNCTION) continue;   // shown by vanilla / only with their own tooltip
            need(missing, lang, "tarnishedrelics.effect." + t.key(), "effect type");
        }
        for (String k : List.of("damage_vs.all", "damage_taken.all", "on_hit_effect.self", "on_hurt_effect.self", "conditional", "in_slot")) {
            need(missing, lang, "tarnishedrelics.effect." + k, "effect line");
        }
        for (EquipmentSlot s : EquipmentSlot.values()) need(missing, lang, "tarnishedrelics.slot." + s.getName(), "slot");
        assertEquals(Set.of(), missing);
    }

    // an effect's own "tooltip" (used for conditional effects) is a lang key or plain text; a key with no text showed raw
    @Test
    void everyEffectTooltipHasItsText() throws IOException {
        JsonObject lang = lang();
        TRData data = BuiltInData.data();
        Set<String> missing = new TreeSet<>();
        for (Affix a : data.affixes().values()) for (Effect e : a.effects()) tooltip(missing, lang, e, "affix " + a.id());
        for (Legendary l : data.legendaries().values()) for (Effect e : l.affix().effects()) tooltip(missing, lang, e, "legendary " + l.id());
        assertEquals(Set.of(), missing);
    }

    private static void tooltip(Set<String> missing, JsonObject lang, Effect e, String where) {
        if (!e.json.has("tooltip")) return;
        String t = e.json.get("tooltip").getAsString();
        if (isKey(t)) need(missing, lang, t, where + " tooltip");
    }

    // every Component.translatable("tarnishedrelics...") written in the code (ones with a fallback are fine either way)
    @Test
    void everyKeyInTheCodeHasItsText() throws IOException {
        JsonObject lang = lang();
        Pattern p = Pattern.compile("Component\\.translatable\\(\"((?:tarnishedrelics|item\\.tarnishedrelics)[a-z0-9_.]*)\"");
        Set<String> missing = new TreeSet<>();
        List<Path> sources = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(Path.of("src/main/java"))) {
            walk.filter(f -> f.toString().endsWith(".java")).forEach(sources::add);
        }
        for (Path f : sources) {
            Matcher m = p.matcher(Files.readString(f));
            while (m.find()) {
                String key = m.group(1);
                if (key.endsWith(".")) continue;   // a key built from parts ("...effect." + type), checked above
                need(missing, lang, key, f.getFileName().toString());
            }
        }
        need(missing, lang, "item.tarnishedrelics.unidentified", "the item");
        assertEquals(Set.of(), missing);
    }
}
