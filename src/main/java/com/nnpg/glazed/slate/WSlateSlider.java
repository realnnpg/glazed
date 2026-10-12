package com.nnpg.glazed.slate;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.gui.themes.meteor.widgets.input.WMeteorSlider;

public class WSlateSlider extends WMeteorSlider implements GlazedSlateWidget {
    public WSlateSlider(double value, double min, double max) {
        super(value, min, max);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        MeteorGuiTheme theme = theme();
        double u = Math.max(1, theme.scale(1));

        double valueWidth = valueWidth();
        double hs = handleSize();
        double ts = u * 2;
        double tx = x + hs / 2;
        double ty = y + height / 2 - ts / 2;
        double tw = width - hs;

        GlazedSlateDraw.track(renderer, tx, ty, tw, ts, u);
        if (valueWidth > 0) GlazedSlateDraw.fillBar(renderer, tx, ty, valueWidth, ts);

        double size = hs * 0.7;
        double kx = x + valueWidth + (hs - size) / 2;
        double ky = y + height / 2 - size / 2;
        GlazedSlateDraw.knob(renderer, kx, ky, size, dragging || handleMouseOver);
    }
}
