package com.flankrat.tarnishedrelics.client.editor;

import com.flankrat.tarnishedrelics.client.editor.Schema.Field;
import com.flankrat.tarnishedrelics.client.editor.Schema.Kind;
import com.flankrat.tarnishedrelics.client.editor.Schema.Pick;
import com.flankrat.tarnishedrelics.data.Category;
import com.flankrat.tarnishedrelics.data.Rarity;
import com.flankrat.tarnishedrelics.data.TRData;
import com.flankrat.tarnishedrelics.editor.EditorServer;
import com.flankrat.tarnishedrelics.network.TRNetwork;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The in-game editor ({@code /tarnishedrelics editor}). Tabs along the top pick a folder (categories, rarities,
 * affixes, names, levels, exclusions) or the Mods overview; the left side lists that folder's files, the right side
 * edits the selected file as a form built from {@link Schema} (or as raw JSON). Everything happens on a working
 * copy ({@link EditorModel}); "Save & Reload" sends the changed files to the server, which checks them, writes them
 * into the world's editor datapack and reloads.
 * <p>
 * The form is a list of rows (a name on the left, controls on the right) that scrolls; any change that adds or
 * removes rows rebuilds the screen, plain value edits don't (so typing keeps focus).
 */
public class EditorScreen extends Screen {

    /** The open editor, for packets that arrive while a picker is on top of it. */
    @Nullable static EditorScreen last;

    private static final List<String> TABS = List.of("categories", "rarities", "affixes", "legendaries", "names", "levels", "exclusions", "tags", "settings", "mods");
    /** Where Save writes: the world's editor pack, or the modpack folder every world loads. Kept between openings. */
    private static boolean saveToModpack;
    private static final int ROW = 22;
    private static final int LABEL_W = 100;
    private static final int RED = 0xFF6060;
    private static final int TEXT = 0xE0E0E0;

    private EditorModel model;
    private String folder = "categories";
    @Nullable private ResourceLocation selected;
    private boolean raw;
    @Nullable private String rawError;
    private String fileSearch = "";
    private double scroll;
    private double fileListScroll;
    @Nullable private String newName;
    @Nullable private Component status;
    private int statusColor = TEXT;
    private List<String> statusDetails = List.of();
    private boolean saving;

    // layout, worked out in init()
    private int leftW, formX, formTop, formW, formBottom;
    private final List<Row> rows = new ArrayList<>();
    private int contentHeight;
    private FileList fileList;

    public EditorScreen(EditorModel model) {
        super(Component.translatableWithFallback("tarnishedrelics.editor.title", "Tarnished Relics Editor"));
        this.model = model;
        last = this;
    }

    private static Component t(String key, String english, Object... args) {
        return Component.translatableWithFallback("tarnishedrelics.editor." + key, english, args);
    }

    // ---- packets ---------------------------------------------------------------------------------------------

    void replaceModel(EditorModel fresh) {
        model = fresh;
        if (selected != null && !model.exists(selected)) selected = null;
        rawError = null;
        if (minecraft != null && minecraft.screen == this) rebuildWidgets();
    }

    void saveResult(boolean saved, List<String> errors) {
        saving = false;
        if (saved) {
            setStatus(errors.isEmpty() ? t("saved", "Saved and reloaded.") : t("saved_errors", "Saved. %s file(s) elsewhere still have errors.", errors.size()),
                    errors.isEmpty() ? 0x60FF60 : 0xFFD060, errors);
        } else {
            setStatus(t("not_saved", "Not saved: fix these first."), RED, errors);
        }
    }

    private void setStatus(Component text, int color, List<String> details) {
        status = text;
        statusColor = color;
        statusDetails = details;
    }

    // ---- layout ----------------------------------------------------------------------------------------------

    @Override
    protected void init() {
        last = this;
        rows.clear();
        leftW = Math.max(110, Math.min(170, width / 4));
        boolean mods = fullWidth();
        formX = mods ? 8 : leftW + 14;
        formW = width - formX - 8;
        formTop = mods ? 46 : 92;
        formBottom = height - 30;

        // folder tabs
        int tabW = (width - 16) / TABS.size();
        for (int i = 0; i < TABS.size(); i++) {
            String tab = TABS.get(i);
            // "Legendaries" doesn't fit a tab at small widths
            String label = tab.equals("legendaries") ? "Legendary" : cap(tab);
            Button b = Button.builder(t("tab." + tab, label), x -> {
                if (!folder.equals(tab)) {
                    folder = tab;
                    selected = null;
                    raw = false;
                    scroll = 0;
                    newName = null;
                    fileList = null;   // so the old tab's list scroll isn't carried over
                    fileListScroll = 0;
                    rebuildWidgets();
                }
            }).bounds(8 + i * tabW, 18, tabW - 2, 18).build();
            b.active = !folder.equals(tab);
            addRenderableWidget(b);
        }

        if (!mods) initFileSide();
        initFooter();
        buildForm();
        layoutForm();
    }

    /** Tabs without a file list. */
    private boolean fullWidth() {
        return folder.equals("mods") || folder.equals("settings");
    }

