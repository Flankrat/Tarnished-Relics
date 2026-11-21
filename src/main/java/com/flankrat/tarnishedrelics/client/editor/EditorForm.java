package com.flankrat.tarnishedrelics.client.editor;

import com.flankrat.tarnishedrelics.client.editor.Schema.Field;
import com.flankrat.tarnishedrelics.client.editor.Schema.Kind;
import com.flankrat.tarnishedrelics.client.editor.Schema.Pick;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nullable;

import static com.flankrat.tarnishedrelics.client.editor.FormJson.*;

/**
 * The editor's form: a list of rows (a name on the left, controls on the right) built from {@link Schema} fields,
 * for a JSON object and everything inside it (lists, maps, per-rarity values, effects, conditions). Edits change
 * the JSON in place. The screen ({@link Host}) owns the widgets and decides what a change means: a plain value edit
 * ({@link Host#changed()}) keeps the widgets, adding or removing rows rebuilds them ({@link Host#restructure()}).
 */
final class EditorForm {

    /** What the form needs from the screen it's on. */
    interface Host {
        /** Adds a widget to the screen. */
        <T extends AbstractWidget> T addWidget(T widget);

        /** The screen pickers go back to. */
        Screen screen();

        /** Opens another screen (a picker) on top. */
        void open(Screen screen);

        /** A value changed (no rows added or removed). */
        void changed();

        /** Rows were added or removed: rebuild the form. */
        void restructure();
    }

    static final int ROW = 22;
    static final int LABEL_W = 100;
    static final int RED = 0xFF6060;
    static final int TEXT = 0xE0E0E0;

    private final Host host;
    private final EditorModel model;
    private final Font font;
    private final int formX, formTop, formW, formBottom;
    private final boolean settings;   // the Settings tab: every config option has to stay (no remove buttons)
    private final List<FormRow> rows = new ArrayList<>();
    private int contentHeight;

    EditorForm(Host host, EditorModel model, Font font, int formX, int formTop, int formW, int formBottom, boolean settings) {
        this.host = host;
        this.model = model;
        this.font = font;
        this.formX = formX;
        this.formTop = formTop;
        this.formW = formW;
        this.formBottom = formBottom;
        this.settings = settings;
    }

    static Component t(String key, String english, Object... args) {
        return Component.translatableWithFallback("tarnishedrelics.editor." + key, english, args);
    }

    /** Starts the rows from the top (a small gap above the first). */
    void start() {
        rows.clear();
        contentHeight = 4;
    }

    /** Ends the rows (a small gap below the last). */
    void finish() {
        contentHeight += 6;
    }

    int contentHeight() {
        return contentHeight;
    }

