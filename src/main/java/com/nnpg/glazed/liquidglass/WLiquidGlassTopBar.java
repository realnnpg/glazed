package com.nnpg.glazed.liquidglass;

import meteordevelopment.meteorclient.gui.themes.meteor.widgets.WMeteorTopBar;
import meteordevelopment.meteorclient.utils.render.color.Color;

public class WLiquidGlassTopBar extends WMeteorTopBar implements GlazedLiquidGlassWidget {
    private static final Color PRESSED = new Color(178, 110, 255, 220);
    private static final Color HOVERED = new Color(132, 78, 228, 190);
    private static final Color NORMAL = new Color(66, 32, 140, 165);

    @Override
    protected Color getButtonColor(boolean pressed, boolean hovered) {
        return pressed ? PRESSED : hovered ? HOVERED : NORMAL;
    }
}
