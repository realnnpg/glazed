package com.nnpg.glazed.midnight;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;

public final class GlazedMidnightDraw {
    private static final Color[] POOL = new Color[10];
    private static final Color[] TMP = new Color[4];

    private static final int[] WIN_BORDER_T = {92, 142, 255, 255};
    private static final int[] WIN_BORDER_B = {46, 58, 146, 255};
    private static final int[] WIN_FILL_T = {17, 21, 44, 250};
    private static final int[] WIN_FILL_B = {8, 10, 22, 250};

    private static final int[] NORMAL_BORDER_T = {74, 92, 176, 255};
    private static final int[] NORMAL_BORDER_B = {50, 62, 130, 255};
    private static final int[] NORMAL_FILL_T = {28, 35, 68, 255};
    private static final int[] NORMAL_FILL_B = {19, 24, 50, 255};

    private static final int[] HOVER_BORDER_T = {110, 190, 255, 255};
    private static final int[] HOVER_BORDER_B = {72, 118, 235, 255};
    private static final int[] HOVER_FILL_T = {40, 52, 100, 255};
    private static final int[] HOVER_FILL_B = {28, 37, 76, 255};

    private static final int[] PRESS_BORDER_T = {130, 236, 255, 255};
    private static final int[] PRESS_BORDER_B = {96, 156, 255, 255};
    private static final int[] PRESS_FILL_T = {96, 112, 255, 255};
    private static final int[] PRESS_FILL_B = {56, 72, 226, 255};

    private static final int[] INPUT_BORDER = {48, 60, 120, 255};
    private static final int[] INPUT_BORDER_HOVER = {96, 176, 255, 255};
    private static final int[] INPUT_FILL_T = {8, 11, 26, 255};
    private static final int[] INPUT_FILL_B = {14, 18, 38, 255};

    static {
        for (int i = 0; i < POOL.length; i++) POOL[i] = new Color(255, 255, 255, 255);
        for (int i = 0; i < TMP.length; i++) TMP[i] = new Color(255, 255, 255, 255);
    }

    private GlazedMidnightDraw() {
    }

    public static Color c(int slot, int r, int g, int b, int a) {
        return POOL[slot].set(r, g, b, Math.max(0, Math.min(255, a)));
    }

