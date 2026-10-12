package com.nnpg.glazed.liquidglass;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.gui.themes.meteor.widgets.input.WMeteorSlider;
import meteordevelopment.meteorclient.utils.render.color.Color;

public class WLiquidGlassSlider extends WMeteorSlider implements GlazedLiquidGlassWidget {
    public WLiquidGlassSlider(double value, double min, double max) {
        super(value, min, max);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        MeteorGuiTheme theme = theme();

        double valueWidth = valueWidth();
        double hs = handleSize();
        double ts = theme.scale(4);
        double tx = x + hs / 2;
        double ty = y + height / 2 - ts / 2;
        double tw = width - hs;

        renderer.quad(tx, ty, tw, ts, GlazedLiquidGlassDraw.c(8, 24, 10, 60, 170));
        renderer.quad(tx, ty, tw, 1, GlazedLiquidGlassDraw.c(9, 255, 255, 255, 40));

        Color left = GlazedLiquidGlassDraw.c(10, 140, 85, 255, 235);
        Color right = GlazedLiquidGlassDraw.c(11, 255, 160, 232, 245);
        renderer.quad(tx, ty, valueWidth, ts, left, right);

        double hx = x + valueWidth;
        double halo = hs * 0.4;
        int haloAlpha = dragging ? 95 : handleMouseOver ? 62 : 38;
        renderer.quad(hx - halo, y - halo, hs + halo * 2, hs + halo * 2, GuiRenderer.CIRCLE, GlazedLiquidGlassDraw.c(8, 215, 150, 255, haloAlpha));
        renderer.quad(hx, y, hs, hs, GuiRenderer.CIRCLE, GlazedLiquidGlassDraw.c(9, 236, 214, 255, 255));
        renderer.quad(hx + hs * 0.18, y + hs * 0.1, hs * 0.5, hs * 0.4, GuiRenderer.CIRCLE, GlazedLiquidGlassDraw.c(10, 255, 255, 255, 170));
    }
}