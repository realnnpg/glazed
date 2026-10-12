package com.nnpg.glazed.slate;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorWidget;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.utils.render.color.Color;

public interface GlazedSlateWidget extends MeteorWidget {
    @Override
    default void renderBackground(GuiRenderer renderer, WWidget widget, boolean pressed, boolean mouseOver) {
        double u = Math.max(1, theme().scale(1));
        GlazedSlateDraw.control(renderer, widget.x, widget.y, widget.width, widget.height, u, pressed, mouseOver, widget instanceof WTextBox);
    }

    @Override
    default void renderBackground(GuiRenderer renderer, WWidget widget, Color outline, Color bg) {
        double u = Math.max(1, theme().scale(1));
        GlazedSlateDraw.control(renderer, widget.x, widget.y, widget.width, widget.height, u, false, false, false);
    }
}
