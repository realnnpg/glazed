package com.nnpg.glazed.liquidglass;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorWidget;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.utils.render.color.Color;

public interface GlazedLiquidGlassWidget extends MeteorWidget {
    @Override
    default void renderBackground(GuiRenderer renderer, WWidget widget, boolean pressed, boolean mouseOver) {
        double s = Math.max(1, theme().scale(1));
        int strength = pressed ? 255 : mouseOver ? 215 : 160;
        GlazedLiquidGlassDraw.panel(renderer, widget.x, widget.y, widget.width, widget.height, s, strength);
    }

}