    private static Color c(int slot, int[] v, double alphaScale) {
        return c(slot, v[0], v[1], v[2], (int) Math.round(v[3] * alphaScale));
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static Color at(int slot, double u, double v, Color tl, Color tr, Color br, Color bl) {
        double r = lerp(lerp(tl.r, tr.r, u), lerp(bl.r, br.r, u), v);
        double g = lerp(lerp(tl.g, tr.g, u), lerp(bl.g, br.g, u), v);
        double b = lerp(lerp(tl.b, tr.b, u), lerp(bl.b, br.b, u), v);
        double a = lerp(lerp(tl.a, tr.a, u), lerp(bl.a, br.a, u), v);
        return TMP[slot].set((int) Math.round(r), (int) Math.round(g), (int) Math.round(b), (int) Math.round(a));
    }

    public static void fill(GuiRenderer r, double x, double y, double w, double h, double radius, boolean top, boolean bottom, Color tl, Color tr, Color br, Color bl) {
        if (w <= 0 || h <= 0) return;

        radius = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        double topRadius = top ? radius : 0;
        double bottomRadius = bottom ? radius : 0;
        double midHeight = h - topRadius - bottomRadius;

        if (midHeight > 0) {
            double v0 = topRadius / h;
            double v1 = (h - bottomRadius) / h;
            r.quad(x, y + topRadius, w, midHeight, at(0, 0, v0, tl, tr, br, bl), at(1, 1, v0, tl, tr, br, bl), at(2, 1, v1, tl, tr, br, bl), at(3, 0, v1, tl, tr, br, bl));
        }

        if (radius <= 0 || (!top && !bottom)) return;

        int n = (int) Math.max(2, Math.min(10, Math.round(radius)));
        double t = radius / n;

        for (int i = 0; i < n; i++) {
            double dy = radius - (i + 0.5) * t;
            double inset = radius - Math.sqrt(Math.max(0, radius * radius - dy * dy));
            double ui = inset / w;

            if (top) {
                double v0 = (i * t) / h;
                double v1 = ((i + 1) * t) / h;
                r.quad(x + inset, y + i * t, w - inset * 2, t, at(0, ui, v0, tl, tr, br, bl), at(1, 1 - ui, v0, tl, tr, br, bl), at(2, 1 - ui, v1, tl, tr, br, bl), at(3, ui, v1, tl, tr, br, bl));
            }

            if (bottom) {
                double v0 = (h - (i + 1) * t) / h;
                double v1 = (h - i * t) / h;
                r.quad(x + inset, y + h - (i + 1) * t, w - inset * 2, t, at(0, ui, v0, tl, tr, br, bl), at(1, 1 - ui, v0, tl, tr, br, bl), at(2, 1 - ui, v1, tl, tr, br, bl), at(3, ui, v1, tl, tr, br, bl));
            }
        }
    }

    private static void ring(GuiRenderer r, double x, double y, double w, double h, double radius, double b, int[] borderTop, int[] borderBottom, int[] fillTop, int[] fillBottom) {
        fill(r, x, y, w, h, radius, true, true, c(0, borderTop, 1), c(1, borderTop, 1), c(2, borderBottom, 1), c(3, borderBottom, 1));
        fill(r, x + b, y + b, w - b * 2, h - b * 2, Math.max(0, radius - b), true, true, c(4, fillTop, 1), c(5, fillTop, 1), c(6, fillBottom, 1), c(7, fillBottom, 1));
    }

    public static void windowBody(GuiRenderer r, double x, double y, double w, double h, double u) {
        double radius = u * 6;

        for (int i = 3; i >= 1; i--) {
            double g = u * i;
            Color glow = c(0, 70, 150, 255, 14 + (3 - i) * 12);
            fill(r, x - g, y - g, w + g * 2, h + g * 2, radius + g, true, true, glow, glow, glow, glow);
        }

        ring(r, x, y, w, h, radius, u, WIN_BORDER_T, WIN_BORDER_B, WIN_FILL_T, WIN_FILL_B);
    }

    public static void popup(GuiRenderer r, double x, double y, double w, double h, double u) {
        ring(r, x, y, w, h, u * 3, u, WIN_BORDER_T, WIN_BORDER_B, WIN_FILL_T, WIN_FILL_B);
    }

    public static void header(GuiRenderer r, double x, double y, double w, double h, double u, boolean bottomRounded) {
        double radius = u * 6;

        fill(r, x, y, w, h, radius, true, bottomRounded, c(0, 122, 96, 255, 255), c(1, 40, 208, 255, 255), c(2, 32, 172, 238, 255), c(3, 98, 72, 238, 255));

        r.quad(x + radius * 0.8, y + u, w - radius * 1.6, h * 0.45, c(4, 255, 255, 255, 42), c(5, 255, 255, 255, 42), c(6, 255, 255, 255, 0), c(7, 255, 255, 255, 0));

        if (!bottomRounded) {
            r.quad(x, y + h - u, w, u, c(8, 6, 10, 32, 130));
        }
    }

    public static void control(GuiRenderer r, double x, double y, double w, double h, double u, boolean pressed, boolean hover, boolean input) {
        double radius = u * 3;

        if (input) {
            ring(r, x, y, w, h, radius, u, hover ? INPUT_BORDER_HOVER : INPUT_BORDER, hover ? INPUT_BORDER_HOVER : INPUT_BORDER, INPUT_FILL_T, INPUT_FILL_B);
            return;
        }

        if (pressed) ring(r, x, y, w, h, radius, u, PRESS_BORDER_T, PRESS_BORDER_B, PRESS_FILL_T, PRESS_FILL_B);
        else if (hover) ring(r, x, y, w, h, radius, u, HOVER_BORDER_T, HOVER_BORDER_B, HOVER_FILL_T, HOVER_FILL_B);
        else ring(r, x, y, w, h, radius, u, NORMAL_BORDER_T, NORMAL_BORDER_B, NORMAL_FILL_T, NORMAL_FILL_B);
    }

    public static void track(GuiRenderer r, double x, double y, double w, double h, double u) {
        ring(r, x, y, w, h, h / 2, u, INPUT_BORDER, INPUT_BORDER, INPUT_FILL_T, INPUT_FILL_B);
    }

    public static void accentPill(GuiRenderer r, double x, double y, double w, double h, double radius, double k) {
        fill(r, x, y, w, h, radius, true, true, c(0, 122, 96, 255, (int) Math.round(240 * k)), c(1, 40, 208, 255, (int) Math.round(215 * k)), c(2, 40, 208, 255, (int) Math.round(215 * k)), c(3, 122, 96, 255, (int) Math.round(240 * k)));
    }

    public static void softPill(GuiRenderer r, double x, double y, double w, double h, double radius, double k) {
        Color tint = c(0, 190, 220, 255, (int) Math.round(24 * k));
        fill(r, x, y, w, h, radius, true, true, tint, tint, tint, tint);
    }

    public static void bar(GuiRenderer r, double x, double y, double w, double h, double k) {
        Color bar = c(0, 255, 255, 255, (int) Math.round(235 * k));
        fill(r, x, y, w, h, w / 2, true, true, bar, bar, bar, bar);
    }

    public static void circle(GuiRenderer r, double x, double y, double size, int cr, int cg, int cb, int ca) {
        r.quad(x, y, size, size, GuiRenderer.CIRCLE, c(9, cr, cg, cb, ca));
    }
}
