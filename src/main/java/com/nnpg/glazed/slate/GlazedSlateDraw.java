package com.nnpg.glazed.slate;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;

public final class GlazedSlateDraw {
    private static final Color[] POOL = new Color[6];

    private static final int[] PANEL_BORDER = {44, 53, 60};
    private static final int[] PANEL_FILL = {23, 27, 31};
    private static final int[] HEADER_FILL = {29, 34, 40};

    private static final int[] TEAL = {96, 176, 168};
    private static final int[] TEAL_DIM = {70, 132, 128};
    private static final int[] TEAL_FILL = {62, 128, 122};

    private static final int[] BUTTON_FILL = {30, 36, 42};
    private static final int[] BUTTON_HOVER = {40, 48, 56};
    private static final int[] BUTTON_BORDER = {52, 62, 70};

    private static final int[] INPUT_FILL = {16, 19, 23};
    private static final int[] INPUT_BORDER = {46, 56, 62};

    private static final int[] ROW_ACTIVE = {44, 56, 60};

    static {
        for (int i = 0; i < POOL.length; i++) POOL[i] = new Color(255, 255, 255, 255);
    }

    private GlazedSlateDraw() {
    }

    public static Color c(int slot, int r, int g, int b, int a) {
        return POOL[slot].set(r, g, b, Math.max(0, Math.min(255, a)));
    }

    private static Color c(int slot, int[] v, int a) {
        return c(slot, v[0], v[1], v[2], a);
    }

    private static int mix(int a, int b, double t) {
        return (int) Math.round(a + (b - a) * t);
    }

    public static void shadow(GuiRenderer r, double x, double y, double w, double h, double u) {
        for (int i = 3; i >= 1; i--) {
            double g = u * i;
            r.quad(x - g, y - g + u, w + g * 2, h + g * 2, c(0, 0, 0, 0, 12 + (3 - i) * 10));
        }
    }

    public static void panel(GuiRenderer r, double x, double y, double w, double h, double u) {
        shadow(r, x, y, w, h, u);
        r.quad(x, y, w, h, c(0, PANEL_BORDER, 255));
        r.quad(x + u, y + u, w - u * 2, h - u * 2, c(1, PANEL_FILL, 255));
    }

    public static void header(GuiRenderer r, double x, double y, double w, double h, double u, boolean open) {
        r.quad(x, y, w, h, c(0, PANEL_BORDER, 255));
        r.quad(x + u, y + u, w - u * 2, h - (open ? u : u * 2), c(1, HEADER_FILL, 255));
        r.quad(x + u * 4, y + u, u * 16, u, c(2, TEAL, 255));
    }

    public static void control(GuiRenderer r, double x, double y, double w, double h, double u, boolean pressed, boolean hover, boolean input) {
        int[] border;
        int[] fill;

        if (input) {
            border = hover ? TEAL_DIM : INPUT_BORDER;
            fill = INPUT_FILL;
        } else if (pressed) {
            border = TEAL;
            fill = TEAL_FILL;
        } else if (hover) {
            border = TEAL_DIM;
            fill = BUTTON_HOVER;
        } else {
            border = BUTTON_BORDER;
            fill = BUTTON_FILL;
        }

        r.quad(x, y, w, h, c(0, border, 255));
        r.quad(x + u, y + u, w - u * 2, h - u * 2, c(1, fill, 255));
    }

    public static void rowHover(GuiRenderer r, double x, double y, double w, double h, double u, double k) {
        r.quad(x + u * 2, y + u, w - u * 4, h - u * 2, c(0, 255, 255, 255, (int) Math.round(10 * k)));
    }

    public static void rowActive(GuiRenderer r, double x, double y, double w, double h, double u, double k) {
        int a = (int) Math.round(255 * k);
        r.quad(x + u * 2, y + u, w - u * 4, h - u * 2, c(0, ROW_ACTIVE, a));
        r.quad(x + u * 4, y + u, u * 9, u, c(1, TEAL, a));
    }

    public static void toggle(GuiRenderer r, double x, double y, double w, double h, double u, double p) {
        Color border = c(0, mix(58, 110, p), mix(68, 190, p), mix(76, 182, p), 255);
        r.quad(x, y, w, h, border);

        Color fill = c(1, mix(33, 82, p), mix(39, 162, p), mix(46, 154, p), 255);
        r.quad(x + u, y + u, w - u * 2, h - u * 2, fill);

        double knob = h - u * 4;
        double kx = x + u * 2 + (w - u * 4 - knob) * p;
        Color knobColor = c(2, mix(140, 236, p), mix(152, 246, p), mix(160, 246, p), 255);
        r.quad(kx, y + u * 2, knob, knob, knobColor);
    }

    public static void track(GuiRenderer r, double x, double y, double w, double h, double u) {
        r.quad(x, y, w, h, c(0, 36, 42, 50, 255));
    }

    public static void fillBar(GuiRenderer r, double x, double y, double w, double h) {
        r.quad(x, y, w, h, c(0, TEAL, 255));
    }

    public static void knob(GuiRenderer r, double x, double y, double size, boolean lit) {
        r.quad(x, y, size, size, lit ? c(0, 236, 248, 246, 255) : c(0, 190, 214, 212, 255));
    }

    public static void tealWash(GuiRenderer r, double x, double y, double w, double h, int alpha) {
        r.quad(x, y, w, h, c(0, TEAL, alpha));
    }

    public static void redWash(GuiRenderer r, double x, double y, double w, double h, int alpha) {
        r.quad(x, y, w, h, c(0, 230, 90, 100, alpha));
    }
}
