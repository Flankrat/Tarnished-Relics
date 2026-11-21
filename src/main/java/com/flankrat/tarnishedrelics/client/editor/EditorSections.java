package com.flankrat.tarnishedrelics.client.editor;

import com.flankrat.tarnishedrelics.data.Category;
import com.flankrat.tarnishedrelics.data.Rarity;
import com.flankrat.tarnishedrelics.data.TRData;
import com.flankrat.tarnishedrelics.editor.EditorServer;
import com.flankrat.tarnishedrelics.network.TRNetwork;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The parts of the editor's form that aren't a file's own fields: the line about what a file does in practice
 * (with its Show / Give buttons), the Settings tab's intro and the Mods tab. Split out of {@link EditorScreen}.
 */
final class EditorSections {

    private EditorSections() {
    }

    /** A line about what the file does in practice (how many items it matches...), with a button to see them. */
    static void info(EditorScreen screen, EditorForm form, String folder, ResourceLocation selected, int width) {
        TRData data = screen.model().preview();
        ResourceLocation shortId = EditorModel.shortId(selected);
        String text = null;
        List<Item> items = null;
        switch (folder) {
            case "categories" -> {
                Category c = data.categories().get(shortId);
                if (c != null) {
                    items = itemsWhere(i -> data.categoryOf(i) == c);
                    text = "Matches " + items.size() + " item(s) from the loaded mods";
                }
            }
            case "rarities" -> {
                Rarity r = data.rarities().get(shortId);
                if (r != null) {
                    items = new ArrayList<>();
                    for (Item i : r.items().resolve()) if (data.categoryOf(i) != null) items.add(i);
                    text = "Can be " + items.size() + " base item(s)";
                }
            }
            case "exclusions" -> {
                items = itemsWhere(i -> data.exclusions().contains(i));
                text = "All exclusion files together keep " + items.size() + " item(s) from being relics";
            }
            case "legendaries" -> {
                var l = data.legendaries().get(shortId);
                if (l != null) {
                    items = new ArrayList<>(l.items().resolve());
                    text = items.size() + " possible base item(s)" + (l.weight() <= 0 ? " · only drops with its set" : "");
                }
            }
            case "affixes" -> {
                var a = data.affixes().get(shortId);
                if (a != null) {
                    long n = data.categories().keySet().stream().filter(a::appliesTo).count();
                    text = "Can roll on " + n + " categor" + (n == 1 ? "y" : "ies");
                }
            }
            default -> { }
        }
        // a short explanation for the folders whose rules aren't obvious from the fields
        String explain = switch (folder) {
            case "levels" -> "Item level = the highest of its tool tier, armor and a material word in its id (keywords). \"items\" here overrides that. Rarities use levels to pick base items.";
            case "rarities" -> "\"*\" = every item with a category, filtered by \"levels\". Items named directly always count. \"legendary\": generated legendaries (plus hand-made ones from the Legendary tab, by handmade_chance).";
            case "legendaries" -> "A hand-made relic: always this name, lore, enchantments and effects; each copy gets one roll for the value ranges. Legendary rarities drop these by their handmade_chance, generated ones otherwise.";
            case "tags" -> tagHelp(shortId.getPath());
            default -> null;
        };
        if (explain != null) {
            FormRow r = form.row(0, "", null);
            r.wideLabel = true;
            r.labelColor = 0x909090;
            r.label = Component.literal(explain);
            r.help = Component.literal(explain);
        }
        if (text == null) return;
        FormRow r = form.row(0, "", null);
        r.wideLabel = true;
        r.labelColor = 0x90C0FF;
        r.label = Component.literal(text);
        int right = width;
        if (items != null) {
            List<Item> shown = items;
            right -= 52;
            form.place(r, Button.builder(EditorForm.t("show", "Show"), b -> screen.open(new ItemsScreen(screen, Component.literal(selected.toString()), screen.model(),
                    () -> shown))).bounds(0, 0, 50, 18).build(), right);
        }
        // test rolls (from the saved data, which is what the server has)
        if (folder.equals("rarities") || folder.equals("categories") || folder.equals("legendaries")) {
            String rarity = folder.equals("rarities") ? shortId.toString() : "";
            String category = folder.equals("categories") ? shortId.toString() : "";
            String legendary = folder.equals("legendaries") ? shortId.toString() : "";
            Tooltip tip = Tooltip.create(EditorForm.t("give.tip", "Rolls one from the saved data (save first to try your changes)"));
            right -= 72;
            form.place(r, Button.builder(EditorForm.t("give_id", "Identified"), b -> TRNetwork.CHANNEL.sendToServer(new TRNetwork.EditorGive(rarity, category, legendary, true)))
                    .bounds(0, 0, 70, 18).tooltip(tip).build(), right);
            right -= 52;
            form.place(r, Button.builder(EditorForm.t("give", "Give"), b -> TRNetwork.CHANNEL.sendToServer(new TRNetwork.EditorGive(rarity, category, legendary, false)))
                    .bounds(0, 0, 50, 18).tooltip(tip).build(), right);
        }
    }

