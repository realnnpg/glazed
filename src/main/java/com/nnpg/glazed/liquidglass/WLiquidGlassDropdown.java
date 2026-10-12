package com.nnpg.glazed.liquidglass;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.meteor.widgets.input.WMeteorDropdown;

public class WLiquidGlassDropdown<T> extends WMeteorDropdown<T> implements GlazedLiquidGlassWidget {
    public WLiquidGlassDropdown(T[] values, T value) {
        super(values, value);
    }

    @Override
    protected WDropdownRoot createRootWidget() {
        return new GlassRoot();
    }

    @Override
    protected WDropdownValue createValueWidget() {
        return new GlassValue();
    }

    private static class GlassRoot extends WDropdownRoot implements GlazedLiquidGlassWidget {
        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            double s = Math.max(1, theme().scale(1));
            GlazedLiquidGlassDraw.panel(renderer, x, y, width, height, s, 235);
        }
    }

    private class GlassValue extends WDropdownValue implements GlazedLiquidGlassWidget {
        @Override
        protected void onCalculateSize() {
            double pad = pad();

            width = pad + theme.textWidth(value.toString()) + pad;
            height = pad + theme.textHeight() + pad;
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            if (pressed || mouseOver) {
                GlazedLiquidGlassDraw.glaze(renderer, x, y, width, height, pressed ? 1 : 0.6);
            }

            String text = value.toString();
            renderer.text(text, x + width / 2 - theme.textWidth(text) / 2, y + pad(), theme().textColor.get(), false);
        }
    }
}
