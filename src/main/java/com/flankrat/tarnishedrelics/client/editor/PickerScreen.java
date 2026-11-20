package com.flankrat.tarnishedrelics.client.editor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Searchable list to pick an id: items (with icons), tags, whole mods, categories... depending on the field.
 * Filters by text (matches the id or the shown name) and by mod. Double-click or Select picks; Esc goes back.
 */
public class PickerScreen extends Screen {

    private static final String ALL = "*all*";

    private final Screen parent;
    private final Schema.Pick pick;
    private final Consumer<String> onPick;
    private final List<Lookup.Choice> all;
    private final List<String> mods = new ArrayList<>();
    private final List<String> kinds = new ArrayList<>();
    private String search = "";
    private String mod = ALL;
    private String kind = ALL;
    private EditBox searchBox;
    private ChoiceList list;

    public PickerScreen(Screen parent, Schema.Pick pick, EditorModel model, Consumer<String> onPick) {
        super(Component.translatableWithFallback("tarnishedrelics.editor.pick", "Pick a value"));
        this.parent = parent;
        this.pick = pick;
        this.onPick = onPick;
        this.all = Lookup.choices(pick, model);
        Set<String> m = new LinkedHashSet<>();
        Set<String> k = new LinkedHashSet<>();
        for (Lookup.Choice c : all) {
            if (!c.namespace().isEmpty()) m.add(c.namespace());
            k.add(c.kind());
        }
        mods.add(ALL);
        mods.addAll(m.stream().sorted().toList());
        kinds.add(ALL);
        kinds.addAll(k);
    }

    @Override
    protected void init() {
        int w = Math.min(360, width - 20);
        int x = (width - w) / 2;
        searchBox = new EditBox(font, x, 22, w - 190, 18, Component.literal("search"));
        searchBox.setMaxLength(200);
        searchBox.setValue(search);
        searchBox.setHint(Component.translatableWithFallback("tarnishedrelics.editor.search", "Search..."));
        searchBox.setResponder(s -> {
            search = s;
            refill();
        });
        addRenderableWidget(searchBox);
        addRenderableWidget(CycleButton.<String>builder(s -> Component.literal(s.equals(ALL) ? "All mods" : Lookup.modName(s)))
                .withValues(mods).withInitialValue(mod).displayOnlyValue()
                .create(x + w - 186, 22, 100, 18, Component.literal("Mod"), (b, v) -> {
                    mod = v;
                    refill();
                }));
        addRenderableWidget(CycleButton.<String>builder(s -> Component.literal(s.equals(ALL) ? "Everything" : kindName(s)))
                .withValues(kinds).withInitialValue(kind).displayOnlyValue()
                .create(x + w - 82, 22, 82, 18, Component.literal("Kind"), (b, v) -> {
                    kind = v;
                    refill();
                }));
        list = new ChoiceList(minecraft, w, height, 46, height - 32, x);
        addRenderableWidget(list);
        addRenderableWidget(Button.builder(Component.translatableWithFallback("tarnishedrelics.editor.select", "Select"), b -> choose())
                .bounds(width / 2 - 104, height - 26, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatableWithFallback("gui.cancel", "Cancel"), b -> onClose())
                .bounds(width / 2 + 4, height - 26, 100, 20).build());
        refill();
        setInitialFocus(searchBox);
    }

    private static String kindName(String kind) {
        return switch (kind) {
            case "special" -> "Special";
            case "category" -> "Categories";
            case "mod" -> "Whole mods";
            case "tag" -> "Tags";
            case "item" -> "Items";
            case "entity" -> "Mobs";
            case "block" -> "Blocks";
            default -> kind.substring(0, 1).toUpperCase(Locale.ROOT) + kind.substring(1);
        };
    }

    private void refill() {
        if (list == null) return;
        String q = search.toLowerCase(Locale.ROOT);
        list.clear();
        int shown = 0;
        for (Lookup.Choice c : all) {
            if (!mod.equals(ALL) && !c.namespace().equals(mod)) continue;
            if (!kind.equals(ALL) && !c.kind().equals(kind)) continue;
            if (!q.isEmpty() && !c.value().toLowerCase(Locale.ROOT).contains(q) && !c.label().toLowerCase(Locale.ROOT).contains(q)) continue;
            list.add(c);
            if (++shown >= 2000) break;   // keeps a "show everything" list from getting slow
        }
    }

    private void choose() {
        ChoiceList.Entry e = list.getSelected();
        if (e == null) return;
        Minecraft.getInstance().setScreen(parent);
        onPick.accept(e.choice.value());
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 257 || key == 335) {   // enter picks the selected (or only) entry
            if (list.getSelected() == null && list.children().size() == 1) list.setSelected(list.children().get(0));
            choose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, width, height, 0xF0141418);   // nearly opaque: the world behind made the list hard to read
        super.render(g, mouseX, mouseY, partial);
        g.drawCenteredString(font, title.copy().append(" (" + kindName(pick.name().toLowerCase(Locale.ROOT)) + ")"), width / 2, 8, 0xFFFFFF);
    }

    /** The scrolling result list. */
    class ChoiceList extends ObjectSelectionList<ChoiceList.Entry> {

        ChoiceList(Minecraft mc, int w, int h, int top, int bottom, int left) {
            super(mc, w, h, top, bottom, 20);
            setLeftPos(left);
            setRenderBackground(false);
            setRenderTopAndBottom(false);
        }

        void clear() {
            clearEntries();
            setScrollAmount(0);
        }

        void add(Lookup.Choice c) {
            addEntry(new Entry(c));
        }

        @Override
        public int getRowWidth() {
            return width - 12;
        }

        @Override
        protected int getScrollbarPosition() {
            return x0 + width - 6;
        }

        class Entry extends ObjectSelectionList.Entry<Entry> {
            final Lookup.Choice choice;
            private long lastClick;

            Entry(Lookup.Choice choice) {
                this.choice = choice;
            }

            @Override
            public void render(GuiGraphics g, int index, int top, int left, int w, int h, int mx, int my, boolean hovered, float partial) {
                if (!choice.icon().isEmpty()) g.renderItem(choice.icon(), left + 2, top + 1);
                g.drawString(font, choice.label(), left + 22, top + 2, 0xFFFFFF, false);
                g.drawString(font, choice.value(), left + 22, top + 11, 0x909090, false);
            }

            @Override
            public boolean mouseClicked(double mx, double my, int button) {
                setSelected(this);
                long now = System.currentTimeMillis();
                if (now - lastClick < 300) choose();   // double-click
                lastClick = now;
                return true;
            }

            @Override
            public Component getNarration() {
                return Component.literal(choice.label());
            }
        }
    }
}
