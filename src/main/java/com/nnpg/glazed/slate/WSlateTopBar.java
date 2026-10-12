package com.nnpg.glazed.slate;

import meteordevelopment.meteorclient.gui.themes.meteor.widgets.WMeteorTopBar;
import meteordevelopment.meteorclient.utils.render.color.Color;

public class WSlateTopBar extends WMeteorTopBar implements GlazedSlateWidget {
    private static final Color PRESSED = new Color(62, 128, 122, 255);
    private static final Color HOVERED = new Color(38, 46, 54, 255);
    private static final Color NORMAL = new Color(23, 27, 31, 255);

    @Override
    protected Color getButtonColor(boolean pressed, boolean hovered) {
        return pressed ? PRESSED : hovered ? HOVERED : NORMAL;
    }
}
