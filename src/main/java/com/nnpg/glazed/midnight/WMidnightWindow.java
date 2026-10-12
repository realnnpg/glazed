package com.nnpg.glazed.midnight;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.meteor.widgets.WMeteorWindow;
import meteordevelopment.meteorclient.gui.widgets.WWidget;

public class WMidnightWindow extends WMeteorWindow implements GlazedMidnightWidget {
    public WMidnightWindow(WWidget icon, String title) {
        super(icon, title);
    }

    @Override
    protected WHeader header(WWidget icon) {
        return new MidnightHeader(icon);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        if (expanded || animProgress > 0) {
            double u = Math.max(1, theme().scale(1));
            GlazedMidnightDraw.windowBody(renderer, x, y, width, height, u);
        }
    }

    private class MidnightHeader extends WHeader {
        public MidnightHeader(WWidget icon) {
            super(icon);
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            double u = Math.max(1, theme().scale(1));
            boolean open = WMidnightWindow.this.expanded || WMidnightWindow.this.animProgress > 0;
            GlazedMidnightDraw.header(renderer, x, y, width, height, u, !open);
        }
    }
}
