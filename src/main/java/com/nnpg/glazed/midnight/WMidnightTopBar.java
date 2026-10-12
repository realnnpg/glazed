package com.nnpg.glazed.midnight;

import meteordevelopment.meteorclient.gui.themes.meteor.widgets.WMeteorTopBar;
import meteordevelopment.meteorclient.utils.render.color.Color;

public class WMidnightTopBar extends WMeteorTopBar implements GlazedMidnightWidget {
    private static final Color PRESSED = new Color(98, 150, 255, 235);
    private static final Color HOVERED = new Color(60, 88, 205, 215);
    private static final Color NORMAL = new Color(20, 26, 58, 235);

    @Override
    protected Color getButtonColor(boolean pressed, boolean hovered) {
        return pressed ? PRESSED : hovered ? HOVERED : NORMAL;
    }
}
