package com.nnpg.glazed.liquidglass;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.renderer.packer.GuiTexture;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.gui.themes.meteor.widgets.pressable.WMeteorConfirmedButton;
import meteordevelopment.meteorclient.utils.render.color.Color;

public class WLiquidGlassConfirmedButton extends WMeteorConfirmedButton implements GlazedLiquidGlassWidget {
    public WLiquidGlassConfirmedButton(String text, String confirmText, GuiTexture texture) {
        super(text, confirmText, texture);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        MeteorGuiTheme theme = theme();
        double pad = pad();

        renderBackground(renderer, this, pressed, mouseOver);
        if (pressedOnce) GlazedLiquidGlassDraw.glaze(renderer, x, y, width, height, 1);

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