    /** Draws the row names and color swatches; returns the help of the row name under the mouse, if any. */
    @Nullable
    Component renderLabels(GuiGraphics g, double scroll, int mouseX, int mouseY) {
        Component hoverHelp = null;
        for (FormRow r : rows) {
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
        return hoverHelp;
    }

    FormRow row(int indent, String label, @Nullable String help) {
        FormRow r = new FormRow();
        r.y = contentHeight;
        r.indent = indent;
        r.label = Component.literal(label);
        r.help = help == null || help.isEmpty() ? null : Component.literal(help);
        rows.add(r);
        contentHeight += ROW;
        return r;
    }

    <T extends AbstractWidget> T place(FormRow r, T widget, int offset) {
        r.widgets.add(widget);
        r.offsets.add(offset);
        host.addWidget(widget);
        return widget;
    }

    /** Where a row's controls start, and how wide they can be. */
    int valueX(int indent) {
        return indent + LABEL_W;
    }

    int valueW(int indent) {
        return formW - valueX(indent) - 2;
    }

    void layout(double scroll) {
        for (FormRow r : rows) {
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

    /** Rows for an object's fields: the ones it has, unknown ones as raw JSON, then an "add field" row. */
    void addFields(JsonObject obj, List<Field> fields, int indent, List<String> skip) {
        List<String> known = new ArrayList<>(skip);
        for (Field f : fields) {
            known.add(f.key());
            if (obj.has(f.key())) addField(obj, f, indent);
        }
        for (String key : new ArrayList<>(obj.keySet())) if (!known.contains(key)) rawField(obj, key, indent);
        addFieldFormRow(obj, fields.stream().filter(f -> !obj.has(f.key())).toList(), indent);
    }

    private void addFieldFormRow(JsonObject obj, List<Field> absent, int indent) {
        if (absent.isEmpty()) return;
        FormRow r = row(indent, "", null);
        r.labelColor = 0x909090;
        r.label = t("add_field", "+ field");
        Field[] pick = {absent.get(0)};
        int w = Math.min(160, valueW(indent) - 50);
        place(r, CycleButton.<Field>builder(f -> Component.literal(f.key())).withValues(absent).withInitialValue(absent.get(0))
                .withTooltip(f -> Tooltip.create(Component.literal(f.help())))
                .displayOnlyValue().create(0, 0, w, 18, Component.literal("field"), (b, v) -> pick[0] = v), valueX(indent));
        place(r, Button.builder(t("add", "Add"), b -> {
            obj.add(pick[0].key(), pick[0].def().get());
            host.restructure();
        }).bounds(0, 0, 46, 18).build(), valueX(indent) + w + 2);
    }

    void addField(JsonObject obj, Field f, int indent) {
        String key = f.key();
        JsonElement v = obj.get(key);
        switch (f.kind()) {
            case TEXT -> {
                FormRow r = row(indent, key, f.help());
                int w = valueW(indent) - 22 - (f.pick() != Pick.NONE ? 22 : 0);
                EditBox box = text(v.isJsonPrimitive() ? v.getAsString() : v.toString(), w, s -> obj.addProperty(key, s));
                if (f.pick() != Pick.NONE) {
                    checkBox(box, f.pick(), box.getValue());
                    box.setResponder(s -> {
                        obj.addProperty(key, s);
                        checkBox(box, f.pick(), s);
                        host.changed();
                    });
                }
                place(r, box, valueX(indent));
                if (f.pick() != Pick.NONE) place(r, pickButton(f.pick(), val -> {
                    obj.addProperty(key, val);
                    host.restructure();
                }), valueX(indent) + w + 2);
                removeField(r, obj, key);
            }
            case NUMBER, INT -> {
                FormRow r = row(indent, key, f.help());
                place(r, number(v, f.kind() == Kind.INT, n -> obj.add(key, n)), valueX(indent));
                removeField(r, obj, key);
            }
            case BOOL -> {
                FormRow r = row(indent, key, f.help());
                place(r, CycleButton.onOffBuilder(v.isJsonPrimitive() && v.getAsBoolean()).displayOnlyValue()
                        .create(0, 0, 60, 18, Component.literal(key), (b, val) -> {
                            obj.addProperty(key, val);
                            host.changed();
                        }), valueX(indent));
                removeField(r, obj, key);
            }
            case ENUM -> {
                FormRow r = row(indent, key, f.help());
                List<String> opts = new ArrayList<>(f.options());
                String cur = v.isJsonPrimitive() ? v.getAsString() : "";
                if (!opts.contains(cur)) opts.add(0, cur);
                place(r, CycleButton.<String>builder(Component::literal).withValues(opts).withInitialValue(cur).displayOnlyValue()
                        .create(0, 0, Math.min(140, valueW(indent) - 24), 18, Component.literal(key), (b, val) -> {
                            obj.addProperty(key, val);
                            host.changed();
                        }), valueX(indent));
                removeField(r, obj, key);
            }
            case COLOR -> {
                FormRow r = row(indent, key, f.help());
                EditBox box = text(v.isJsonPrimitive() ? v.getAsString() : "#FFFFFF", 70, s -> obj.addProperty(key, s));
                place(r, box, valueX(indent));
                r.swatchX = valueX(indent) + 74;
                box.setResponder(s -> {
                    obj.addProperty(key, s);
                    r.swatch = parseColor(s);
                    box.setTextColor(r.swatch < 0 ? RED : TEXT);
                    host.changed();
                });
                r.swatch = parseColor(box.getValue());
                removeField(r, obj, key);
            }
            case RANGE -> {
                FormRow r = row(indent, key, f.help());
                rangeBoxes(r, valueX(indent), v, val -> obj.add(key, val));
                removeField(r, obj, key);
            }
            case LIST -> listField(obj, f, indent);
            case MAP_INT, MAP_RANGE -> mapField(obj, f, indent);
            case WEIGHT -> weightField(obj, f, indent);
            case VALUE -> valueField(obj, f, indent);
            case CONDITIONS -> {
                FormRow r = row(indent, key, f.help());
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
        FormRow r = row(indent, key, "Edited as raw JSON");
        r.labelColor = 0xC0A0FF;
        EditBox box = text(obj.get(key).toString(), valueW(indent) - 22, s -> { });
        box.setResponder(s -> {
            try {
                obj.add(key, JsonParser.parseString(s));
                box.setTextColor(TEXT);
                host.changed();
            } catch (RuntimeException e) {
                box.setTextColor(RED);
            }
        });
        place(r, box, valueX(indent));
        removeField(r, obj, key);
    }

    /** A string list. A single string in the file counts as a one-entry list; edits always write an array. */
    private void listField(JsonObject obj, Field f, int indent) {
        String key = f.key();
        JsonArray arr = asArray(obj.get(key));
        FormRow head = row(indent, key, f.help());
        place(head, Button.builder(t("add_entry", "+ Add"), b -> {
            if (f.pick() != Pick.NONE) {
                host.open(new PickerScreen(host.screen(), f.pick(), model, val -> {
                    arr.add(val);
                    obj.add(key, arr);
                    host.restructure();
                }));
            } else {
                arr.add("");
                obj.add(key, arr);
                host.restructure();
            }
        }).bounds(0, 0, 50, 18).build(), valueX(indent));
        if (arr.isEmpty()) {
            head.labelColor = TEXT;
        }
        removeField(head, obj, key);
        for (int i = 0; i < arr.size(); i++) {
            int index = i;
            FormRow r = row(indent + 12, "", null);
            int w = valueW(indent) - 22 - (f.pick() != Pick.NONE ? 22 : 0);
            EditBox box = text(arr.get(i).isJsonPrimitive() ? arr.get(i).getAsString() : arr.get(i).toString(), w, s -> { });
            if (f.pick() != Pick.NONE) checkBox(box, f.pick(), entryId(arr.get(i)));
            box.setResponder(s -> {
                JsonElement entry = listEntry(s);
                arr.set(index, entry);
                obj.add(key, arr);
                if (f.pick() != Pick.NONE) checkBox(box, f.pick(), entryId(entry));
                host.changed();
            });
            place(r, box, valueX(indent));
            if (f.pick() != Pick.NONE) place(r, pickButton(f.pick(), val -> {
                arr.set(index, new JsonPrimitive(val));
                obj.add(key, arr);
                host.restructure();
            }), valueX(indent) + w + 2);
            place(r, removeButton(() -> {
                arr.remove(index);
                obj.add(key, arr);
            }), formW - 22);
        }
    }

    /** { key: int } or { key: [min, max] }. Keys can be renamed in place (order is kept). */
    private void mapField(JsonObject obj, Field f, int indent) {
        String key = f.key();
        if (!obj.get(key).isJsonObject()) obj.add(key, new JsonObject());
        JsonObject map = obj.getAsJsonObject(key);
        FormRow head = row(indent, key, f.help());
        place(head, Button.builder(t("add_entry", "+ Add"), b -> {
            Consumer<String> add = k -> {
                map.add(uniqueKey(map, k), f.kind() == Kind.MAP_INT ? new JsonPrimitive(1) : rangeJson(1, 2));
                host.restructure();
            };
            if (f.pick() != Pick.NONE) host.open(new PickerScreen(host.screen(), f.pick(), model, add));
            else add.accept("new");
        }).bounds(0, 0, 50, 18).build(), valueX(indent));
        removeField(head, obj, key);
        mapEntries(map, f.pick(), f.kind() == Kind.MAP_RANGE, indent + 12);
    }

    private void mapEntries(JsonObject map, Pick keyPick, boolean ranges, int indent) {
        for (String k : new ArrayList<>(map.keySet())) {
            String[] keyRef = {k};
            FormRow r = row(indent, "", null);
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
                host.changed();
            });
            place(r, keyBox, x);
            x += keyW + 2;
            if (keyPick != Pick.NONE) {
                place(r, pickButton(keyPick, val -> {
                    if (!map.has(val)) renameKey(map, keyRef[0], val);
                    host.restructure();
                }), x);
                x += 22;
            }
            if (ranges) rangeBoxes(r, x, map.get(k), val -> map.add(keyRef[0], val));
            else place(r, number(map.get(k), true, n -> map.add(keyRef[0], n)), x);
            place(r, removeButton(() -> map.remove(keyRef[0])), formW - 22);
        }
    }

    /** A weight: the same number for every rarity, or per rarity. */
    private void weightField(JsonObject obj, Field f, int indent) {
        String key = f.key();
        JsonElement v = obj.get(key);
        boolean per = v.isJsonObject();
        FormRow head = row(indent, key, f.help());
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
                    host.restructure();
                }), valueX(indent));
        if (!per) {
            place(head, number(v, true, n -> obj.add(key, n)), valueX(indent) + 92);
            removeField(head, obj, key);
            return;
        }
        place(head, Button.builder(t("add_entry", "+ Add"), b -> host.open(new PickerScreen(host.screen(), Pick.RARITY, model, k -> {
            obj.getAsJsonObject(key).add(uniqueKey(obj.getAsJsonObject(key), k), new JsonPrimitive(10));
            host.restructure();
        }))).bounds(0, 0, 50, 18).build(), valueX(indent) + 92);
        removeField(head, obj, key);
        mapEntries(v.getAsJsonObject(), Pick.RARITY, false, indent + 12);
    }

