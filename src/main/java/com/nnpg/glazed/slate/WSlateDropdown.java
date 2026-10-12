package com.nnpg.glazed.slate;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.meteor.widgets.input.WMeteorDropdown;

public class WSlateDropdown<T> extends WMeteorDropdown<T> implements GlazedSlateWidget {
    public WSlateDropdown(T[] values, T value) {
        super(values, value);
    }

    @Override
    protected WDropdownRoot createRootWidget() {
        return new SlateRoot();
    }

    @Override
    protected WDropdownValue createValueWidget() {
        return new SlateValue();
    }

    private static class SlateRoot extends WDropdownRoot implements GlazedSlateWidget {
        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            double u = Math.max(1, theme().scale(1));
            GlazedSlateDraw.panel(renderer, x, y, width, height, u);
        }
    }

    private class SlateValue extends WDropdownValue implements GlazedSlateWidget {
        @Override
        protected void onCalculateSize() {
            double pad = pad();

            width = pad + theme.textWidth(value.toString()) + pad;
            height = pad + theme.textHeight() + pad;
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            double u = Math.max(1, theme().scale(1));

            if (pressed || mouseOver) GlazedSlateDraw.rowActive(renderer, x, y, width, height, u, pressed ? 1 : 0.7);

            String text = value.toString();
            renderer.text(text, x + width / 2 - theme.textWidth(text) / 2, y + pad(), theme().textColor.get(), false);
        }
    }
}
