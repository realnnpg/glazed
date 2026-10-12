package com.nnpg.glazed.midnight;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.gui.utils.AlignmentX;
import meteordevelopment.meteorclient.gui.widgets.pressable.WPressable;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.util.Mth;

import java.util.Locale;

import static com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT;
import static com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT;
import static meteordevelopment.meteorclient.MeteorClient.mc;

public class WMidnightModule extends WPressable implements GlazedMidnightWidget {
    private final Module module;
    private final String[] glyphs;
    private final double[] advances;
    private final Color textColor = new Color(255, 255, 255, 255);

    private double spacing;
    private double boldOffset;
    private double titleWidth;
    private double hoverProgress;
    private double activeProgress;

    public WMidnightModule(Module module, String title) {
        this.module = module;
        this.tooltip = module.description;

        String display = title.toUpperCase(Locale.ROOT);
        glyphs = new String[display.length()];
        advances = new double[display.length()];
        for (int i = 0; i < display.length(); i++) glyphs[i] = String.valueOf(display.charAt(i));

        hoverProgress = module.isActive() ? 1 : 0;
        activeProgress = module.isActive() ? 1 : 0;
    }

    @Override
    public double pad() {
        return theme.scale(4);
    }

    @Override
    protected void onCalculateSize() {
        double pad = pad();

        spacing = theme.scale(1.2);
        boldOffset = theme.scale(0.5);

        double total = 0;
        for (int i = 0; i < glyphs.length; i++) {
            advances[i] = theme.textWidth(glyphs[i]);
            total += advances[i];
        }
        if (glyphs.length > 1) total += spacing * (glyphs.length - 1);

        titleWidth = total + boldOffset;
        width = pad + titleWidth + pad;
        height = pad + theme.textHeight() + pad;
    }

    @Override
    protected void onPressed(int button) {
        if (button == MOUSE_BUTTON_LEFT) module.toggle();
        else if (button == MOUSE_BUTTON_RIGHT) mc.gui.setScreen(theme.moduleScreen(module));
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        MeteorGuiTheme theme = theme();
        double pad = pad();
        double u = Math.max(1, theme.scale(1));

        hoverProgress += delta * 5 * ((module.isActive() || mouseOver) ? 1 : -1);
        hoverProgress = Mth.clamp(hoverProgress, 0, 1);

        activeProgress += delta * 6 * (module.isActive() ? 1 : -1);
        activeProgress = Mth.clamp(activeProgress, 0, 1);

        double ix = x + u * 2;
        double iy = y + u;
        double iw = width - u * 4;
        double ih = height - u * 2;

        if (hoverProgress > 0) GlazedMidnightDraw.softPill(renderer, ix, iy, iw, ih, u * 4, hoverProgress);

        if (activeProgress > 0) {
            GlazedMidnightDraw.accentPill(renderer, ix, iy, iw, ih, u * 4, activeProgress);
            GlazedMidnightDraw.bar(renderer, ix + u * 2, iy + ih * 0.2, u * 2, ih * 0.6, activeProgress);
        }

        double tx = this.x + pad;
        double w = width - pad * 2;

        if (theme.moduleAlignment.get() == AlignmentX.Center) {
            tx += w / 2 - titleWidth / 2;
        } else if (theme.moduleAlignment.get() == AlignmentX.Right) {
            tx += w - titleWidth;
        } else {
            tx += u * 3 * activeProgress;
        }

        double lit = Math.max(activeProgress, hoverProgress * 0.6);
        int red = (int) (170 + 70 * lit);
        int green = (int) (186 + 62 * lit);
        int blue = (int) (226 + 29 * lit);

        textColor.set(red, green, blue, 255);

        double cx = tx;
        double ty = y + pad;

        for (int i = 0; i < glyphs.length; i++) {
            renderer.text(glyphs[i], cx, ty, textColor, false);
            renderer.text(glyphs[i], cx + boldOffset, ty, textColor, false);
            cx += advances[i] + spacing;
        }
    }
}
