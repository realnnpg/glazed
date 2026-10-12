package com.nnpg.glazed.liquidglass;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.meteor.widgets.WMeteorTooltip;

public class WLiquidGlassTooltip extends WMeteorTooltip implements GlazedLiquidGlassWidget {
    public WLiquidGlassTooltip(String text) {
        super(text);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        double s = Math.max(1, theme().scale(1));
        GlazedLiquidGlassDraw.panel(renderer, x, y, width, height, s, 240);
    }
}
