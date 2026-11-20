package com.flankrat.tarnishedrelics.client.editor;

import com.flankrat.tarnishedrelics.data.Category;
import com.flankrat.tarnishedrelics.data.TRData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * A list of items with what Tarnished Relics makes of each (category, material level, which rarities can roll it)
 * and a button to exclude or include it. Used for a mod's items, a category's matches and a rarity's base items.
 * Everything is worked out from the editor's unsaved working copy.
 */
public class ItemsScreen extends Screen {

    private final Screen parent;
    private final EditorModel model;
    private final Supplier<List<Item>> items;
    private String search = "";
    private ItemList list;

    public ItemsScreen(Screen parent, Component title, EditorModel model, Supplier<List<Item>> items) {
        super(title);
        this.parent = parent;
        this.model = model;
        this.items = items;
    }

    @Override
    protected void init() {
        int w = Math.min(420, width - 20);
        int x = (width - w) / 2;
        EditBox box = new EditBox(font, x, 22, w, 18, Component.literal("search"));
        box.setMaxLength(200);
        box.setValue(search);
        box.setHint(Component.translatableWithFallback("tarnishedrelics.editor.search", "Search..."));
        box.setResponder(s -> {
            search = s;
            refill();
        });
        addRenderableWidget(box);
        list = new ItemList(minecraft, w, height, 46, height - 32, x);
        addRenderableWidget(list);
        addRenderableWidget(Button.builder(Component.translatableWithFallback("gui.done", "Done"), b -> onClose())
                .bounds(width / 2 - 50, height - 26, 100, 20).build());
        refill();
    }

    private void refill() {
        String q = search.toLowerCase(Locale.ROOT);
        list.clear();
        for (Item item : items.get()) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
            String name = new ItemStack(item).getHoverName().getString();
            if (!q.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(q) && (id == null || !id.toString().contains(q))) continue;
            list.add(item, id);
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, width, height, 0xF0141418);   // nearly opaque: the world behind made the list hard to read
        super.render(g, mouseX, mouseY, partial);
        g.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
    }

    class ItemList extends ObjectSelectionList<ItemList.Entry> {

        ItemList(Minecraft mc, int w, int h, int top, int bottom, int left) {
            super(mc, w, h, top, bottom, 22);
            setLeftPos(left);
            setRenderBackground(false);
            setRenderTopAndBottom(false);
        }

        void clear() {
            clearEntries();
        }

        void add(Item item, ResourceLocation id) {
            addEntry(new Entry(item, id));
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
            final Item item;
            final ResourceLocation id;
            private int buttonX, buttonY;

            Entry(Item item, ResourceLocation id) {
                this.item = item;
                this.id = id;
            }

            private boolean excludedHere() {
                return id != null && model.excludedEntry(id.toString());
            }

            @Override
            public void render(GuiGraphics g, int index, int top, int left, int w, int h, int mx, int my, boolean hovered, float partial) {
                TRData data = model.preview();
                g.renderItem(new ItemStack(item), left + 2, top + 2);
                g.drawString(font, new ItemStack(item).getHoverName(), left + 22, top + 2, 0xFFFFFF, false);
                Category c = data.categoryOf(item);
                String info;
                if (c != null) {
                    long rarities = data.rarities().values().stream().filter(r -> r.items().contains(item)).count();
                    info = c.id().getPath() + " · level " + data.levels().of(item) + " · " + rarities + " rarit" + (rarities == 1 ? "y" : "ies");
                } else {
                    info = data.exclusions().contains(item) ? "excluded" : "not a relic";
                }
                g.drawString(font, (id == null ? "" : id + "  ") + info, left + 22, top + 12, c != null ? 0x90C090 : 0x909090, false);
                // include/exclude button drawn at the right
                boolean ex = excludedHere();
                String label = ex ? "Excluded" : "Exclude";
                int bw = font.width(label) + 8;
                buttonX = left + w - bw - 2;
                buttonY = top + 3;
                boolean over = mx >= buttonX && mx < buttonX + bw && my >= buttonY && my < buttonY + 14;
                g.fill(buttonX, buttonY, buttonX + bw, buttonY + 14, over ? 0xFF606060 : 0xFF383838);
                g.drawString(font, label, buttonX + 4, buttonY + 3, ex ? 0xFFFF7070 : 0xFFE0E0E0, false);
            }

            @Override
            public boolean mouseClicked(double mx, double my, int button) {
                setSelected(this);
                if (id != null && mx >= buttonX && my >= buttonY && my < buttonY + 14) {
                    model.setExcluded(id.toString(), !excludedHere());
                    return true;
                }
                return true;
            }

            @Override
            public Component getNarration() {
                return new ItemStack(item).getHoverName();
            }
        }
    }
}
