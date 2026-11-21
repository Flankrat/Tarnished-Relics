package com.flankrat.tarnishedrelics.client.editor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The editor's list of files in the open folder. Each entry is colored by its state: removed (grey), has errors
 * (red), turned off, changed (yellow, with a "*"), or saved by the editor (green). Clicking one opens it.
 */
final class EditorFileList extends ObjectSelectionList<EditorFileList.Entry> {

    private final EditorScreen screen;

    EditorFileList(EditorScreen screen, Minecraft mc, int w, int height, int top, int bottom) {
        super(mc, w, height, top, bottom, 14);
        this.screen = screen;
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
        if (id.equals(screen.selected())) setSelected(e);
    }

    @Override
    public int getRowWidth() {
        return width - 8;
    }

    @Override
    protected int getScrollbarPosition() {
        return x0 + width - 4;
    }

    final class Entry extends ObjectSelectionList.Entry<Entry> {
        final ResourceLocation id;

        Entry(ResourceLocation id) {
            this.id = id;
        }

        @Override
        public void render(GuiGraphics g, int index, int top, int left, int w, int h, int mx, int my, boolean hovered, float partial) {
            EditorModel model = screen.model();
            ResourceLocation s = EditorModel.shortId(id);
            String name = (s.getNamespace().equals(EditorModel.NAMESPACE) ? "" : s.getNamespace() + ":") + s.getPath();
            int color = EditorForm.TEXT;
            if (model.get(id) == null) color = 0x707070;
            else if (!model.errorsFor(id).isEmpty()) color = EditorForm.RED;
            else if (model.isDisabled(id)) color = 0x808080;
            else if (model.isChanged(id)) color = 0xFFD060;
            else if (EditorModel.isEditorPack(model.origin(id))) color = 0x80E080;
            String mark = model.isChanged(id) || model.isRemoved(id) ? "* " : "";
            var font = Minecraft.getInstance().font;
            g.drawString(font, font.plainSubstrByWidth(mark + name, w - 4), left + 2, top + 2, color, false);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            screen.openFile(id);
            return true;
        }

        @Override
        public Component getNarration() {
            return Component.literal(id.toString());
        }
    }
}
