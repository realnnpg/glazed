package com.nnpg.glazed.antivanish;

import meteordevelopment.meteorclient.settings.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.renderer.RenderPipelines;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class StaffPanel {
    public enum Presence {
        ONLINE,
        SPECTATOR,
        VANISHED
    }

    public record Row(UUID id, String name, Presence presence, boolean marked, long provenAt, boolean nearby, float distance, long highlightUntil) {
    }

    public static final String WATCHING_YOU = "WATCHING YOU";

    private static final int WIDTH = 168;
    private static final int HEADER_H = 22;
    private static final int ROW_H = 20;
    private static final int PAD = 8;
    private static final int HEAD = 16;
    private static final int RADIUS = 10;
    private static final long ENTER_MS = 350L;
    private static final long EXIT_MS = 300L;
    private static final long ALERT_MS = 4000L;
    private static final double PULSE_MS = 500.0;

    private static final int BG_RGB = 0x0A0A0E;
    private static final int TITLE = 0xFFFFFFFF;
    private static final int NAME = 0xFFF2F2F8;
    private static final int EMPTY = 0xFFA0A0AA;
    private static final int DIM = 0xFFA0A4AC;
    private static final int WARN = 0xFFFF453A;
    private static final int FACE_FALLBACK = 0xFF2A2A2A;

    private final List<Line> lines = new ArrayList<>();
    private int more;
    private boolean dragging;
    private double dragOffX;
    private double dragOffY;

    public void clear() {
        lines.clear();
        more = 0;
        dragging = false;
    }

    public void sync(List<Row> current, int more, boolean initialSeed) {
        long now = System.currentTimeMillis();
        this.more = more;

        Map<String, Row> byKey = new LinkedHashMap<>();
        Map<String, Integer> orders = new LinkedHashMap<>();
        int index = 0;
        for (Row row : current) {
            String key = row.id().toString();
            byKey.put(key, row);
            orders.put(key, index++);
        }

        if (initialSeed) {
            lines.clear();
            for (Row row : current) {
                Line line = new Line(row, false);
                line.order = orders.get(line.key);
                lines.add(line);
            }
            lines.sort(ORDER);
            return;
        }

        for (Line line : lines) {
            Row row = byKey.get(line.key);

            if (row != null) {
                line.row = row;
                line.order = orders.get(line.key);

                if (line.leaving) {
                    line.leaving = false;
                    line.entering = true;
                    line.since = now;
                }

                continue;
            }

            if (!line.leaving) {
                line.leaving = true;
                line.entering = false;
                line.since = now;
            }
        }

        for (Row row : current) {
            if (find(row.id().toString()) != null) continue;

            Line line = new Line(row, true);
            line.order = orders.get(line.key);
            lines.add(line);
        }

        lines.sort(ORDER);
        prune(now);
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    public void handleDrag(Setting<Integer> hudX, Setting<Integer> hudY, Setting<Integer> size) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.getWindow() == null || !(mc.gui.screen() instanceof ChatScreen)) {
            dragging = false;
            return;
        }

        float scale = scaleOf(size);
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int panelW = measureWidth(scale);
        int panelH = scaled(panelHeight(), scale);
        int headerH = scaled(HEADER_H, scale);
        int x = resolveX(hudX, screenW, panelW);
        int y = resolveY(hudY, screenH, scale);

        double windowScale = mc.getWindow().getGuiScale();
        double mouseX = mc.mouseHandler.xpos() / windowScale;
        double mouseY = mc.mouseHandler.ypos() / windowScale;
        boolean mouseDown = GLFW.glfwGetMouseButton(mc.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_1) == GLFW.GLFW_PRESS;
        boolean overHeader = mouseX >= x && mouseX < x + panelW && mouseY >= y && mouseY < y + headerH;

        if (mouseDown && overHeader && !dragging) {
            dragging = true;
            dragOffX = mouseX - x;
            dragOffY = mouseY - y;
        }

        if (!mouseDown) {
            dragging = false;
            return;
        }

        if (!dragging) return;

        int newX = (int) Math.round(Math.max(0, Math.min(mouseX - dragOffX, screenW - panelW)));
        int newY = (int) Math.round(Math.max(0, Math.min(mouseY - dragOffY, screenH - panelH)));

        if (hudX.get() != newX) hudX.set(newX);
        if (hudY.get() != newY) hudY.set(newY);
    }

    public void render(GuiGraphicsExtractor g, Setting<Integer> hudX, Setting<Integer> hudY, Setting<Integer> size, int opacity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null || mc.font == null) return;

        float scale = scaleOf(size);
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int w = measureWidth(scale);
        int h = scaled(panelHeight(), scale);
        int x = resolveX(hudX, screenW, w);
        int y = resolveY(hudY, screenH, scale);
        int pad = scaled(PAD, scale);
        int head = Math.max(8, scaled(HEAD, scale));
        int headerH = scaled(HEADER_H, scale);
        int rowH = scaled(ROW_H, scale);
        int radius = Math.max(3, scaled(RADIUS, scale));
        int bgAlpha = Math.max(0, Math.min(255, Math.round(opacity / 100f * 255f)));

        long now = System.currentTimeMillis();
        prune(now);

        SmoothRoundRect.draw(g, x + 2, y + 2, w, h, radius, 0x55000000);
        SmoothRoundRect.draw(g, x, y, w, h, radius, (bgAlpha << 24) | BG_RGB);

        drawText(g, "Online Admin List", x + pad, y + scaled(7, scale), TITLE, scale, 1.05f);

        int liveCount = 0;
        for (Line line : lines) {
            if (!line.leaving) liveCount++;
        }

        if (liveCount > 0 || more > 0) {
            String count = Integer.toString(liveCount + more);
            drawText(g, count, x + w - pad - textWidth(count, scale, 1.05f), y + scaled(7, scale), DIM, scale, 1.05f);
        }

        int rowY = y + headerH;
        boolean any = false;

        for (Line line : lines) {
            float progress = progress(line, now);
            if (progress <= 0.01f && line.leaving) continue;

            any = true;
            Row row = line.row;

            int alpha = Math.max(0, Math.min(255, (int) (progress * 255f)));
            int slideX = line.leaving ? 0 : (int) ((1f - easeOut(progress)) * 8f * scale);
            int slideY = line.leaving ? (int) ((1f - progress) * -6f * scale) : 0;
            int rx = x + pad + slideX;
            int ry = rowY + slideY;

            if (row.marked()) drawWatchGlow(g, row, x, ry, w, rowH, scale, alpha, now);

            drawFace(g, row, rx, ry + scaled(2, scale), head, alpha);

            int nameX = rx + head + scaled(6, scale);
            int textY = ry + scaled(5, scale);

            drawText(g, row.name(), nameX, textY, withAlpha(NAME, alpha), scale, 1.05f);

            String status = statusOf(row);
            if (!status.isEmpty() && !row.marked()) {
                int sw = textWidth(status, scale, 0.85f);
                drawText(g, status, x + w - pad - sw, textY + scaled(1, scale), withAlpha(WARN, alpha), scale, 0.85f);
            }

            if (row.marked()) {
                int nameEnd = nameX + textWidth(row.name(), scale, 1.05f);

                if (!status.isEmpty()) {
                    drawText(g, status, nameEnd + scaled(6, scale), textY + scaled(1, scale), withAlpha(WARN, alpha), scale, 0.85f);
                }

                String value = valueOf(row, now);
                int valueW = textWidth(value, scale, 1.05f);
                int tagW = textWidth(WATCHING_YOU, scale, 0.85f);
                int valueX = x + w - pad - valueW;
                int tagX = valueX - scaled(4, scale) - tagW;

                drawText(g, WATCHING_YOU, tagX, textY + scaled(1, scale), withAlpha(WARN, alpha), scale, 0.85f);
                drawText(g, value, valueX, textY, withAlpha(row.nearby() ? NAME : DIM, alpha), scale, 1.05f);
            }

            rowY += (int) (rowH * progress);
        }

        if (more > 0) {
            drawText(g, "+" + more + " more", x + pad + head + scaled(6, scale), rowY + scaled(5, scale), DIM, scale, 0.95f);
            any = true;
        }

        if (!any) drawText(g, "No admins online", x + pad, y + scaled(28, scale), EMPTY, scale, 1.05f);
    }

    private void drawWatchGlow(GuiGraphicsExtractor g, Row row, int panelX, int rowTop, int panelW, int rowH, float scale, int alpha, long now) {
        float level = row.highlightUntil() <= now ? 0f : Math.max(0f, Math.min(1f, (row.highlightUntil() - now) / (float) ALERT_MS));
        float glow = 0.14f;

        if (level > 0f) {
            double elapsed = (1.0 - level) * ALERT_MS;
            float wave = 0.5f + 0.5f * (float) Math.cos(elapsed * 2.0 * Math.PI / PULSE_MS);
            glow += 0.24f * level * wave;
        }

        int tint = Math.round(255f * glow * (alpha / 255f));
        int inset = scaled(3, scale);
        int top = rowTop + 1;
        int bottom = rowTop + rowH - 1;

        g.fill(panelX + inset, top, panelX + panelW - inset, bottom, (Math.max(0, Math.min(255, tint)) << 24) | (WARN & 0xFFFFFF));
        g.fill(panelX + inset, top + scaled(3, scale), panelX + inset + Math.max(1, scaled(2, scale)), bottom - scaled(3, scale), withAlpha(WARN, alpha));
    }

    private void drawFace(GuiGraphicsExtractor g, Row row, int x, int y, int size, int alpha) {
        FaceSkins.Skin skin = FaceSkins.get(row.id(), row.name());
        int color = (alpha << 24) | 0xFFFFFF;

        if (skin == null || skin.texture() == null) {
            g.fill(x, y, x + size, y + size, withAlpha(FACE_FALLBACK, alpha));
            return;
        }

        g.blit(RenderPipelines.GUI_TEXTURED, skin.texture(), x, y, 8f, 8f, size, size, 8, 8, skin.width(), skin.height(), color);
        g.blit(RenderPipelines.GUI_TEXTURED, skin.texture(), x, y, 40f, 8f, size, size, 8, 8, skin.width(), skin.height(), color);
    }

    private static String statusOf(Row row) {
        return switch (row.presence()) {
            case VANISHED -> "VANISHED";
            case SPECTATOR -> "SPECTATOR";
            case ONLINE -> "";
        };
    }

    private static String valueOf(Row row, long now) {
        if (row.nearby()) return Math.round(row.distance()) + "m";
        return formatAge(now - row.provenAt());
    }

    public static String formatAge(long ms) {
        long total = Math.max(0L, ms) / 1000L;
        long hours = total / 3600L;
        long minutes = total / 60L % 60L;
        long seconds = total % 60L;

        StringBuilder out = new StringBuilder(8);
        if (hours > 0L) out.append(hours).append(minutes < 10L ? ":0" : ":");

        return out.append(minutes).append(seconds < 10L ? ":0" : ":").append(seconds).toString();
    }

    private int measureWidth(float scale) {
        Minecraft mc = Minecraft.getInstance();
        int base = scaled(WIDTH, scale);
        if (mc.font == null) return base;

        int pad = scaled(PAD, scale);
        int head = Math.max(8, scaled(HEAD, scale));
        long now = System.currentTimeMillis();
        int widest = 0;

        for (Line line : lines) {
            if (line.leaving && progress(line, now) <= 0.01f) continue;

            Row row = line.row;
            int rowW = head + scaled(6, scale) + textWidth(row.name(), scale, 1.05f);
            String status = statusOf(row);

            if (row.marked()) {
                if (!status.isEmpty()) rowW += scaled(6, scale) + textWidth(status, scale, 0.85f);
                rowW += scaled(14, scale) + textWidth(WATCHING_YOU, scale, 0.85f) + scaled(4, scale) + textWidth(valueOf(row, now), scale, 1.05f);
            } else if (!status.isEmpty()) {
                rowW += scaled(14, scale) + textWidth(status, scale, 0.85f);
            }

            widest = Math.max(widest, rowW);
        }

        return Math.max(base, widest + pad * 2);
    }

    private static void drawText(GuiGraphicsExtractor g, String text, int x, int y, int argb, float hudScale, float factor) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null || text == null || text.isEmpty()) return;

        float fontScale = factor * hudScale;

        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(fontScale, fontScale);
        g.text(mc.font, text, 0, 0, argb, true);
        g.pose().popMatrix();
    }

    private static int textWidth(String text, float hudScale, float factor) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null || text == null) return 0;

        return Math.round(mc.font.width(text) * factor * hudScale);
    }

    private static int withAlpha(int argb, int alpha) {
        return (Math.min(alpha, (argb >>> 24) & 0xFF) << 24) | (argb & 0xFFFFFF);
    }

    private int panelHeight() {
        long now = System.currentTimeMillis();
        int visibleRows = 0;

        for (Line line : lines) {
            if (!line.leaving || progress(line, now) > 0.01f) visibleRows++;
        }

        if (more > 0) visibleRows++;

        return visibleRows == 0 ? 50 : HEADER_H + visibleRows * ROW_H + PAD;
    }

    private int resolveX(Setting<Integer> hudX, int screenW, int panelW) {
        int value = hudX.get();
        if (value < 0 || value > screenW - 20) return Math.max(0, screenW - panelW - 10);
        return Math.min(value, Math.max(0, screenW - panelW));
    }

    private int resolveY(Setting<Integer> hudY, int screenH, float scale) {
        int value = hudY.get();
        int panelH = scaled(panelHeight(), scale);
        if (value < 0 || value > screenH - 20) return 10;
        return Math.min(value, Math.max(0, screenH - panelH));
    }

    private static float scaleOf(Setting<Integer> size) {
        return size.get() / 100.0f;
    }

    private static int scaled(int value, float scale) {
        return Math.max(1, Math.round(value * scale));
    }

    private Line find(String key) {
        for (Line line : lines) {
            if (line.key.equals(key)) return line;
        }
        return null;
    }

    private void prune(long now) {
        Iterator<Line> it = lines.iterator();

        while (it.hasNext()) {
            Line line = it.next();
            if (line.leaving && progress(line, now) <= 0.01f) it.remove();
        }
    }

    private float progress(Line line, long now) {
        long elapsed = now - line.since;
        if (line.leaving) return 1.0f - easeOut(Math.min(1.0f, (float) elapsed / EXIT_MS));
        if (line.entering) return easeOut(Math.min(1.0f, (float) elapsed / ENTER_MS));
        return 1.0f;
    }

    private static float easeOut(float t) {
        float inv = 1.0f - t;
        return 1.0f - inv * inv * inv;
    }

    private static final Comparator<Line> ORDER = Comparator
            .<Line>comparingInt(l -> l.order)
            .thenComparing(l -> l.row.name().toLowerCase(Locale.ROOT));

    private static final class Line {
        private final String key;
        private Row row;
        private int order;
        private boolean entering;
        private boolean leaving;
        private long since = System.currentTimeMillis();

        private Line(Row row, boolean animateEnter) {
            this.key = row.id().toString();
            this.row = row;
            this.entering = animateEnter;
        }
    }
}