    private static String tagHelp(String path) {
        if (path.equals("villager_profession/blacksmiths")) return "Villager professions that identify relics of categories without \"smiths\" (add a mod's smith here).";
        if (path.startsWith("villager_profession/")) return "Villager professions that identify the categories whose \"smiths\" name #tarnishedrelics:" + path.substring(20) + " (add a mod's smith here).";
        if (path.startsWith("entity_types/")) return "Mobs in this tag; affixes target it as #tarnishedrelics:" + path.substring(13) + ".";
        return "Tags from every pack add up; this list is all of them together. Taking out one another pack adds makes it replace the tag.";
    }

    static void settings(EditorForm form, EditorModel model) {
        JsonObject json = model.get(EditorServer.CONFIG_FILE);
        FormRow intro = form.row(0, "", null);
        intro.wideLabel = true;
        intro.labelColor = 0x90C0FF;
        intro.label = EditorForm.t("settings.intro", "The config file (config/tarnishedrelics-common.toml): the same for every world. Saving writes it right away.");
        intro.help = intro.label;
        if (json == null) return;
        form.addFields(json, Schema.CONFIG, 0, List.of());
    }

    private static List<Item> itemsWhere(Predicate<Item> test) {
        List<Item> out = new ArrayList<>();
        for (Item i : ForgeRegistries.ITEMS) if (test.test(i)) out.add(i);
        return out;
    }

    /** The Mods tab: every loaded mod with items that can be relics, with a switch to exclude it. */
    static void mods(EditorScreen screen, EditorForm form, int width) {
        TRData data = screen.model().preview();
        FormRow intro = form.row(0, "", null);
        intro.wideLabel = true;
        intro.labelColor = 0x90C0FF;
        intro.label = EditorForm.t("mods.intro", "Loaded mods with items that can be relics");
        intro.help = EditorForm.t("mods.intro.tip", "Exclude a whole mod here, or open its items to exclude single ones. Exclusions go in tarnishedrelics:exclusions/editor.");
        Map<String, List<Item>> byMod = Lookup.candidatesByMod(data);
        for (Map.Entry<String, List<Item>> e : byMod.entrySet()) {
            String mod = e.getKey();
            List<Item> items = e.getValue();
            long relics = items.stream().filter(i -> data.categoryOf(i) != null).count();
            boolean excluded = screen.model().excludedEntry(mod + ":*");
            FormRow r = form.row(0, "", null);
            r.wideLabel = true;
            r.label = Component.literal(Lookup.modName(mod)).append(Component.literal("  " + mod).withStyle(s -> s.withColor(0x808080)));
            r.labelColor = excluded ? 0x909090 : EditorForm.TEXT;
            // per-category counts in the tooltip
            Map<String, Integer> perCat = new LinkedHashMap<>();
            for (Item i : items) {
                Category c = data.categoryOf(i);
                perCat.merge(c == null ? "excluded" : c.id().getPath(), 1, Integer::sum);
            }
            StringBuilder tip = new StringBuilder();
            perCat.forEach((k, n) -> tip.append(k).append(": ").append(n).append("\n"));
            r.help = Component.literal(tip.toString().trim());
            Button count = Button.builder(Component.literal(relics + " / " + items.size()), b -> { })
                    .bounds(0, 0, 60, 18).tooltip(Tooltip.create(EditorForm.t("mods.count", "Relics now / items that fit a category"))).build();
            count.active = false;
            form.place(r, count, width - 60 - 4 - 60 - 4 - 80);
            form.place(r, Button.builder(EditorForm.t("mods.items", "Items"), b -> screen.open(new ItemsScreen(screen, Component.literal(Lookup.modName(mod)), screen.model(),
                    () -> items))).bounds(0, 0, 60, 18).build(), width - 60 - 4 - 80);
            form.place(r, Button.builder(excluded ? EditorForm.t("mods.excluded", "Excluded") : EditorForm.t("mods.included", "Included"), b -> {
                screen.model().setExcluded(mod + ":*", !excluded);
                screen.restructure();
            }).bounds(0, 0, 80, 18).tooltip(Tooltip.create(EditorForm.t("mods.toggle", "Excluded: nothing from this mod is ever a relic"))).build(), width - 80);
        }
    }
}
