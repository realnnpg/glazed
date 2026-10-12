package com.nnpg.glazed.liquidglass;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.gui.themes.meteor.widgets.pressable.WMeteorCheckbox;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.util.Mth;

public class WLiquidGlassCheckbox extends WMeteorCheckbox implements GlazedLiquidGlassWidget {
    private double progress;

    public WLiquidGlassCheckbox(boolean checked) {
        super(checked);
        progress = checked ? 1 : 0;
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        MeteorGuiTheme theme = theme();

        progress += (checked ? 1 : -1) * delta * 14;
        progress = Mth.clamp(progress, 0, 1);

        renderBackground(renderer, this, pressed, mouseOver);

        if (progress > 0) {
            double cs = (width - theme.scale(2)) / 1.6 * progress;
            double cx = x + (width - cs) / 2;
            double cy = y + (height - cs) / 2;
            double g = theme.scale(2);

            renderer.quad(cx - g, cy - g, cs + g * 2, cs + g * 2, GlazedLiquidGlassDraw.c(9, 200, 130, 255, (int) (50 * progress)));

            Color tl = GlazedLiquidGlassDraw.c(8, 255, 170, 235, 255);
            Color tr = GlazedLiquidGlassDraw.c(9, 195, 120, 255, 255);
            Color br = GlazedLiquidGlassDraw.c(10, 135, 75, 245, 255);
            Color bl = GlazedLiquidGlassDraw.c(11, 195, 120, 255, 255);
            renderer.quad(cx, cy, cs, cs, tl, tr, br, bl);

            Color shine = GlazedLiquidGlassDraw.c(7, 255, 255, 255, 90);
            Color none = GlazedLiquidGlassDraw.c(6, 255, 255, 255, 0);
            renderer.quad(cx, cy, cs, cs * 0.5, shine, shine, none, none);
        }
    }
}