    /** A ValueSpec: fixed, a [min, max] roll, or per rarity (each fixed or a range). */
    private void valueField(JsonObject obj, Field f, int indent) {
        String key = f.key();
        JsonElement v = obj.get(key);
        int mode = v.isJsonObject() ? 2 : v.isJsonArray() ? 1 : 0;
        FormRow head = row(indent, key, f.help());
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
                    host.restructure();
                }), valueX(indent));
        int x = valueX(indent) + 92;
        if (mode == 0) place(head, number(v, false, n -> obj.add(key, n)), x);
        else if (mode == 1) rangeBoxes(head, x, v, val -> obj.add(key, val));
        else place(head, Button.builder(t("add_entry", "+ Add"), b -> host.open(new PickerScreen(host.screen(), Pick.RARITY, model, k -> {
            obj.getAsJsonObject(key).add(uniqueKey(obj.getAsJsonObject(key), k), rangeJson(0.1, 0.2));
            host.restructure();
        }))).bounds(0, 0, 50, 18).build(), x);
        removeField(head, obj, key);
        if (mode == 2) mapEntries(v.getAsJsonObject(), Pick.RARITY, true, indent + 12);
    }

    /** An affix's effects: each one a block with its type and the fields that type uses. */
    private void effectsField(JsonObject obj, Field f, int indent) {
        String key = f.key();
        JsonArray effects = asArray(obj.get(key));
        FormRow head = row(indent, key, f.help());
        List<String> types = new ArrayList<>(Schema.EFFECT_TYPES.keySet());
        String[] newType = {types.get(0)};
        place(head, CycleButton.<String>builder(Component::literal).withValues(types).withInitialValue(newType[0])
                .withTooltip(s -> Tooltip.create(Component.literal(Schema.effectHelp(s))))
                .displayOnlyValue().create(0, 0, 110, 18, Component.literal("type"), (b, val) -> newType[0] = val), valueX(indent));
        place(head, Button.builder(t("add_effect", "+ Add effect"), b -> {
            effects.add(newEffect(newType[0]));
            obj.add(key, effects);
            host.restructure();
        }).bounds(0, 0, 80, 18).build(), valueX(indent) + 112);
        for (int i = 0; i < effects.size(); i++) {
            if (!effects.get(i).isJsonObject()) continue;
            JsonObject effect = effects.get(i).getAsJsonObject();
            int index = i;
            String type = effect.get("type") instanceof JsonPrimitive t ? t.getAsString() : "";   // an odd "type" shows as blank, not a crash
            FormRow r = row(indent + 8, "Effect " + (i + 1), Schema.effectHelp(type));
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
                        host.restructure();
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

    private EditBox text(String value, int w, Consumer<String> onChange) {
        EditBox box = new EditBox(font, 0, 0, Math.max(30, w), 18, Component.literal("value"));
        box.setMaxLength(Integer.MAX_VALUE);   // a value is never cut short
        box.setValue(value);
        box.moveCursorToStart();
        box.setResponder(s -> {
            onChange.accept(s);
            host.changed();
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
                host.changed();
            }
        });
        return box;
    }

    /** min and max boxes; equal values are saved as one number. */
    private void rangeBoxes(FormRow r, int x, JsonElement v, Consumer<JsonElement> onChange) {
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
            host.changed();
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

    private Button pickButton(Pick pick, Consumer<String> onPick) {
        return Button.builder(Component.literal("..."), b -> host.open(new PickerScreen(host.screen(), pick, model, onPick)))
                .bounds(0, 0, 20, 18).tooltip(Tooltip.create(t("pick.tip", "Pick from what's loaded"))).build();
    }

    /** The remove button of a field (not for settings: every config option has to stay). */
    private void removeField(FormRow r, JsonObject obj, String key) {
        if (settings) return;
        place(r, removeButton(() -> obj.remove(key)), formW - 22);
    }

    private Button removeButton(Runnable remove) {
        return Button.builder(Component.literal("✖"), b -> {
            remove.run();
            host.restructure();
        }).bounds(0, 0, 20, 18).tooltip(Tooltip.create(t("remove", "Remove"))).build();
    }

    private void checkBox(EditBox box, Pick pick, String value) {
        String problem = Lookup.check(pick, value, model);
        box.setTextColor(problem == null ? TEXT : (problem.contains("isn't loaded") ? 0xFFD060 : RED));
        box.setTooltip(problem == null ? null : Tooltip.create(Component.literal(problem)));
    }
}
