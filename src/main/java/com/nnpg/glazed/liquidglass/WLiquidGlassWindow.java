package com.nnpg.glazed.liquidglass;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.meteor.widgets.WMeteorWindow;
import meteordevelopment.meteorclient.gui.widgets.WWidget;

public class WLiquidGlassWindow extends WMeteorWindow implements GlazedLiquidGlassWidget {
    public WLiquidGlassWindow(WWidget icon, String title) {
        super(icon, title);
    }

    @Override
    protected WHeader header(WWidget icon) {
        return new GlazedHeader(icon);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        double s = Math.max(1, theme().scale(1));
        GlazedLiquidGlassDraw.glow(renderer, x, y, width, height, s);
        if (expanded || animProgress > 0) {
            GlazedLiquidGlassDraw.panel(renderer, x, y + header.height, width, height - header.height, s, 200);
        }
    }

    private class GlazedHeader extends WHeader {
        public GlazedHeader(WWidget icon) {
            super(icon);
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            double s = Math.max(1, theme().scale(1));
            GlazedLiquidGlassDraw.panel(renderer, x, y, width, height, s, 255);
            GlazedLiquidGlassDraw.tint(renderer, x, y, width, height, theme().accentColor.get(), 90);
            GlazedLiquidGlassDraw.shimmer(renderer, x, y, width, height, 30);
        }
    }
}