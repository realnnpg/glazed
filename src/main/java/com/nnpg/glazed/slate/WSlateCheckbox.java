package com.nnpg.glazed.slate;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.gui.themes.meteor.widgets.pressable.WMeteorCheckbox;
import net.minecraft.util.Mth;

public class WSlateCheckbox extends WMeteorCheckbox implements GlazedSlateWidget {
    private double progress;

    public WSlateCheckbox(boolean checked) {
        super(checked);
        progress = checked ? 1 : 0;
    }

    @Override
    protected void onCalculateSize() {
        super.onCalculateSize();
        height = height * 0.72;
        width = height * 1.9;
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        MeteorGuiTheme theme = theme();
        double u = Math.max(1, theme.scale(1));

        progress += (checked ? 1 : -1) * delta * 12;
        progress = Mth.clamp(progress, 0, 1);

        GlazedSlateDraw.toggle(renderer, x, y, width, height, u, progress);
    }
}
