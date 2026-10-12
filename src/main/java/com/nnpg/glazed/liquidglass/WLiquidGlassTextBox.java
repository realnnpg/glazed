package com.nnpg.glazed.liquidglass;

import meteordevelopment.meteorclient.gui.themes.meteor.widgets.input.WMeteorTextBox;
import meteordevelopment.meteorclient.gui.utils.CharFilter;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;

public class WLiquidGlassTextBox extends WMeteorTextBox implements GlazedLiquidGlassWidget {
    public WLiquidGlassTextBox(String text, String placeholder, CharFilter filter, Class<? extends WTextBox.Renderer> renderer) {
        super(text, placeholder, filter, renderer);
    }
}