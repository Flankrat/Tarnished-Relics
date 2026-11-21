package com.flankrat.tarnishedrelics.client.editor;

import com.flankrat.tarnishedrelics.client.editor.Schema.Field;
import com.flankrat.tarnishedrelics.client.editor.Schema.Pick;
import com.flankrat.tarnishedrelics.editor.EditorServer;
import com.flankrat.tarnishedrelics.editor.ModpackPack;
import com.flankrat.tarnishedrelics.name.NameFallback;
import com.flankrat.tarnishedrelics.network.TRNetwork;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;

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
public class EditorScreen extends Screen implements EditorForm.Host {

    /** The open editor, for packets that arrive while a picker is on top of it. */
    @Nullable static EditorScreen last;

    private static final List<String> TABS = List.of("categories", "rarities", "affixes", "legendaries", "names", "levels", "exclusions", "tags", "settings", "mods");
    /** Where Save writes: the world's editor pack, or the modpack folder every world loads. Kept between openings. */
    private static boolean saveToModpack;
    private static final int RED = EditorForm.RED;
    private static final int TEXT = EditorForm.TEXT;

    private EditorModel model;
    private String folder = "categories";
    @Nullable private ResourceLocation selected;
    private boolean raw;
    @Nullable private String rawError;
    private String fileSearch = "";
    private double scroll;
    private double fileListScroll;
    @Nullable private String newName;
    @Nullable private ResourceLocation copyFrom;   // the file a new file starts as a copy of (null: a blank one)
    @Nullable private Component status;
    private int statusColor = TEXT;
    private List<String> statusDetails = List.of();
    private boolean saving;

    // layout, worked out in init()
    private int leftW, formX, formTop, formW, formBottom;
    private EditorForm form;
    private EditorFileList fileList;

    public EditorScreen(EditorModel model) {
        super(Component.translatableWithFallback("tarnishedrelics.editor.title", "Tarnished Relics Editor"));
        this.model = model;
        last = this;
    }

    private static Component t(String key, String english, Object... args) {
        return EditorForm.t(key, english, args);
    }

    EditorModel model() {
        return model;
    }

    @Nullable
    ResourceLocation selected() {
        return selected;
    }

    /** Opens a file from the list (not while the raw JSON has an error: stay until it's fixed). */
    void openFile(ResourceLocation id) {
        if (id.equals(selected)) return;
        if (raw && rawError != null) return;
        selected = id;
        scroll = 0;
        rebuildWidgets();
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
        leftW = Math.max(110, Math.min(170, width / 4));
        boolean mods = fullWidth();
        formX = mods ? 8 : leftW + 14;
        formW = width - formX - 8;
        formTop = mods ? 46 : 92;
        formBottom = height - 30;
        form = new EditorForm(this, model, font, formX, formTop, formW, formBottom, folder.equals("settings"));

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
                    copyFrom = null;
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
        form.layout(scroll);
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

        fileList = new EditorFileList(this, minecraft, leftW, height, 62, height - 56);
        addRenderableWidget(fileList);
        fillFileList();
        fileList.setScrollAmount(fileListScroll);

        if (newName == null) {
            addRenderableWidget(Button.builder(t("new", "+ New"), b -> {
                newName = "";
                copyFrom = null;
                rebuildWidgets();
            }).bounds(8, height - 52, leftW / 2 - 1, 18).tooltip(Tooltip.create(t("new.tip", "Make a new file in this folder"))).build());
            Button copy = Button.builder(t("copy", "Copy"), b -> {
                newName = selected == null ? "" : EditorModel.shortId(selected).getPath() + "_copy";
                // remembered now: the copy is of this file whatever the new one is called, even if another is clicked
                copyFrom = selected;
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
                copyFrom = null;
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
        JsonObject start = copyFrom != null && model.get(copyFrom) != null ? model.get(copyFrom).deepCopy() : Schema.template(folder);
        start.remove("disabled");
        // a readable name from the file name, so it never shows a raw lang key
        String title = NameFallback.title(name);
        switch (folder) {
            case "categories" -> start.addProperty("name", "Unidentified " + title);
            case "rarities" -> start.addProperty("name", title);
            case "affixes" -> start.addProperty("name", title);
            default -> { }
        }
        model.put(id, start);
        selected = id;
        newName = null;
        copyFrom = null;
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
        EditorClient.save(model, saveToModpack);
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

    private void buildForm() {
        form.start();
        if (folder.equals("mods")) {
            EditorSections.mods(this, form, formW);
        } else if (folder.equals("settings")) {
            EditorSections.settings(form, model);
        } else if (selected != null) {
            JsonObject json = model.get(selected);
            if (json == null) {
                FormRow r = form.row(0, "", null);
                r.wideLabel = true;
                r.label = model.overridesAnother(selected)
                        ? t("removed.original", "On save, the editor's copy goes and the version from %s is used again.", packName(model.origins(selected).get(model.origins(selected).size() - 2)))
                        : t("removed", "This file will be deleted when you save.");
            } else if (raw) {
                buildRaw(json);
            } else {
                EditorSections.info(this, form, folder, selected, formW);
                if (model.isDisabled(selected)) {
                    FormRow r = form.row(0, "", null);
                    r.wideLabel = true;
                    r.labelColor = 0xFFD060;
                    r.label = t("off", "This file is turned off: the game ignores it.");
                }
                List<Field> fields = folder.equals("tags")
                        ? Schema.tagFields(EditorModel.shortId(selected).getPath().split("/")[0])
                        : Schema.FILES.getOrDefault(folder, List.of());
                form.addFields(json, fields, 0, folder.equals("tags") ? List.of("replace") : List.of("disabled"));
            }
        }
        form.finish();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, form.contentHeight() - (formBottom - formTop))));
    }

    private void buildRaw(JsonObject json) {
        MultiLineEditBox box = new MultiLineEditBox(font, formX, formTop, formW, formBottom - formTop - 14,
                Component.empty(), Component.literal("json"));
        box.setCharacterLimit(Integer.MAX_VALUE);   // never cut a big file short (that would lose its end on save)
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

    // ---- small widgets ---------------------------------------------------------------------------------------

    @Override
    public <T extends AbstractWidget> T addWidget(T widget) {
        return addRenderableWidget(widget);
    }

    @Override
    public Screen screen() {
        return this;
    }

    @Override
    public void open(Screen screen) {
        minecraft.setScreen(screen);
    }

    /** A value changed: the preview is stale and Save may become clickable. */
    @Override
    public void changed() {
        model.edited();
        status = null;
        for (var child : children()) {
            if (child instanceof Button b && b.getMessage().getString().equals(t("save", "Save & Reload").getString())) b.active = !saving && model.hasChanges();
        }
    }

    /** Rows were added or removed: rebuild, keeping the scroll position. */
    @Override
    public void restructure() {
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
        if (packId.equals(ModpackPack.ID)) return "the modpack folder";
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
            int max = Math.max(0, form.contentHeight() - (formBottom - formTop));
            scroll = Math.max(0, Math.min(max, scroll - delta * EditorForm.ROW));
            form.layout(scroll);
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
        if (!raw) hoverHelp = form.renderLabels(g, scroll, mouseX, mouseY);
        g.disableScissor();
        super.render(g, mouseX, mouseY, partial);
        if (form.contentHeight() > formBottom - formTop && !raw) {
            // scroll bar
            int track = formBottom - formTop;
            int bar = Math.max(20, track * track / form.contentHeight());
            int top = formTop + (int) ((track - bar) * (scroll / Math.max(1, form.contentHeight() - track)));
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

}
