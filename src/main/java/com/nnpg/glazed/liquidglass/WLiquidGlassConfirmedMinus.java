package com.nnpg.glazed.liquidglass;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.gui.themes.meteor.widgets.pressable.WMeteorConfirmedMinus;

public class WLiquidGlassConfirmedMinus extends WMeteorConfirmedMinus implements GlazedLiquidGlassWidget {
    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        MeteorGuiTheme theme = theme();
        double pad = pad();
        double s = theme.scale(3);

        renderBackground(renderer, this, pressed, mouseOver);
        if (pressedOnce) renderer.quad(x, y, width, height, GlazedLiquidGlassDraw.c(8, 255, 100, 140, 120));

        renderer.quad(x + pad, y + height / 2 - s / 2, width - pad * 2, s, theme.minusColor.get());
    }
}
