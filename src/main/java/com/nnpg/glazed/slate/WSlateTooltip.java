package com.nnpg.glazed.slate;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.meteor.widgets.WMeteorTooltip;

public class WSlateTooltip extends WMeteorTooltip implements GlazedSlateWidget {
    public WSlateTooltip(String text) {
        super(text);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        double u = Math.max(1, theme().scale(1));
        GlazedSlateDraw.panel(renderer, x, y, width, height, u);
    }
}
