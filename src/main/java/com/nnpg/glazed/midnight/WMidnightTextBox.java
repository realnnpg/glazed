package com.nnpg.glazed.midnight;

import meteordevelopment.meteorclient.gui.themes.meteor.widgets.input.WMeteorTextBox;
import meteordevelopment.meteorclient.gui.utils.CharFilter;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;

public class WMidnightTextBox extends WMeteorTextBox implements GlazedMidnightWidget {
    public WMidnightTextBox(String text, String placeholder, CharFilter filter, Class<? extends WTextBox.Renderer> renderer) {
        super(text, placeholder, filter, renderer);
    }
}
