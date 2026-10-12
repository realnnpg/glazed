package com.nnpg.glazed.slate;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.meteor.widgets.WMeteorWindow;
import meteordevelopment.meteorclient.gui.widgets.WWidget;

import java.util.Locale;

public class WSlateWindow extends WMeteorWindow implements GlazedSlateWidget {
    public WSlateWindow(WWidget icon, String title) {
        super(icon, title.toUpperCase(Locale.ROOT));
    }

    @Override
    protected WHeader header(WWidget icon) {
        return new SlateHeader(icon);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        double u = Math.max(1, theme().scale(1));

        if (expanded || animProgress > 0) {
            GlazedSlateDraw.panel(renderer, x, y, width, height, u);
        } else {
            GlazedSlateDraw.shadow(renderer, x, y, width, header.height, u);
        }
    }

    private class SlateHeader extends WHeader {
        public SlateHeader(WWidget icon) {
            super(icon);
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            double u = Math.max(1, theme().scale(1));
            boolean open = WSlateWindow.this.expanded || WSlateWindow.this.animProgress > 0;
            GlazedSlateDraw.header(renderer, x, y, width, height, u, open);
        }
    }
}
