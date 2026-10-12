package com.nnpg.glazed.liquidglass;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;

public final class GlazedLiquidGlassDraw {
    private static final Color[] POOL = new Color[12];

    static {
        for (int i = 0; i < POOL.length; i++) POOL[i] = new Color(255, 255, 255, 255);
    }

    private GlazedLiquidGlassDraw() {
    }

    public static Color c(int slot, int r, int g, int b, int a) {
        return POOL[slot].set(r, g, b, Math.max(0, Math.min(255, a)));
    }

    public static double time() {
        return System.nanoTime() / 1.0e9;
    }

    public static void panel(GuiRenderer r, double x, double y, double w, double h, double s, int strength) {
        double k = strength / 255.0;

        Color tl = c(0, 128, 66, 220, (int) (112 * k));
        Color tr = c(1, 200, 120, 255, (int) (100 * k));
        Color br = c(2, 74, 32, 160, (int) (136 * k));
        Color bl = c(3, 42, 18, 102, (int) (130 * k));
        r.quad(x, y, w, h, tl, tr, br, bl);

        Color sheenTop = c(4, 255, 255, 255, (int) (50 * k));
        Color sheenBottom = c(5, 255, 255, 255, 0);
        r.quad(x, y, w, h * 0.45, sheenTop, sheenTop, sheenBottom, sheenBottom);

        Color edgeHi = c(6, 235, 205, 255, (int) (150 * k));
        Color edgeLo = c(7, 150, 100, 235, (int) (80 * k));
        r.quad(x, y, w, s, edgeHi);
        r.quad(x, y + h - s, w, s, edgeLo);
        r.quad(x, y + s, s, h - s * 2, edgeHi, edgeHi, edgeLo, edgeLo);
        r.quad(x + w - s, y + s, s, h - s * 2, edgeHi, edgeHi, edgeLo, edgeLo);

        Color gloss = c(8, 255, 255, 255, (int) (70 * k));
        r.quad(x + s * 2, y + s, w - s * 4, s, gloss);
    }

    public static void glow(GuiRenderer r, double x, double y, double w, double h, double s) {
        for (int i = 0; i < 3; i++) {
            double o = i * s;
            Color col = c(9, 180, 110, 255, 34 - i * 11);
            r.quad(x - o - s, y - o - s, w + 2 * (o + s), s, col);
            r.quad(x - o - s, y + h + o, w + 2 * (o + s), s, col);
            r.quad(x - o - s, y - o, s, h + 2 * o, col);
            r.quad(x + w + o, y - o, s, h + 2 * o, col);
        }
    }

    public static void shimmer(GuiRenderer r, double x, double y, double w, double h, int alpha) {
        double bw = Math.min(w * 0.45, 140);
        if (bw <= 0) return;
        double p = 0.5 + 0.5 * Math.sin(time() * 0.9);
        double cx = x + bw / 2 + (w - bw) * p;
        Color clear = c(10, 255, 255, 255, 0);
        Color lit = c(11, 255, 255, 255, alpha);
        r.quad(cx - bw / 2, y, bw / 2, h, clear, lit);
        r.quad(cx, y, bw / 2, h, lit, clear);
    }

    public static void tint(GuiRenderer r, double x, double y, double w, double h, Color base, int alpha) {
        Color left = c(10, base.r, base.g, base.b, alpha);
        Color right = c(11, base.r, base.g, base.b, alpha / 4);
        r.quad(x, y, w, h, left, right);
    }

    public static void hover(GuiRenderer r, double x, double y, double w, double h, double p) {
        Color left = c(10, 255, 255, 255, (int) (38 * p));
        Color right = c(11, 220, 190, 255, (int) (8 * p));
        r.quad(x, y, w, h, left, right);
    }

    public static void glaze(GuiRenderer r, double x, double y, double w, double h, double p) {
        Color left = c(10, 255, 150, 230, (int) (96 * p));
        Color right = c(11, 150, 90, 255, (int) (34 * p));
        r.quad(x, y, w, h, left, right);

        Color top = c(8, 255, 255, 255, (int) (28 * p));
        Color none = c(9, 255, 255, 255, 0);
        r.quad(x, y, w, h * 0.5, top, top, none, none);
    }
}