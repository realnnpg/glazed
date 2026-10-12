package com.nnpg.glazed.midnight;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.meteor.widgets.input.WMeteorDropdown;

public class WMidnightDropdown<T> extends WMeteorDropdown<T> implements GlazedMidnightWidget {
    public WMidnightDropdown(T[] values, T value) {
        super(values, value);
    }

    @Override
    protected WDropdownRoot createRootWidget() {
        return new MidnightRoot();
    }

    @Override
    protected WDropdownValue createValueWidget() {
        return new MidnightValue();
    }

    private static class MidnightRoot extends WDropdownRoot implements GlazedMidnightWidget {
        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            double u = Math.max(1, theme().scale(1));
            GlazedMidnightDraw.popup(renderer, x, y, width, height, u);
        }
    }

    private class MidnightValue extends WDropdownValue implements GlazedMidnightWidget {
        @Override
        protected void onCalculateSize() {
            double pad = pad();

            width = pad + theme.textWidth(value.toString()) + pad;
            height = pad + theme.textHeight() + pad;
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            double u = Math.max(1, theme().scale(1));

            if (pressed || mouseOver) {
                GlazedMidnightDraw.accentPill(renderer, x + u, y, width - u * 2, height, u * 2, pressed ? 1 : 0.6);
            }

            String text = value.toString();
            renderer.text(text, x + width / 2 - theme.textWidth(text) / 2, y + pad(), theme().textColor.get(), false);
        }
    }
}
