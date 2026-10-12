package com.nnpg.glazed.slate;

import meteordevelopment.meteorclient.gui.themes.meteor.widgets.input.WMeteorTextBox;
import meteordevelopment.meteorclient.gui.utils.CharFilter;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;

public class WSlateTextBox extends WMeteorTextBox implements GlazedSlateWidget {
    public WSlateTextBox(String text, String placeholder, CharFilter filter, Class<? extends WTextBox.Renderer> renderer) {
        super(text, placeholder, filter, renderer);
    }
}
