package com.nnpg.glazed.midnight;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.gui.themes.meteor.widgets.input.WMeteorSlider;

public class WMidnightSlider extends WMeteorSlider implements GlazedMidnightWidget {
    public WMidnightSlider(double value, double min, double max) {
        super(value, min, max);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        MeteorGuiTheme theme = theme();
        double u = Math.max(1, theme.scale(1));

        double valueWidth = valueWidth();
        double hs = handleSize();
        double ts = theme.scale(5);
        double tx = x + hs / 2;
        double ty = y + height / 2 - ts / 2;
        double tw = width - hs;

        GlazedMidnightDraw.track(renderer, tx, ty, tw, ts, u);

        if (valueWidth > 0) {
            GlazedMidnightDraw.accentPill(renderer, tx, ty, valueWidth, ts, ts / 2, 1);
        }

        double hx = x + valueWidth;
        double hy = y;

        int haloAlpha = dragging ? 90 : handleMouseOver ? 60 : 0;
        if (haloAlpha > 0) {
            GlazedMidnightDraw.circle(renderer, hx - hs * 0.3, hy - hs * 0.3, hs * 1.6, 80, 190, 255, haloAlpha);
        }

        GlazedMidnightDraw.circle(renderer, hx - u * 0.5, hy + u * 0.5, hs + u, 2, 4, 24, 90);
        GlazedMidnightDraw.circle(renderer, hx, hy, hs, 70, 200, 255, 255);
        GlazedMidnightDraw.circle(renderer, hx + u * 1.5, hy + u * 1.5, hs - u * 3, 236, 246, 255, 255);
    }
}
