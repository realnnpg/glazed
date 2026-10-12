package com.nnpg.glazed.slate;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.gui.themes.meteor.widgets.pressable.WMeteorConfirmedMinus;

public class WSlateConfirmedMinus extends WMeteorConfirmedMinus implements GlazedSlateWidget {
    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        MeteorGuiTheme theme = theme();
        double pad = pad();
        double s = theme.scale(3);
        double u = Math.max(1, theme.scale(1));

        renderBackground(renderer, this, pressed, mouseOver);
        if (pressedOnce) GlazedSlateDraw.redWash(renderer, x + u, y + u, width - u * 2, height - u * 2, 130);

        renderer.quad(x + pad, y + height / 2 - s / 2, width - pad * 2, s, theme.minusColor.get());
    }
}