    private static String cap(String s) {
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    private void initFileSide() {
        EditBox search = new EditBox(font, 8, 42, leftW, 16, Component.literal("search"));
        search.setMaxLength(100);
        search.setValue(fileSearch);
        search.setHint(t("search", "Search..."));
        search.setResponder(s -> {
            fileSearch = s;
            fillFileList();
        });
        addRenderableWidget(search);

        fileList = new FileList(minecraft, leftW, 62, height - 56);
        addRenderableWidget(fileList);
        fillFileList();
        fileList.setScrollAmount(fileListScroll);

        if (newName == null) {
            addRenderableWidget(Button.builder(t("new", "+ New"), b -> {
                newName = "";
                rebuildWidgets();
            }).bounds(8, height - 52, leftW / 2 - 1, 18).tooltip(Tooltip.create(t("new.tip", "Make a new file in this folder"))).build());
            Button copy = Button.builder(t("copy", "Copy"), b -> {
                newName = selected == null ? "" : EditorModel.shortId(selected).getPath() + "_copy";
                rebuildWidgets();
            }).bounds(8 + leftW / 2 + 1, height - 52, leftW / 2 - 1, 18).tooltip(Tooltip.create(t("copy.tip", "New file starting as a copy of the selected one"))).build();
            copy.active = selected != null && model.get(selected) != null;
            addRenderableWidget(copy);
        } else {
            EditBox name = new EditBox(font, 8, height - 52, leftW - 42, 18, Component.literal("name"));
            name.setMaxLength(100);
            name.setValue(newName);
            name.setHint(t("new.name", "file name"));
            name.setResponder(s -> newName = s);
            addRenderableWidget(name);
            setInitialFocus(name);
            addRenderableWidget(Button.builder(Component.literal("✔"), b -> createFile())
                    .bounds(8 + leftW - 40, height - 52, 19, 18).tooltip(Tooltip.create(t("create", "Create"))).build());
            addRenderableWidget(Button.builder(Component.literal("✖"), b -> {
                newName = null;
                rebuildWidgets();
            }).bounds(8 + leftW - 19, height - 52, 19, 18).build());
        }

        if (selected == null) return;
        // file buttons above the form
        int x = formX;
        JsonObject json = model.get(selected);
        if (json != null) {
            addRenderableWidget(Button.builder(raw ? t("form", "Form") : t("raw", "Raw JSON"), b -> {
                if (raw && rawError != null) return;   // fix the JSON before going back to the form
                raw = !raw;
                rebuildWidgets();
            }).bounds(x, 54, 70, 18).tooltip(Tooltip.create(t("raw.tip", "Edit the file's JSON text directly"))).build());
            x += 72;
        }
        Button revert = Button.builder(t("revert", "Revert"), b -> {
            model.revert(selected);
            rawError = null;
            rebuildWidgets();
        }).bounds(x, 54, 56, 18).tooltip(Tooltip.create(t("revert.tip", "Throw away unsaved changes to this file"))).build();
        revert.active = model.isChanged(selected) || model.isRemoved(selected);
        addRenderableWidget(revert);
        x += 58;
        if (json == null) return;
        if (model.editorOnly(selected)) {
            addRenderableWidget(Button.builder(t("delete", "Delete"), b -> {
                model.remove(selected);
                if (!model.isRemoved(selected)) selected = null;
                rebuildWidgets();
            }).bounds(x, 54, 56, 18).tooltip(Tooltip.create(t("delete.tip", "Delete this file (on save)"))).build());
            x += 58;
        } else if (model.overridesAnother(selected)) {
            addRenderableWidget(Button.builder(t("use_original", "Use original"), b -> {
                model.remove(selected);
                rebuildWidgets();
            }).bounds(x, 54, 80, 18).tooltip(Tooltip.create(t("use_original.tip", "Drop the editor's copy so the version from %s is used again (on save)",
                    packName(model.origins(selected).get(model.origins(selected).size() - 2))))).build());
            x += 82;
        }
        if (EditorServer.isTag(selected)) return;   // tags have no "disabled" switch
        boolean off = model.isDisabled(selected);
        addRenderableWidget(Button.builder(off ? t("enable", "Turn on") : t("disable", "Turn off"), b -> {
            JsonObject o = model.get(selected);
            if (off) o.remove("disabled");
            else o.addProperty("disabled", true);
            model.edited();
            rebuildWidgets();
        }).bounds(x, 54, 60, 18).tooltip(Tooltip.create(off ? t("enable.tip", "Use this file again")
                : t("disable.tip", "Switch the file off without deleting it (works for files from mods and other packs too)"))).build());
    }

    private void initFooter() {
        addRenderableWidget(CycleButton.<Boolean>builder(v -> v ? t("target.modpack", "Save to: whole modpack") : t("target.world", "Save to: this world"))
                .withValues(false, true).withInitialValue(saveToModpack).displayOnlyValue()
                .withTooltip(v -> Tooltip.create(v
                        ? t("target.modpack.tip", "config/tarnishedrelics/datapack: every world loads it, so the modpack ships it with its config folder. Replaces this world's copy of the same files.")
                        : t("target.world.tip", "This world's datapacks/tarnishedrelics_editor folder: only this world.")))
                .create(width - 8 - 190 - 152, height - 24, 150, 20, Component.literal("target"), (b, v) -> saveToModpack = v));
        Button save = Button.builder(saving ? t("saving", "Saving...") : t("save", "Save & Reload"), b -> save())
                .bounds(width - 8 - 190, height - 24, 110, 20)
                .tooltip(Tooltip.create(t("save.tip", "Check every change, write it to the world's editor datapack and reload (Ctrl+S)"))).build();
        save.active = !saving && model.hasChanges();
        addRenderableWidget(save);
        addRenderableWidget(Button.builder(t("close", "Close"), b -> onClose()).bounds(width - 8 - 76, height - 24, 76, 20).build());
    }

    private void fillFileList() {
        if (fileList == null) return;
        fileList.clear();
        String q = fileSearch.toLowerCase(Locale.ROOT);
        List<ResourceLocation> ids = new ArrayList<>(model.filesIn(folder));
        for (ResourceLocation id : model.changes().keySet()) if (!ids.contains(id) && EditorModel.folderOf(id).equals(folder)) ids.add(id);
        for (ResourceLocation id : ids) {
            if (!q.isEmpty() && !id.toString().contains(q)) continue;
            fileList.add(id);
        }
    }

    private void createFile() {
        String name = newName == null ? "" : newName.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (!name.matches("[a-z0-9_./-]+")) {
            setStatus(t("bad_name", "Use only a-z, 0-9, _ - . / in file names."), RED, List.of());
            return;
        }
        if (folder.equals("tags") && name.split("/").length < 2) {
            setStatus(t("tag_name", "Name tags like entity_types/dragons, items/..., blocks/... or villager_profession/smiths."), RED, List.of());
            return;
        }
        ResourceLocation id = new ResourceLocation(EditorModel.NAMESPACE, folder + "/" + name);
        if (model.exists(id)) {
            setStatus(t("exists", "There already is a file called %s.", name), RED, List.of());
            return;
        }
        JsonObject start = selected != null && model.get(selected) != null && name.endsWith("_copy")
                ? model.get(selected).deepCopy() : Schema.template(folder);
        start.remove("disabled");
        // a readable name from the file name, so it never shows a raw lang key
        String title = com.flankrat.tarnishedrelics.name.NameFallback.title(name);
        switch (folder) {
            case "categories" -> start.addProperty("name", "Unidentified " + title);
            case "rarities" -> start.addProperty("name", title);
            case "affixes" -> start.addProperty("name", title);
            default -> { }
        }
        model.put(id, start);
        selected = id;
        newName = null;
        raw = false;
        scroll = 0;
        rebuildWidgets();
    }

    private void save() {
        if (saving || !model.hasChanges()) return;
        // the server checks too, but catching errors here saves a round trip
        List<String> errs = new ArrayList<>();
        for (ResourceLocation id : model.changes().keySet()) for (String e : model.errorsFor(id)) errs.add(id + ": " + e);
        if (rawError != null) errs.add(selected + ": " + rawError);
        if (!errs.isEmpty()) {
            setStatus(t("fix_first", "Fix the errors first."), RED, errs);
            return;
        }
        List<String> tooBig = EditorClient.save(model, saveToModpack);
        if (!tooBig.isEmpty()) {
            setStatus(t("too_big", "A file is too big to send."), RED, tooBig);
            return;
        }
        saving = true;
        setStatus(t("saving", "Saving..."), TEXT, List.of());
        rebuildWidgets();
    }

    @Override
    public void onClose() {
        if (model.hasChanges()) {
            minecraft.setScreen(new ConfirmScreen(yes -> {
                if (yes) close();
                else minecraft.setScreen(this);
            }, t("discard", "Close without saving?"), t("discard.msg", "Your unsaved changes will be lost.")));
            return;
        }
        close();
    }

    private void close() {
        TRNetwork.CHANNEL.sendToServer(new TRNetwork.EditorCommit(false, false));
        last = null;
        minecraft.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 83 && hasControlDown()) {   // Ctrl+S
            save();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    // ---- the form --------------------------------------------------------------------------------------------

    /** One line of the form: a name (with help on hover) and its controls at x offsets from the form's left. */
    private static final class Row {
        int y;
        int indent;
        Component label = Component.empty();
        @Nullable Component help;
        int labelColor = TEXT;
        boolean wideLabel;
        final List<AbstractWidget> widgets = new ArrayList<>();
        final List<Integer> offsets = new ArrayList<>();
        int swatch = -1, swatchX;   // a color box (color fields)
        int height = ROW;
    }

    private Row row(int indent, String label, @Nullable String help) {
        Row r = new Row();
        r.y = contentHeight;
        r.indent = indent;
        r.label = Component.literal(label);
        r.help = help == null || help.isEmpty() ? null : Component.literal(help);
        rows.add(r);
        contentHeight += ROW;
        return r;
    }

    private <T extends AbstractWidget> T place(Row r, T widget, int offset) {
        r.widgets.add(widget);
        r.offsets.add(offset);
        addRenderableWidget(widget);
        return widget;
    }

    /** Where a row's controls start, and how wide they can be. */
    private int valueX(int indent) {
        return indent + LABEL_W;
    }

    private int valueW(int indent) {
        return formW - valueX(indent) - 2;
    }

    private void layoutForm() {
        for (Row r : rows) {
            int y = formTop + r.y - (int) scroll;
            boolean inside = y >= formTop - 1 && y + r.height - 2 <= formBottom;
            for (int i = 0; i < r.widgets.size(); i++) {
                AbstractWidget w = r.widgets.get(i);
                w.setX(formX + r.offsets.get(i));
                w.setY(y + (r.height == ROW ? 1 : 0));
                w.visible = inside;
            }
        }
    }

    private void buildForm() {
        contentHeight = 4;
        if (folder.equals("mods")) {
            buildMods();
        } else if (folder.equals("settings")) {
            buildSettings();
        } else if (selected != null) {
            JsonObject json = model.get(selected);
            if (json == null) {
                Row r = row(0, "", null);
                r.wideLabel = true;
                r.label = model.overridesAnother(selected)
                        ? t("removed.original", "On save, the editor's copy goes and the version from %s is used again.", packName(model.origins(selected).get(model.origins(selected).size() - 2)))
                        : t("removed", "This file will be deleted when you save.");
            } else if (raw) {
                buildRaw(json);
            } else {
                buildInfo(json);
                if (model.isDisabled(selected)) {
                    Row r = row(0, "", null);
                    r.wideLabel = true;
                    r.labelColor = 0xFFD060;
                    r.label = t("off", "This file is turned off: the game ignores it.");
                }
                List<Field> fields = folder.equals("tags")
                        ? Schema.tagFields(EditorModel.shortId(selected).getPath().split("/")[0])
                        : Schema.FILES.getOrDefault(folder, List.of());
                addFields(json, fields, 0, folder.equals("tags") ? List.of("replace") : List.of("disabled"));
            }
        }
        contentHeight += 6;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - (formBottom - formTop))));
    }

    private void buildRaw(JsonObject json) {
        MultiLineEditBox box = new MultiLineEditBox(font, formX, formTop, formW, formBottom - formTop - 14,
                Component.empty(), Component.literal("json"));
        box.setCharacterLimit(32000);
        box.setValue(new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(json));
        box.setValueListener(text -> {
            try {
                JsonElement e = JsonParser.parseString(text);
                if (!e.isJsonObject()) throw new IllegalStateException("the file must be a JSON object { ... }");
                model.put(selected, e.getAsJsonObject());
                rawError = null;
            } catch (RuntimeException ex) {
                rawError = ex.getMessage() == null ? "invalid JSON" : ex.getMessage();
            }
        });
        addRenderableWidget(box);
    }

    /** A line about what the file does in practice (how many items it matches...), with a button to see them. */
    private void buildInfo(JsonObject json) {
        TRData data = model.preview();
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
            Row r = row(0, "", null);
            r.wideLabel = true;
            r.labelColor = 0x909090;
            r.label = Component.literal(explain);
            r.help = Component.literal(explain);
        }
        if (text == null) return;
        Row r = row(0, "", null);
        r.wideLabel = true;
        r.labelColor = 0x90C0FF;
        r.label = Component.literal(text);
        int right = formW;
        if (items != null) {
            List<Item> shown = items;
            right -= 52;
            place(r, Button.builder(t("show", "Show"), b -> minecraft.setScreen(new ItemsScreen(this, Component.literal(selected.toString()), model,
                    () -> shown))).bounds(0, 0, 50, 18).build(), right);
        }
        // test rolls (from the saved data, which is what the server has)
        if (folder.equals("rarities") || folder.equals("categories") || folder.equals("legendaries")) {
            String rarity = folder.equals("rarities") ? shortId.toString() : "";
            String category = folder.equals("categories") ? shortId.toString() : "";
            String legendary = folder.equals("legendaries") ? shortId.toString() : "";
            Tooltip tip = Tooltip.create(t("give.tip", "Rolls one from the saved data (save first to try your changes)"));
            right -= 72;
            place(r, Button.builder(t("give_id", "Identified"), b -> TRNetwork.CHANNEL.sendToServer(new TRNetwork.EditorGive(rarity, category, legendary, true)))
                    .bounds(0, 0, 70, 18).tooltip(tip).build(), right);
            right -= 52;
            place(r, Button.builder(t("give", "Give"), b -> TRNetwork.CHANNEL.sendToServer(new TRNetwork.EditorGive(rarity, category, legendary, false)))
                    .bounds(0, 0, 50, 18).tooltip(tip).build(), right);
        }
    }

    private static String tagHelp(String path) {
        if (path.equals("villager_profession/blacksmiths")) return "Villager professions that can identify relics (add a mod's smith here).";
        if (path.startsWith("entity_types/")) return "Mobs in this tag; affixes target it as #tarnishedrelics:" + path.substring(13) + ".";
        return "Tags from every pack add up; this list is all of them together. Taking out one another pack adds makes it replace the tag.";
    }

    private void buildSettings() {
        JsonObject json = model.get(EditorServer.CONFIG_FILE);
        Row intro = row(0, "", null);
        intro.wideLabel = true;
        intro.labelColor = 0x90C0FF;
        intro.label = t("settings.intro", "The config file (config/tarnishedrelics-common.toml): the same for every world. Saving writes it right away.");
        intro.help = intro.label;
        if (json == null) return;
        addFields(json, Schema.CONFIG, 0, List.of());
    }

    private static List<Item> itemsWhere(java.util.function.Predicate<Item> test) {
        List<Item> out = new ArrayList<>();
        for (Item i : ForgeRegistries.ITEMS) if (test.test(i)) out.add(i);
        return out;
    }

    /** Rows for an object's fields: the ones it has, unknown ones as raw JSON, then an "add field" row. */
    private void addFields(JsonObject obj, List<Field> fields, int indent, List<String> skip) {
        List<String> known = new ArrayList<>(skip);
        for (Field f : fields) {
            known.add(f.key());
            if (obj.has(f.key())) addField(obj, f, indent);
        }
        for (String key : new ArrayList<>(obj.keySet())) if (!known.contains(key)) rawField(obj, key, indent);
        addFieldRow(obj, fields.stream().filter(f -> !obj.has(f.key())).toList(), indent);
    }

    private void addFieldRow(JsonObject obj, List<Field> absent, int indent) {
        if (absent.isEmpty()) return;
        Row r = row(indent, "", null);
        r.labelColor = 0x909090;
        r.label = t("add_field", "+ field");
        Field[] pick = {absent.get(0)};
        int w = Math.min(160, valueW(indent) - 50);
        place(r, CycleButton.<Field>builder(f -> Component.literal(f.key())).withValues(absent).withInitialValue(absent.get(0))
                .withTooltip(f -> Tooltip.create(Component.literal(f.help())))
                .displayOnlyValue().create(0, 0, w, 18, Component.literal("field"), (b, v) -> pick[0] = v), valueX(indent));
        place(r, Button.builder(t("add", "Add"), b -> {
            obj.add(pick[0].key(), pick[0].def().get());
            restructure();
        }).bounds(0, 0, 46, 18).build(), valueX(indent) + w + 2);
    }

    private void addField(JsonObject obj, Field f, int indent) {
        String key = f.key();
        JsonElement v = obj.get(key);
        switch (f.kind()) {
            case TEXT -> {
                Row r = row(indent, key, f.help());
                int w = valueW(indent) - 22 - (f.pick() != Pick.NONE ? 22 : 0);
                EditBox box = text(v.isJsonPrimitive() ? v.getAsString() : v.toString(), w, s -> obj.addProperty(key, s));
                if (f.pick() != Pick.NONE) {
                    checkBox(box, f.pick(), box.getValue());
                    box.setResponder(s -> {
                        obj.addProperty(key, s);
                        checkBox(box, f.pick(), s);
                        changed();
                    });
                }
                place(r, box, valueX(indent));
                if (f.pick() != Pick.NONE) place(r, pickButton(f.pick(), val -> {
                    obj.addProperty(key, val);
                    restructure();
                }), valueX(indent) + w + 2);
                removeField(r, obj, key);
            }
            case NUMBER, INT -> {
                Row r = row(indent, key, f.help());
                place(r, number(v, f.kind() == Kind.INT, n -> obj.add(key, n)), valueX(indent));
                removeField(r, obj, key);
            }
            case BOOL -> {
                Row r = row(indent, key, f.help());
                place(r, CycleButton.onOffBuilder(v.isJsonPrimitive() && v.getAsBoolean()).displayOnlyValue()
                        .create(0, 0, 60, 18, Component.literal(key), (b, val) -> {
                            obj.addProperty(key, val);
                            changed();
                        }), valueX(indent));
                removeField(r, obj, key);
            }
            case ENUM -> {
                Row r = row(indent, key, f.help());
                List<String> opts = new ArrayList<>(f.options());
                String cur = v.isJsonPrimitive() ? v.getAsString() : "";
                if (!opts.contains(cur)) opts.add(0, cur);
                place(r, CycleButton.<String>builder(Component::literal).withValues(opts).withInitialValue(cur).displayOnlyValue()
                        .create(0, 0, Math.min(140, valueW(indent) - 24), 18, Component.literal(key), (b, val) -> {
                            obj.addProperty(key, val);
                            changed();
                        }), valueX(indent));
                removeField(r, obj, key);
            }
            case COLOR -> {
                Row r = row(indent, key, f.help());
                EditBox box = text(v.isJsonPrimitive() ? v.getAsString() : "#FFFFFF", 70, s -> obj.addProperty(key, s));
                place(r, box, valueX(indent));
                r.swatchX = valueX(indent) + 74;
                box.setResponder(s -> {
                    obj.addProperty(key, s);
                    r.swatch = parseColor(s);
                    box.setTextColor(r.swatch < 0 ? RED : TEXT);
                    changed();
                });
                r.swatch = parseColor(box.getValue());
                removeField(r, obj, key);
            }
            case RANGE -> {
                Row r = row(indent, key, f.help());
                rangeBoxes(r, valueX(indent), v, val -> obj.add(key, val));
                removeField(r, obj, key);
            }
            case LIST -> listField(obj, f, indent);
            case MAP_INT, MAP_RANGE -> mapField(obj, f, indent);
            case WEIGHT -> weightField(obj, f, indent);
            case VALUE -> valueField(obj, f, indent);
            case CONDITIONS -> {
                Row r = row(indent, key, f.help());
                removeField(r, obj, key);
                if (!v.isJsonObject()) {
                    obj.add(key, new JsonObject());
                    v = obj.get(key);
                }
                addFields(v.getAsJsonObject(), Schema.CONDITIONS, indent + 12, List.of());
            }
            case EFFECTS -> effectsField(obj, f, indent);
        }
    }

    /** A field the schema doesn't know (or an odd shape): edited as one line of JSON. */
    private void rawField(JsonObject obj, String key, int indent) {
        Row r = row(indent, key, "Edited as raw JSON");
        r.labelColor = 0xC0A0FF;
        EditBox box = text(obj.get(key).toString(), valueW(indent) - 22, s -> { });
        box.setResponder(s -> {
            try {
                obj.add(key, JsonParser.parseString(s));
                box.setTextColor(TEXT);
                changed();
            } catch (RuntimeException e) {
                box.setTextColor(RED);
            }
        });
        place(r, box, valueX(indent));
        removeField(r, obj, key);
    }

    // ---- lists and maps --------------------------------------------------------------------------------------

    /** A string list. A single string in the file counts as a one-entry list; edits always write an array. */
    private void listField(JsonObject obj, Field f, int indent) {
        String key = f.key();
        JsonArray arr = asArray(obj.get(key));
        Row head = row(indent, key, f.help());
        place(head, Button.builder(t("add_entry", "+ Add"), b -> {
            if (f.pick() != Pick.NONE) {
                minecraft.setScreen(new PickerScreen(this, f.pick(), model, val -> {
                    arr.add(val);
                    obj.add(key, arr);
                    restructure();
                }));
            } else {
                arr.add("");
                obj.add(key, arr);
                restructure();
            }
        }).bounds(0, 0, 50, 18).build(), valueX(indent));
        if (arr.isEmpty()) {
            head.labelColor = TEXT;
        }
        removeField(head, obj, key);
        for (int i = 0; i < arr.size(); i++) {
            int index = i;
            Row r = row(indent + 12, "", null);
            int w = valueW(indent) - 22 - (f.pick() != Pick.NONE ? 22 : 0);
            EditBox box = text(arr.get(i).isJsonPrimitive() ? arr.get(i).getAsString() : arr.get(i).toString(), w, s -> { });
            if (f.pick() != Pick.NONE) checkBox(box, f.pick(), box.getValue());
            box.setResponder(s -> {
                arr.set(index, new JsonPrimitive(s));
                obj.add(key, arr);
                if (f.pick() != Pick.NONE) checkBox(box, f.pick(), s);
                changed();
            });
            place(r, box, valueX(indent));
            if (f.pick() != Pick.NONE) place(r, pickButton(f.pick(), val -> {
                arr.set(index, new JsonPrimitive(val));
                obj.add(key, arr);
                restructure();
            }), valueX(indent) + w + 2);
            place(r, removeButton(() -> {
                arr.remove(index);
                obj.add(key, arr);
            }), formW - 22);
        }
    }

    private static JsonArray asArray(@Nullable JsonElement e) {
        if (e == null || e.isJsonNull()) return new JsonArray();
        if (e.isJsonArray()) return e.getAsJsonArray();
        JsonArray a = new JsonArray();
        a.add(e);
        return a;
    }

    /** { key: int } or { key: [min, max] }. Keys can be renamed in place (order is kept). */
    private void mapField(JsonObject obj, Field f, int indent) {
        String key = f.key();
        if (!obj.get(key).isJsonObject()) obj.add(key, new JsonObject());
        JsonObject map = obj.getAsJsonObject(key);
        Row head = row(indent, key, f.help());
        place(head, Button.builder(t("add_entry", "+ Add"), b -> {
            Consumer<String> add = k -> {
                map.add(uniqueKey(map, k), f.kind() == Kind.MAP_INT ? new JsonPrimitive(1) : rangeJson(1, 2));
                restructure();
            };
            if (f.pick() != Pick.NONE) minecraft.setScreen(new PickerScreen(this, f.pick(), model, add));
            else add.accept("new");
        }).bounds(0, 0, 50, 18).build(), valueX(indent));
        removeField(head, obj, key);
        mapEntries(map, f.pick(), f.kind() == Kind.MAP_RANGE, indent + 12);
    }

    private void mapEntries(JsonObject map, Pick keyPick, boolean ranges, int indent) {
        for (String k : new ArrayList<>(map.keySet())) {
            String[] keyRef = {k};
            Row r = row(indent, "", null);
            int keyW = Math.max(60, valueW(indent) - 22 - (ranges ? 108 : 64) - (keyPick != Pick.NONE ? 22 : 0) + LABEL_W - 12);
            int x = valueX(indent) - LABEL_W + 12;
            EditBox keyBox = text(k, keyW, s -> { });
            if (keyPick != Pick.NONE) checkBox(keyBox, keyPick, k);
            keyBox.setResponder(s -> {
                if (s.isEmpty() || (map.has(s) && !s.equals(keyRef[0]))) {
                    keyBox.setTextColor(RED);
                    return;
                }
                renameKey(map, keyRef[0], s);
                keyRef[0] = s;
                keyBox.setTextColor(TEXT);
                if (keyPick != Pick.NONE) checkBox(keyBox, keyPick, s);
                changed();
            });
            place(r, keyBox, x);
            x += keyW + 2;
            if (keyPick != Pick.NONE) {
                place(r, pickButton(keyPick, val -> {
                    if (!map.has(val)) renameKey(map, keyRef[0], val);
                    restructure();
                }), x);
                x += 22;
            }
            if (ranges) rangeBoxes(r, x, map.get(k), val -> map.add(keyRef[0], val));
            else place(r, number(map.get(k), true, n -> map.add(keyRef[0], n)), x);
            place(r, removeButton(() -> map.remove(keyRef[0])), formW - 22);
        }
    }

    private static String uniqueKey(JsonObject map, String k) {
        String out = k;
        for (int i = 2; map.has(out); i++) out = k + i;
        return out;
    }

    private static void renameKey(JsonObject map, String from, String to) {
        if (from.equals(to)) return;
        Map<String, JsonElement> copy = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : map.entrySet()) copy.put(e.getKey().equals(from) ? to : e.getKey(), e.getValue());
        for (String k : new ArrayList<>(map.keySet())) map.remove(k);
        copy.forEach(map::add);
    }

    /** A weight: the same number for every rarity, or per rarity. */
    private void weightField(JsonObject obj, Field f, int indent) {
        String key = f.key();
        JsonElement v = obj.get(key);
        boolean per = v.isJsonObject();
        Row head = row(indent, key, f.help());
        place(head, CycleButton.<Boolean>builder(b -> b ? t("per_rarity", "Per rarity") : t("same", "Same for all"))
                .withValues(false, true).withInitialValue(per).displayOnlyValue()
                .create(0, 0, 90, 18, Component.literal(key), (b, val) -> {
                    if (val) {
                        JsonObject m = new JsonObject();
                        for (ResourceLocation r : model.filesIn("rarities")) m.add(EditorModel.shortId(r).getPath(), v.isJsonPrimitive() ? v : new JsonPrimitive(10));
                        obj.add(key, m);
                    } else {
                        obj.addProperty(key, 10);
                    }
                    restructure();
                }), valueX(indent));
        if (!per) {
            place(head, number(v, true, n -> obj.add(key, n)), valueX(indent) + 92);
            removeField(head, obj, key);
            return;
        }
        place(head, Button.builder(t("add_entry", "+ Add"), b -> minecraft.setScreen(new PickerScreen(this, Pick.RARITY, model, k -> {
            obj.getAsJsonObject(key).add(uniqueKey(obj.getAsJsonObject(key), k), new JsonPrimitive(10));
            restructure();
        }))).bounds(0, 0, 50, 18).build(), valueX(indent) + 92);
        removeField(head, obj, key);
        mapEntries(v.getAsJsonObject(), Pick.RARITY, false, indent + 12);
    }

    /** A ValueSpec: fixed, a [min, max] roll, or per rarity (each fixed or a range). */
    private void valueField(JsonObject obj, Field f, int indent) {
        String key = f.key();
        JsonElement v = obj.get(key);
        int mode = v.isJsonObject() ? 2 : v.isJsonArray() ? 1 : 0;
        Row head = row(indent, key, f.help());
        List<Integer> modes = List.of(0, 1, 2);
        place(head, CycleButton.<Integer>builder(m -> switch (m) {
                    case 0 -> t("fixed", "Fixed");
                    case 1 -> t("range", "Random range");
                    default -> t("per_rarity", "Per rarity");
                }).withValues(modes).withInitialValue(mode).displayOnlyValue()
                .create(0, 0, 90, 18, Component.literal(key), (b, m) -> {
                    double base = firstNumber(v);
                    if (m == 0) obj.add(key, num(base));
                    else if (m == 1) obj.add(key, rangeJson(base, base * 2));
                    else {
                        JsonObject per = new JsonObject();
                        for (ResourceLocation r : model.filesIn("rarities")) per.add(EditorModel.shortId(r).getPath(), rangeJson(base, base * 2));
                        obj.add(key, per);
                    }
                    restructure();
                }), valueX(indent));
        int x = valueX(indent) + 92;
        if (mode == 0) place(head, number(v, false, n -> obj.add(key, n)), x);
        else if (mode == 1) rangeBoxes(head, x, v, val -> obj.add(key, val));
        else place(head, Button.builder(t("add_entry", "+ Add"), b -> minecraft.setScreen(new PickerScreen(this, Pick.RARITY, model, k -> {
            obj.getAsJsonObject(key).add(uniqueKey(obj.getAsJsonObject(key), k), rangeJson(0.1, 0.2));
            restructure();
        }))).bounds(0, 0, 50, 18).build(), x);
        removeField(head, obj, key);
        if (mode == 2) mapEntries(v.getAsJsonObject(), Pick.RARITY, true, indent + 12);
    }

    private static double firstNumber(JsonElement v) {
        try {
            if (v.isJsonPrimitive()) return v.getAsDouble();
            if (v.isJsonArray() && !v.getAsJsonArray().isEmpty()) return v.getAsJsonArray().get(0).getAsDouble();
            if (v.isJsonObject()) for (JsonElement e : v.getAsJsonObject().asMap().values()) return firstNumber(e);
        } catch (RuntimeException ignored) {
            // not a number: start from 0
        }
        return 0;
    }

    /** An affix's effects: each one a block with its type and the fields that type uses. */
    private void effectsField(JsonObject obj, Field f, int indent) {
        String key = f.key();
        JsonArray effects = asArray(obj.get(key));
        Row head = row(indent, key, f.help());
        List<String> types = new ArrayList<>(Schema.EFFECT_TYPES.keySet());
        String[] newType = {types.get(0)};
        place(head, CycleButton.<String>builder(Component::literal).withValues(types).withInitialValue(newType[0])
                .withTooltip(s -> Tooltip.create(Component.literal(Schema.effectHelp(s))))
                .displayOnlyValue().create(0, 0, 110, 18, Component.literal("type"), (b, val) -> newType[0] = val), valueX(indent));
        place(head, Button.builder(t("add_effect", "+ Add effect"), b -> {
            effects.add(newEffect(newType[0]));
            obj.add(key, effects);
            restructure();
        }).bounds(0, 0, 80, 18).build(), valueX(indent) + 112);
        for (int i = 0; i < effects.size(); i++) {
            if (!effects.get(i).isJsonObject()) continue;
            JsonObject effect = effects.get(i).getAsJsonObject();
            int index = i;
            String type = effect.has("type") ? effect.get("type").getAsString() : "";
            Row r = row(indent + 8, "Effect " + (i + 1), Schema.effectHelp(type));
            r.labelColor = 0xFFD080;
            List<String> opts = new ArrayList<>(types);
            if (!opts.contains(type)) opts.add(0, type);
            place(r, CycleButton.<String>builder(Component::literal).withValues(opts).withInitialValue(type)
                    .withTooltip(s -> Tooltip.create(Component.literal(Schema.effectHelp(s))))
                    .displayOnlyValue().create(0, 0, 130, 18, Component.literal("type"), (b, val) -> {
                        effect.addProperty("type", val);
                        // add the fields the new type needs, keep the rest
                        for (String need : Schema.EFFECT_TYPES.getOrDefault(val, List.of())) {
                            if (!effect.has(need)) effect.add(need, Schema.EFFECT_FIELDS.get(need).def().get());
                        }
                        restructure();
                    }), valueX(indent + 8));
            place(r, removeButton(() -> {
                effects.remove(index);
                obj.add(key, effects);
            }), formW - 22);
            // the type's own fields first, then every other effect field
            List<Field> fields = new ArrayList<>();
            for (String k : Schema.EFFECT_TYPES.getOrDefault(type, List.of())) fields.add(Schema.EFFECT_FIELDS.get(k));
            for (Field ef : Schema.EFFECT_FIELDS.values()) if (!fields.contains(ef)) fields.add(ef);
            addFields(effect, fields, indent + 20, List.of("type"));
        }
    }

    private static JsonObject newEffect(String type) {
        JsonObject e = new JsonObject();
        e.addProperty("type", type);
        for (String k : Schema.EFFECT_TYPES.getOrDefault(type, List.of())) e.add(k, Schema.EFFECT_FIELDS.get(k).def().get());
        return e;
    }

    // ---- the Mods tab ----------------------------------------------------------------------------------------

    private void buildMods() {
        TRData data = model.preview();
        Row intro = row(0, "", null);
        intro.wideLabel = true;
        intro.labelColor = 0x90C0FF;
        intro.label = t("mods.intro", "Loaded mods with items that can be relics");
        intro.help = t("mods.intro.tip", "Exclude a whole mod here, or open its items to exclude single ones. Exclusions go in tarnishedrelics:exclusions/editor.");
        Map<String, List<Item>> byMod = Lookup.candidatesByMod(data);
        for (Map.Entry<String, List<Item>> e : byMod.entrySet()) {
            String mod = e.getKey();
            List<Item> items = e.getValue();
            long relics = items.stream().filter(i -> data.categoryOf(i) != null).count();
            boolean excluded = model.excludedEntry(mod + ":*");
            Row r = row(0, "", null);
            r.wideLabel = true;
            r.label = Component.literal(Lookup.modName(mod)).append(Component.literal("  " + mod).withStyle(s -> s.withColor(0x808080)));
            r.labelColor = excluded ? 0x909090 : TEXT;
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
                    .bounds(0, 0, 60, 18).tooltip(Tooltip.create(t("mods.count", "Relics now / items that fit a category"))).build();
            count.active = false;
            place(r, count, formW - 60 - 4 - 60 - 4 - 80);
            place(r, Button.builder(t("mods.items", "Items"), b -> minecraft.setScreen(new ItemsScreen(this, Component.literal(Lookup.modName(mod)), model,
                    () -> items))).bounds(0, 0, 60, 18).build(), formW - 60 - 4 - 80);
            place(r, Button.builder(excluded ? t("mods.excluded", "Excluded") : t("mods.included", "Included"), b -> {
                model.setExcluded(mod + ":*", !excluded);
                restructure();
            }).bounds(0, 0, 80, 18).tooltip(Tooltip.create(t("mods.toggle", "Excluded: nothing from this mod is ever a relic"))).build(), formW - 80);
        }
    }

    // ---- small widgets ---------------------------------------------------------------------------------------

    private EditBox text(String value, int w, Consumer<String> onChange) {
        EditBox box = new EditBox(font, 0, 0, Math.max(30, w), 18, Component.literal("value"));
        box.setMaxLength(32000);
        box.setValue(value);
        box.moveCursorToStart();
        box.setResponder(s -> {
            onChange.accept(s);
            changed();
        });
        return box;
    }

    private EditBox number(JsonElement v, boolean integer, Consumer<JsonPrimitive> onChange) {
        EditBox box = text(fmt(v), 60, s -> { });
        box.setResponder(s -> {
            JsonPrimitive n = parseNumber(s, integer);
            box.setTextColor(n == null ? RED : TEXT);
            if (n != null) {
                onChange.accept(n);
                changed();
            }
        });
        return box;
    }

    /** min and max boxes; equal values are saved as one number. */
    private void rangeBoxes(Row r, int x, JsonElement v, Consumer<JsonElement> onChange) {
        String[] mm = {"0", "0"};
        if (v != null && v.isJsonArray() && v.getAsJsonArray().size() == 2) {
            mm[0] = fmt(v.getAsJsonArray().get(0));
            mm[1] = fmt(v.getAsJsonArray().get(1));
        } else if (v != null && v.isJsonPrimitive()) {
            mm[0] = fmt(v);
            mm[1] = fmt(v);
        }
        EditBox min = text(mm[0], 50, s -> { });
        EditBox max = text(mm[1], 50, s -> { });
        Runnable push = () -> {
            JsonPrimitive a = parseNumber(mm[0], false), b = parseNumber(mm[1], false);
            min.setTextColor(a == null ? RED : TEXT);
            max.setTextColor(b == null ? RED : TEXT);
            if (a == null || b == null) return;
            onChange.accept(a.getAsDouble() == b.getAsDouble() ? a : pair(a, b));
            changed();
        };
        min.setResponder(s -> {
            mm[0] = s;
            push.run();
        });
        max.setResponder(s -> {
            mm[1] = s;
            push.run();
        });
        min.setTooltip(Tooltip.create(t("min", "min")));
        max.setTooltip(Tooltip.create(t("max", "max")));
        place(r, min, x);
        place(r, max, x + 54);
    }

    private static JsonArray pair(JsonPrimitive a, JsonPrimitive b) {
        JsonArray arr = new JsonArray();
        arr.add(a);
        arr.add(b);
        return arr;
    }

    private static JsonArray rangeJson(double a, double b) {
        return pair(num(a), num(b));
    }

    private static JsonPrimitive num(double d) {
        if (d == Math.rint(d) && Math.abs(d) < 1e9) return new JsonPrimitive((long) d);
        return new JsonPrimitive(Math.round(d * 10000) / 10000.0);
    }

    @Nullable
    private static JsonPrimitive parseNumber(String s, boolean integer) {
        try {
            if (integer) return new JsonPrimitive(Integer.parseInt(s.trim()));
            return num(Double.parseDouble(s.trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String fmt(@Nullable JsonElement v) {
        if (v == null || !v.isJsonPrimitive()) return "0";
        try {
            double d = v.getAsDouble();
            if (d == Math.rint(d) && Math.abs(d) < 1e15) return Long.toString((long) d);
            return Double.toString(d);
        } catch (NumberFormatException e) {
            return v.getAsString();
        }
    }

    private static int parseColor(String s) {
        try {
            String hex = s.startsWith("#") ? s.substring(1) : s;
            if (hex.length() != 6) return -1;
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private Button pickButton(Pick pick, Consumer<String> onPick) {
        return Button.builder(Component.literal("..."), b -> minecraft.setScreen(new PickerScreen(this, pick, model, onPick)))
                .bounds(0, 0, 20, 18).tooltip(Tooltip.create(t("pick.tip", "Pick from what's loaded"))).build();
    }

    /** The remove button of a field (not for settings: every config option has to stay). */
    private void removeField(Row r, JsonObject obj, String key) {
        if (folder.equals("settings")) return;
        place(r, removeButton(() -> obj.remove(key)), formW - 22);
    }

    private Button removeButton(Runnable remove) {
        return Button.builder(Component.literal("✖"), b -> {
            remove.run();
            restructure();
        }).bounds(0, 0, 20, 18).tooltip(Tooltip.create(t("remove", "Remove"))).build();
    }

    private void checkBox(EditBox box, Pick pick, String value) {
        String problem = Lookup.check(pick, value, model);
        box.setTextColor(problem == null ? TEXT : (problem.contains("isn't loaded") ? 0xFFD060 : RED));
        box.setTooltip(problem == null ? null : Tooltip.create(Component.literal(problem)));
    }

    /** A value changed: the preview is stale and Save may become clickable. */
    private void changed() {
        model.edited();
        status = null;
        for (var child : children()) {
            if (child instanceof Button b && b.getMessage().getString().equals(t("save", "Save & Reload").getString())) b.active = !saving && model.hasChanges();
        }
        if (fileList != null) fileList.refreshMarks();
    }

    /** Rows were added or removed: rebuild, keeping the scroll position. */
    private void restructure() {
        model.edited();
        status = null;
        rebuildWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        if (fileList != null) fileListScroll = fileList.getScrollAmount();
        super.rebuildWidgets();
    }

    /** A readable name for a pack id: the editor, a mod's name, KubeJS, a world datapack... */
    private static String packName(String packId) {
        if (packId.equals(EditorServer.PACK_ID)) return "the editor (this world)";
        if (packId.equals(com.flankrat.tarnishedrelics.editor.ModpackPack.ID)) return "the modpack folder";
        if (packId.equals("config")) return "the config file";
        if (packId.equals("vanilla")) return "Minecraft";
        String id = packId.startsWith("mod:") ? packId.substring(4) : packId;
        if (Lookup.modLoaded(id) && !id.equals("minecraft")) return Lookup.modName(id) + " (mod)";
        if (packId.equals("main")) return "Tarnished Relics (dev)";
        if (packId.toLowerCase(Locale.ROOT).contains("kubejs")) return "KubeJS";
        if (packId.startsWith("file/")) return "datapack " + packId.substring(5);
        return packId;
    }

    // ---- input and drawing -----------------------------------------------------------------------------------

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (!raw && mx >= formX && mx < formX + formW && my >= formTop && my < formBottom) {
            int max = Math.max(0, contentHeight - (formBottom - formTop));
            scroll = Math.max(0, Math.min(max, scroll - delta * ROW));
            layoutForm();
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        // nearly opaque, so the hotbar and chat behind don't make the form hard to read
        g.fill(0, 0, width, height, 0xF0141418);
        g.drawString(font, title, 8, 6, 0xFFFFFF);
        boolean mods = fullWidth();
        if (!mods) {
            g.fill(6, 60, 8 + leftW + 2, height - 54, 0x60000000);
            if (selected != null) {
                Component name = Component.literal(selected.toString()).withStyle(s -> s.withColor(model.isChanged(selected) ? 0xFFD060 : 0xFFFFFF));
                g.drawString(font, name, formX, 42, 0xFFFFFF);
                String origin = model.origin(selected).isEmpty() ? "new" : "from " + packName(model.origin(selected));
                g.drawString(font, origin, formX + formW - font.width(origin), 42, 0x808080);
            } else {
                g.drawString(font, t("pick_file", "Pick a file on the left, or make a new one."), formX, 42, 0x909090);
            }
        }
        g.fill(formX - 2, formTop - 2, formX + formW + 2, formBottom + 2, 0x60000000);
        // row labels
        Component hoverHelp = null;
        g.enableScissor(formX - 2, formTop - 2, formX + formW + 2, formBottom + 2);
        if (!raw) for (Row r : rows) {
            int y = formTop + r.y - (int) scroll;
            if (y + ROW < formTop || y > formBottom) continue;
            int lx = formX + r.indent;
            int maxW = r.wideLabel ? (r.widgets.isEmpty() ? formW - r.indent : r.offsets.get(0) - r.indent - 4) : LABEL_W - 4;
            String text = font.plainSubstrByWidth(r.label.getString(), maxW);
            g.drawString(font, r.label.getString().length() == text.length() ? r.label : Component.literal(text),
                    lx, y + 6, r.labelColor, false);
            boolean whole = y >= formTop - 1 && y + r.height - 2 <= formBottom;
            if (r.swatch >= 0 && whole) {
                g.fill(formX + r.swatchX, y + 3, formX + r.swatchX + 14, y + 17, 0xFF000000);
                g.fill(formX + r.swatchX + 1, y + 4, formX + r.swatchX + 13, y + 16, 0xFF000000 | r.swatch);
            }
            if (r.help != null && mouseX >= lx && mouseX < lx + maxW && mouseY >= y + 3 && mouseY < y + 17) hoverHelp = r.help;
        }
        g.disableScissor();
        super.render(g, mouseX, mouseY, partial);
        if (contentHeight > formBottom - formTop && !raw) {
            // scroll bar
            int track = formBottom - formTop;
            int bar = Math.max(20, track * track / contentHeight);
            int top = formTop + (int) ((track - bar) * (scroll / Math.max(1, contentHeight - track)));
            g.fill(formX + formW, top, formX + formW + 2, top + bar, 0xFFA0A0A0);
        }
        renderStatus(g, mouseX, mouseY);
        if (hoverHelp != null) g.renderTooltip(font, font.split(hoverHelp, 250), mouseX, mouseY);
    }

    private void renderStatus(GuiGraphics g, int mouseX, int mouseY) {
        Component text = status;
        int color = statusColor;
        List<String> details = statusDetails;
        if (text == null && raw && rawError != null) {
            text = Component.literal("JSON: " + rawError);
            color = RED;
            details = List.of(rawError);
        }
        if (text == null && selected != null) {
            List<String> errs = model.errorsFor(selected);
            if (!errs.isEmpty()) {
                text = Component.literal(errs.get(0));
                color = RED;
                details = errs;
            }
        }
        if (text == null && model.hasChanges()) {
            text = t("unsaved", "%s unsaved change(s)", model.changes().size());
            color = 0xFFD060;
            details = new ArrayList<>(model.changes().keySet().stream().map(ResourceLocation::toString).toList());
        }
        if (text == null) return;
        int maxW = width - 8 - 200 - 8;
        String s = font.plainSubstrByWidth(text.getString(), maxW);
        g.drawString(font, s, 8, height - 18, color);
        if (!details.isEmpty() && mouseY >= height - 22 && mouseX < 8 + font.width(s)) {
            g.renderComponentTooltip(font, details.stream().limit(20).map(d -> (Component) Component.literal(d)).toList(), mouseX, mouseY);
        }
    }

    // ---- the file list ---------------------------------------------------------------------------------------

    class FileList extends ObjectSelectionList<FileList.Entry> {

        FileList(Minecraft mc, int w, int top, int bottom) {
            super(mc, w, EditorScreen.this.height, top, bottom, 14);
            setLeftPos(8);
            setRenderBackground(false);
            setRenderTopAndBottom(false);
        }

        void clear() {
            clearEntries();
        }

        void add(ResourceLocation id) {
            Entry e = new Entry(id);
            addEntry(e);
            if (id.equals(selected)) setSelected(e);
        }

        void refreshMarks() {
            // entries read the model when they draw, nothing to cache
        }

        @Override
        public int getRowWidth() {
            return width - 8;
        }

        @Override
        protected int getScrollbarPosition() {
            return x0 + width - 4;
        }

        class Entry extends ObjectSelectionList.Entry<Entry> {
            final ResourceLocation id;

            Entry(ResourceLocation id) {
                this.id = id;
            }

            @Override
            public void render(GuiGraphics g, int index, int top, int left, int w, int h, int mx, int my, boolean hovered, float partial) {
                ResourceLocation s = EditorModel.shortId(id);
                String name = (s.getNamespace().equals(EditorModel.NAMESPACE) ? "" : s.getNamespace() + ":") + s.getPath();
                int color = TEXT;
                if (model.get(id) == null) color = 0x707070;
                else if (!model.errorsFor(id).isEmpty()) color = RED;
                else if (model.isDisabled(id)) color = 0x808080;
                else if (model.isChanged(id)) color = 0xFFD060;
                else if (EditorModel.isEditorPack(model.origin(id))) color = 0x80E080;
                String mark = model.isChanged(id) || model.isRemoved(id) ? "* " : "";
                g.drawString(font, font.plainSubstrByWidth(mark + name, w - 4), left + 2, top + 2, color, false);
            }

            @Override
            public boolean mouseClicked(double mx, double my, int button) {
                if (!id.equals(selected)) {
                    if (raw && rawError != null) return true;   // stay until the JSON is fixed
                    selected = id;
                    scroll = 0;
                    rebuildWidgets();
                }
                return true;
            }

            @Override
            public Component getNarration() {
                return Component.literal(id.toString());
            }
        }
    }
}
