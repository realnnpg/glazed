package com.nnpg.glazed.midnight;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.renderer.packer.GuiTexture;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.gui.themes.meteor.widgets.pressable.WMeteorConfirmedButton;
import meteordevelopment.meteorclient.utils.render.color.Color;

public class WMidnightConfirmedButton extends WMeteorConfirmedButton implements GlazedMidnightWidget {
    public WMidnightConfirmedButton(String text, String confirmText, GuiTexture texture) {
        super(text, confirmText, texture);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        MeteorGuiTheme theme = theme();
        double pad = pad();
        double u = Math.max(1, theme.scale(1));

        renderBackground(renderer, this, pressed, mouseOver);
        if (pressedOnce) GlazedMidnightDraw.accentPill(renderer, x, y, width, height, u * 3, 1);

        Color fg = theme.textColor.get();
        String text = getText();

        if (text != null) {
            renderer.text(text, x + width / 2 - textWidth / 2, y + pad, fg, false);
        } else {
            double ts = theme.textHeight();
            renderer.quad(x + width / 2 - ts / 2, y + pad, ts, ts, texture, fg);
        }
    }
}
