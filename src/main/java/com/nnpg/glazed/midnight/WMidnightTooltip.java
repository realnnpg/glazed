package com.nnpg.glazed.midnight;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.meteor.widgets.WMeteorTooltip;

public class WMidnightTooltip extends WMeteorTooltip implements GlazedMidnightWidget {
    public WMidnightTooltip(String text) {
        super(text);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        double u = Math.max(1, theme().scale(1));
        GlazedMidnightDraw.popup(renderer, x, y, width, height, u);
    }
}
