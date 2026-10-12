package com.nnpg.glazed.midnight;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.gui.themes.meteor.widgets.pressable.WMeteorCheckbox;
import net.minecraft.util.Mth;

public class WMidnightCheckbox extends WMeteorCheckbox implements GlazedMidnightWidget {
    private double progress;

    public WMidnightCheckbox(boolean checked) {
        super(checked);
        progress = checked ? 1 : 0;
    }

    @Override
    protected void onCalculateSize() {
        super.onCalculateSize();
        height = height * 0.85;
        width = height * 1.8;
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        MeteorGuiTheme theme = theme();
        double u = Math.max(1, theme.scale(1));

        progress += (checked ? 1 : -1) * delta * 12;
        progress = Mth.clamp(progress, 0, 1);

        GlazedMidnightDraw.track(renderer, x, y, width, height, u);

        if (progress > 0) {
            GlazedMidnightDraw.accentPill(renderer, x, y, width, height, height / 2, progress);
        }

        double size = height - u * 4;
        double knobX = x + u * 2 + (width - u * 4 - size) * progress;
        double knobY = y + u * 2;

        GlazedMidnightDraw.circle(renderer, knobX - u * 0.5, knobY + u * 0.5, size + u, 2, 4, 24, 90);

        if (mouseOver) GlazedMidnightDraw.circle(renderer, knobX, knobY, size, 255, 255, 255, 255);
        else GlazedMidnightDraw.circle(renderer, knobX, knobY, size, 226, 236, 255, 255);
    }
}
