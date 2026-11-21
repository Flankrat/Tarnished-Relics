package com.flankrat.tarnishedrelics.client.editor;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/** One line of the editor's form: a name (with help on hover) and its controls at x offsets from the form's left. */
final class FormRow {
    int y;
    int indent;
    Component label = Component.empty();
    @Nullable Component help;
    int labelColor = EditorForm.TEXT;
    boolean wideLabel;
    final List<AbstractWidget> widgets = new ArrayList<>();
    final List<Integer> offsets = new ArrayList<>();
    int swatch = -1, swatchX;   // a color box (color fields)
    int height = EditorForm.ROW;
}
