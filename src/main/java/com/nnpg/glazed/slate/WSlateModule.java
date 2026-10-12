package com.nnpg.glazed.slate;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.gui.widgets.pressable.WPressable;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.util.Mth;

import java.util.Locale;

import static com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT;
import static com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT;
import static meteordevelopment.meteorclient.MeteorClient.mc;

public class WSlateModule extends WPressable implements GlazedSlateWidget {
    private final Module module;
    private final String[] glyphs;
    private final double[] advances;
    private final Color textColor = new Color(255, 255, 255, 255);

    private double spacing;
    private double titleWidth;
    private double toggleWidth;
    private double toggleHeight;
    private double hoverProgress;
    private double activeProgress;

    public WSlateModule(Module module, String title) {
        this.module = module;
        this.tooltip = module.description;

        String display = title.toUpperCase(Locale.ROOT);
        glyphs = new String[display.length()];
        advances = new double[display.length()];
        for (int i = 0; i < display.length(); i++) glyphs[i] = String.valueOf(display.charAt(i));

        hoverProgress = 0;
        activeProgress = module.isActive() ? 1 : 0;
    }

    @Override
    public double pad() {
        return theme.scale(4);
    }

    @Override
    protected void onCalculateSize() {
        double pad = pad();

        spacing = theme.scale(0.8);

        double total = 0;
        for (int i = 0; i < glyphs.length; i++) {
            advances[i] = theme.textWidth(glyphs[i]);
            total += advances[i];
        }
        if (glyphs.length > 1) total += spacing * (glyphs.length - 1);

        titleWidth = total;
        toggleHeight = theme.textHeight() * 0.66;
        toggleWidth = toggleHeight * 1.9;

        width = pad * 2 + titleWidth + pad * 3 + toggleWidth;
        height = pad * 2 + theme.textHeight();
    }

    @Override
    protected void onPressed(int button) {
        if (button == MOUSE_BUTTON_LEFT) module.toggle();
        else if (button == MOUSE_BUTTON_RIGHT) mc.setScreen(theme.moduleScreen(module));
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        MeteorGuiTheme theme = theme();
        double pad = pad();
        double u = Math.max(1, theme.scale(1));

        hoverProgress += delta * 6 * (mouseOver ? 1 : -1);
        hoverProgress = Mth.clamp(hoverProgress, 0, 1);

        activeProgress += delta * 7 * (module.isActive() ? 1 : -1);
        activeProgress = Mth.clamp(activeProgress, 0, 1);

        if (hoverProgress > 0) GlazedSlateDraw.rowHover(renderer, x, y, width, height, u, hoverProgress);
        if (activeProgress > 0) GlazedSlateDraw.rowActive(renderer, x, y, width, height, u, activeProgress);

        double toggleX = x + width - pad - u * 2 - toggleWidth;
        double toggleY = y + height / 2 - toggleHeight / 2;
        GlazedSlateDraw.toggle(renderer, toggleX, toggleY, toggleWidth, toggleHeight, u, activeProgress);

        double lit = Math.max(activeProgress, hoverProgress * 0.5);
        textColor.set((int) (146 + 90 * lit), (int) (158 + 88 * lit), (int) (166 + 78 * lit), 255);

        double cx = x + pad + u * 3;
        double ty = y + pad;

        for (int i = 0; i < glyphs.length; i++) {
            renderer.text(glyphs[i], cx, ty, textColor, false);
            cx += advances[i] + spacing;
        }
    }
}
