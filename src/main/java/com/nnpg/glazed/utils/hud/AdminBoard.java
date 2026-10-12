package com.nnpg.glazed.utils.hud;

import meteordevelopment.meteorclient.settings.Setting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.PlayerSkin;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public final class AdminBoard {
    private static final int WIDTH = 168;
    // nvm
    private static final int HEADER = 14;
    private static final int ROW = 20;
    private static final int PAD = 8;
    private static final int FACE = 16;
    private static final int RADIUS = 10;

    private static final long ENTER_MS = 350L;
    private static final long EXIT_MS = 300L;
    private static final long NEW_MS = 5000L;

    private static final int BACKGROUND = 0xF00A0A0E;
    private static final int BORDER = 0x1AFFFFFF;
    private static final int DIVIDER = 0x12FFFFFF;
    private static final int ACCENT = 0xFFCBA6F7;
    private static final int NAME = 0xFFF2F2F8;
    private static final int COUNT = 0xFF8E8E9A;
    private static final int DOT = 0xFF55555F;
    private static final int NOBODY = 0xFFA0A0AA;
    private static final int FACE_FALLBACK = 0xFF2A2A2A;
    private static final int INITIAL = 0xFF7A7A86;

    private final List<Line> lines = new ArrayList<>();

    private boolean dragging;
    private double grabX;
    private double grabY;

    public void clear() {
        lines.clear();
        dragging = false;
    }

    public void sync(Map<String, String> online, List<String> joined, List<String> left, boolean firstScan) {
        long now = System.currentTimeMillis();

        if (firstScan) {
            lines.clear();
            for (Map.Entry<String, String> entry : online.entrySet()) lines.add(new Line(entry.getKey(), entry.getValue(), false));
            sort();
            return;
        }

        for (String key : joined) {
            Line line = find(key);
            if (line == null) {
                lines.add(new Line(key, online.get(key), true));
                continue;
            }
            line.leaving = false;
            line.entering = true;
            line.since = now;
        }

        for (String key : left) {
            Line line = find(key);
            if (line == null || line.leaving) continue;
            line.leaving = true;
            line.entering = false;
            line.since = now;
        }

        for (Line line : lines) {
            if (online.containsKey(line.key) && !left.contains(line.key)) {
                line.leaving = false;
                line.display = online.get(line.key);
                continue;
            }
            if (online.containsKey(line.key) || line.leaving) continue;
            line.leaving = true;
            line.entering = false;
            line.since = now;
        }

        sort();
        prune(now);
    }

    public void drag(Setting<Integer> hudX, Setting<Integer> hudY, Setting<Integer> size) {
        if (mc.getWindow() == null || !(mc.gui.screen() instanceof ChatScreen)) {
            dragging = false;
            return;
        }

        float scale = size.get() / 100.0f;
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int panelWidth = px(WIDTH, scale);
        int panelHeight = px(height(), scale);

        int x = resolveX(hudX, screenWidth, scale);
        int y = resolveY(hudY, screenHeight, scale);

        double mouseX = mc.mouseHandler.getScaledXPos(mc.getWindow());
        double mouseY = mc.mouseHandler.getScaledYPos(mc.getWindow());
        boolean held = GLFW.glfwGetMouseButton(mc.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_1) == GLFW.GLFW_PRESS;

        if (!held) {
            dragging = false;
            return;
        }

        if (!dragging) {
            boolean onHeader = mouseX >= x && mouseX < x + panelWidth && mouseY >= y && mouseY < y + px(HEADER, scale);
            if (!onHeader) return;

            dragging = true;
            grabX = mouseX - x;
            grabY = mouseY - y;
        }

        int newX = (int) Math.round(Math.max(0, Math.min(mouseX - grabX, screenWidth - panelWidth)));
        int newY = (int) Math.round(Math.max(0, Math.min(mouseY - grabY, screenHeight - panelHeight)));

        if (hudX.get() != newX) hudX.set(newX);
        if (hudY.get() != newY) hudY.set(newY);
    }

    public void render(GuiGraphicsExtractor context, Setting<Integer> hudX, Setting<Integer> hudY, Setting<Integer> size) {
        if (mc.getWindow() == null || mc.font == null) return;

        float scale = size.get() / 100.0f;
        long now = System.currentTimeMillis();
        prune(now);

        int x = resolveX(hudX, mc.getWindow().getGuiScaledWidth(), scale);
        int y = resolveY(hudY, mc.getWindow().getGuiScaledHeight(), scale);
        int width = px(WIDTH, scale);
        int height = px(height(), scale);
        int pad = px(PAD, scale);
        int face = Math.max(8, px(FACE, scale));
        int radius = Math.max(3, px(RADIUS, scale));

        RoundRect.draw(context, x, y, width, height, radius, BACKGROUND);
        RoundRect.stroke(context, x, y, width, height, radius, BORDER);

        int header = px(HEADER, scale);
        drawHeader(context, x, y, width, header, pad, scale);

        int rowY = y + header;
        boolean drewAny = false;

        for (Line line : lines) {
            if (line.leaving && progress(line, now) <= 0.01f) continue;

            float progress = progress(line, now);
            drewAny = true;

            int alpha = Math.max(0, Math.min(255, (int) (progress * 255f)));
            int slideX = line.leaving ? 0 : (int) ((1f - RoundRect.easeOut(progress)) * 8f * scale);
            int slideY = line.leaving ? (int) ((1f - progress) * -6f * scale) : 0;

            int lineX = x + pad + slideX;
            int lineY = rowY + slideY;

            drawFace(context, line, lineX, lineY + px(2, scale), face, alpha);
            text(context, line.display, lineX + face + px(6, scale), lineY + px(6, scale), RoundRect.withAlpha(NAME, alpha), scale);
            drawNewTag(context, line, now, x + width - pad, lineY + px(6, scale), alpha, scale);

            rowY += (int) (px(ROW, scale) * progress);
        }

        if (!drewAny) text(context, "nobody on", x + pad, y + header + px(4, scale), NOBODY, scale);
    }

    private void drawHeader(GuiGraphicsExtractor context, int x, int y, int width, int header, int pad, float scale) {
        int barW = Math.max(1, px(2, scale));
        int barTop = y + Math.max(1, px(4, scale));
        int barBottom = y + header - Math.max(1, px(4, scale));
        context.fill(x + pad, barTop, x + pad + barW, barBottom, ACCENT);

        int live = 0;
        long now = System.currentTimeMillis();
        for (Line line : lines) {
            if (!line.leaving || progress(line, now) > 0.01f) live++;
        }

        text(context, live + " online", x + pad + barW + px(4, scale), y + px(4, scale), COUNT, scale);

        int dot = Math.max(1, px(1, scale));
        int gap = dot * 2;
        int dotY = y + header / 2 - dot / 2;
        int dotX = x + width - pad - (dot * 3 + gap * 2);
        for (int i = 0; i < 3; i++) {
            context.fill(dotX, dotY, dotX + dot, dotY + dot, DOT);
            dotX += dot + gap;
        }

        context.fill(x + 1, y + header, x + width - 1, y + header + Math.max(1, px(1, scale)), DIVIDER);
    }

    private void drawNewTag(GuiGraphicsExtractor context, Line line, long now, int right, int y, int alpha, float scale) {
        if (line.leaving || !line.entering) return;

        long age = now - line.since;
        if (age >= NEW_MS) return;

        float fade = age > NEW_MS - 600L ? (NEW_MS - age) / 600f : 1f;
        int tagAlpha = Math.max(0, Math.min(alpha, (int) (fade * 255f)));
        if (tagAlpha <= 2) return;

        int tagWidth = Math.round(mc.font.width("new") * scale);
        text(context, "new", right - tagWidth, y, RoundRect.withAlpha(ACCENT, tagAlpha), scale);
    }

    private void drawFace(GuiGraphicsExtractor context, Line line, int x, int y, int size, int alpha) {
        PlayerSkin skin = skinOf(line);
        if (skin != null) {
            PlayerFaceExtractor.extractRenderState(context, skin, x, y, size);
            return;
        }

        context.fill(x, y, x + size, y + size, RoundRect.withAlpha(FACE_FALLBACK, alpha));

        String initial = line.display.isEmpty() ? "?" : line.display.substring(0, 1).toUpperCase(Locale.ROOT);
        int textX = x + (size - mc.font.width(initial)) / 2;
        int textY = y + (size - 8) / 2;
        context.text(mc.font, initial, textX, textY, RoundRect.withAlpha(INITIAL, alpha), false);
    }

    // aye
    private PlayerSkin skinOf(Line line) {
        if (mc.getConnection() != null) {
            PlayerInfo entry = mc.getConnection().getPlayerInfoIgnoreCase(line.display);
            if (entry == null) entry = mc.getConnection().getPlayerInfoIgnoreCase(line.key);
            if (entry != null) line.skin = entry.getSkin();
        }
        return line.skin;
    }

    // the font is a bitmap, any fractional scale smears it, so ride the panel scale and nothing else
    private void text(GuiGraphicsExtractor context, String value, int x, int y, int argb, float scale) {
        if (value == null || value.isEmpty()) return;

        if (scale == 1.0f) {
            context.text(mc.font, value, x, y, argb, true);
            return;
        }

        context.pose().pushMatrix();
        context.pose().translate(x, y);
        context.pose().scale(scale, scale);
        context.text(mc.font, value, 0, 0, argb, true);
        context.pose().popMatrix();
    }

    private int height() {
        long now = System.currentTimeMillis();
        int visible = 0;

        for (Line line : lines) {
            if (!line.leaving || progress(line, now) > 0.01f) visible++;
        }

        return visible == 0 ? HEADER + 16 + PAD : HEADER + visible * ROW + PAD;
    }

    private int resolveX(Setting<Integer> hudX, int screenWidth, float scale) {
        int value = hudX.get();
        if (value < 0 || value > screenWidth - 20) return Math.max(0, screenWidth - px(WIDTH, scale) - 10);
        return value;
    }

    private int resolveY(Setting<Integer> hudY, int screenHeight, float scale) {
        int value = hudY.get();
        if (value < 0 || value > screenHeight - 20) return 10;
        return Math.min(value, Math.max(0, screenHeight - px(height(), scale)));
    }

    private static int px(int value, float scale) {
        return Math.max(1, Math.round(value * scale));
    }

    private Line find(String key) {
        for (Line line : lines) {
            if (line.key.equals(key)) return line;
        }
        return null;
    }

    private void sort() {
        lines.sort(Comparator.comparing(line -> line.display.toLowerCase(Locale.ROOT)));
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
        if (line.leaving) return 1.0f - RoundRect.easeOut(Math.min(1.0f, elapsed / (float) EXIT_MS));
        if (line.entering) return RoundRect.easeOut(Math.min(1.0f, elapsed / (float) ENTER_MS));
        return 1.0f;
    }

    private static final class Line {
        private final String key;
        private String display;
        private PlayerSkin skin;
        private boolean entering;
        private boolean leaving;
        private long since = System.currentTimeMillis();

        private Line(String key, String display, boolean animate) {
            this.key = key;
            this.display = display == null ? key : display;
            this.entering = animate;
        }
    }
}
