package com.nnpg.glazed.utils.hud;

import meteordevelopment.meteorclient.settings.Setting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public final class SpotifyBoard {

    private static final int PAD = 8;
    private static final int ART = 44;
    private static final int EQ_HEIGHT = 22;
    private static final int RADIUS = 10;
    private static final int EQ_MAX = 128;

    private static final int BACKGROUND = 0xF00A0A0E;
    private static final int BORDER = 0x1AFFFFFF;
    private static final int ART_PLACEHOLDER = 0x33141414;
    private static final int GREEN = 0xFF1ED760;
    private static final int TITLE = 0xFFF4F6FA;
    private static final int ARTIST = 0xFF8B95A7;
    private static final int TIME = 0xFF7E889B;
    private static final int TRACK = 0x20FFFFFF;

    private final float[] barHeights = new float[EQ_MAX];
    private final float[] barPhase = new float[EQ_MAX];
    private final float[] barJitter = new float[EQ_MAX];
    private final Random random = new Random();

    private boolean dragging;
    private double grabX;
    private double grabY;

    private float scrollX;
    private long scrollNanos;
    private String lastTitle = "";

    public SpotifyBoard() {
        for (int i = 0; i < EQ_MAX; i++) {
            barHeights[i] = 2.0f;
            barPhase[i] = random.nextFloat() * 6.2831855f;
            barJitter[i] = 0.7f + random.nextFloat() * 0.6f;
        }
    }

    public void reset() {
        dragging = false;
        scrollX = 0.0f;
        lastTitle = "";
    }

    public static int height() {
        return PAD + ART + 8 + EQ_HEIGHT + 8 + 9 + PAD;
    }

    public void drag(Setting<Integer> hudX, Setting<Integer> hudY, Setting<Integer> width, Setting<Integer> size) {
        if (mc.getWindow() == null || !(mc.gui.screen() instanceof ChatScreen)) {
            dragging = false;
            return;
        }

        float scale = size.get() / 100.0f;
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int panelWidth = px(width.get(), scale);
        int panelHeight = px(height(), scale);

        int x = resolveX(hudX, screenWidth, panelWidth);
        int y = resolveY(hudY, screenHeight, panelHeight);

        double mouseX = mc.mouseHandler.xpos() * screenWidth / mc.getWindow().getScreenWidth();
        double mouseY = mc.mouseHandler.ypos() * screenHeight / mc.getWindow().getScreenHeight();

        boolean down = GLFW.glfwGetMouseButton(mc.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        if (!down) {
            dragging = false;
            return;
        }

        if (!dragging) {
            boolean inside = mouseX >= x && mouseX <= x + panelWidth && mouseY >= y && mouseY <= y + panelHeight;
            if (!inside) return;

            dragging = true;
            grabX = mouseX - x;
            grabY = mouseY - y;
        }

        hudX.set(Math.max(0, Math.min(screenWidth - panelWidth, (int) (mouseX - grabX))));
        hudY.set(Math.max(0, Math.min(screenHeight - panelHeight, (int) (mouseY - grabY))));
    }

    public void render(GuiGraphicsExtractor context, Track track, Identifier art, Setting<Integer> hudX, Setting<Integer> hudY,
                       Setting<Integer> widthSetting, Setting<Integer> size, boolean showArt, boolean showProgress,
                       boolean showEqualizer, double sensitivity) {
        if (mc.getWindow() == null) return;

        float scale = size.get() / 100.0f;
        int width = px(widthSetting.get(), scale);
        int height = px(height(), scale);
        int x = resolveX(hudX, mc.getWindow().getGuiScaledWidth(), width);
        int y = resolveY(hudY, mc.getWindow().getGuiScaledHeight(), height);

        int pad = px(PAD, scale);
        int artSize = px(ART, scale);
        int radius = Math.max(3, px(RADIUS, scale));

        RoundRect.draw(context, x, y, width, height, radius, BACKGROUND);
        RoundRect.stroke(context, x, y, width, height, radius, BORDER);

        int artX = x + pad;
        int artY = y + pad;

        RoundRect.draw(context, artX, artY, artSize, artSize, Math.max(2, px(6, scale)), ART_PLACEHOLDER);

        if (showArt && art != null) {
            context.blit(RenderPipelines.GUI_TEXTURED, art, artX, artY, 0.0f, 0.0f, artSize, artSize, artSize, artSize);
        }

        int infoX = artX + artSize + px(10, scale);
        int infoWidth = Math.max(1, x + width - pad - infoX);

        text(context, "NOW PLAYING", infoX, artY + px(1, scale), GREEN, scale * 0.85f);

        String title = track.title();
        String artist = track.artist();

        if (!title.equals(lastTitle)) {
            lastTitle = title;
            scrollX = 0.0f;
        }

        drawTitle(context, title, infoX, artY + px(17, scale), infoWidth, scale);

        if (!artist.isEmpty()) {
            text(context, clip(artist, infoWidth, scale * 0.9f), infoX, artY + px(30, scale), ARTIST, scale * 0.9f);
        }

        int eqTop = y + pad + artSize + px(8, scale);
        int eqHeight = px(EQ_HEIGHT, scale);
        int eqBottom = eqTop + eqHeight;
        int innerWidth = width - pad * 2;

        if (showEqualizer) {
            drawEqualizer(context, x + pad, eqBottom, innerWidth, eqHeight, track, sensitivity, scale);
        }

        if (showProgress) {
            drawProgress(context, x, eqBottom + px(9, scale), width, pad, track, scale);
        }
    }

    private void drawTitle(GuiGraphicsExtractor context, String title, int x, int y, int maxWidth, float scale) {
        if (title.isEmpty()) return;

        int titleWidth = (int) (mc.font.width(title) * scale);

        if (titleWidth <= maxWidth) {
            scrollX = 0.0f;
            text(context, title, x, y, TITLE, scale);
            return;
        }

        long now = System.nanoTime();
        float delta = scrollNanos == 0L ? 0.0f : Math.min(0.1f, (now - scrollNanos) / 1.0e9f);
        scrollNanos = now;

        scrollX += 28.0f * delta * scale;
        if (scrollX > titleWidth + px(24, scale)) scrollX = 0.0f;

        context.enableScissor(x, y - px(2, scale), x + maxWidth, y + px(12, scale));
        text(context, title, x - (int) scrollX, y, TITLE, scale);
        text(context, title, x - (int) scrollX + titleWidth + px(24, scale), y, TITLE, scale);
        context.disableScissor();
    }

    private void drawEqualizer(GuiGraphicsExtractor context, int x, int bottom, int innerWidth, int maxHeight, Track track, double sensitivity, float scale) {
        int gap = Math.max(1, px(2, scale));
        int barWidth = Math.max(1, px(3, scale));
        int unit = barWidth + gap;
        int count = Math.max(1, Math.min(EQ_MAX, innerWidth / unit));
        int used = count * unit - gap;
        int startX = x + (innerWidth - used) / 2;

        tick(track.playing(), count, track.musicMs(), maxHeight, sensitivity);

        for (int i = 0; i < count; i++) {
            int barHeight = Math.max(1, Math.min(maxHeight, (int) barHeights[i]));
            context.fill(startX + i * unit, bottom - barHeight, startX + i * unit + barWidth, bottom, GREEN);
        }
    }

    private void drawProgress(GuiGraphicsExtractor context, int x, int rowY, int width, int pad, Track track, float scale) {
        String elapsed = format(track.positionSeconds());
        String total = track.durationSeconds() > 0 ? format(track.durationSeconds()) : "0:00";

        int elapsedWidth = (int) (mc.font.width(elapsed) * scale * 0.85f);
        int totalWidth = (int) (mc.font.width(total) * scale * 0.85f);

        text(context, elapsed, x + pad, rowY, TIME, scale * 0.85f);
        text(context, total, x + width - pad - totalWidth, rowY, TIME, scale * 0.85f);

        int trackX = x + pad + elapsedWidth + px(8, scale);
        int trackEnd = x + width - pad - totalWidth - px(8, scale);
        int trackWidth = trackEnd - trackX;
        int barY = rowY - px(2, scale);
        int barHeight = Math.max(1, px(3, scale));

        if (trackWidth <= px(10, scale)) return;

        RoundRect.draw(context, trackX, barY, trackWidth, barHeight, barHeight / 2, TRACK);

        if (track.durationSeconds() <= 0) return;

        float percent = Math.min(1.0f, (float) track.positionSeconds() / track.durationSeconds());
        int filled = Math.max(barHeight, (int) (trackWidth * percent));

        RoundRect.draw(context, trackX, barY, filled, barHeight, barHeight / 2, GREEN);
    }

    private void tick(boolean playing, int count, long musicMs, int maxPixels, double sensitivity) {
        if (!playing) {
            for (int i = 0; i < count; i++) barHeights[i] += (2.0f - barHeights[i]) * 0.2f;
            return;
        }

        double beatMs = 60000.0 / 125.0;
        double halfMs = beatMs / 2.0;
        double beatPhase = (musicMs % (long) beatMs) / beatMs;
        double halfPhase = (musicMs % (long) halfMs) / halfMs;

        float kick = (float) Math.exp(-3.4 * beatPhase);
        float hat = (float) (Math.exp(-6.0 * halfPhase) * 0.55);
        float seconds = musicMs / 1000.0f;
        int denominator = Math.max(1, count - 1);

        for (int i = 0; i < count; i++) {
            float centre = 1.0f - Math.abs((i / (float) denominator) * 2.0f - 1.0f);
            float frequency = (6.0f + (1.0f - centre) * 16.0f) * barJitter[i];
            float oscillation = (float) (Math.sin(seconds * frequency + barPhase[i]) * 0.5 + 0.5);
            float energy = centre * kick + (1.0f - centre) * hat + oscillation * (0.30f + 0.45f * (1.0f - centre));
            float target = 2.0f + Math.min(1.4f, energy) * (float) sensitivity * (maxPixels - 2);

            barHeights[i] += (target - barHeights[i]) * 0.35f;
        }
    }

    private String clip(String value, int maxWidth, float scale) {
        if (mc.font.width(value) * scale <= maxWidth) return value;

        String out = value;
        while (!out.isEmpty() && mc.font.width(out + "...") * scale > maxWidth) {
            out = out.substring(0, out.length() - 1);
        }

        return out + "...";
    }

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

    private static String format(int seconds) {
        if (seconds < 0) seconds = 0;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    private static int px(int value, float scale) {
        return Math.max(1, Math.round(value * scale));
    }

    private static int resolveX(Setting<Integer> hudX, int screenWidth, int panelWidth) {
        int value = hudX.get();
        if (value < 0) return Math.max(0, screenWidth - panelWidth - 4);
        return Math.max(0, Math.min(screenWidth - panelWidth, value));
    }

    private static int resolveY(Setting<Integer> hudY, int screenHeight, int panelHeight) {
        int value = hudY.get();
        if (value < 0) return 4;
        return Math.max(0, Math.min(screenHeight - panelHeight, value));
    }

    public record Track(String title, String artist, int positionSeconds, int durationSeconds, boolean playing, long musicMs) {
        public static Track idle() {
            return new Track("Not Playing", "Open Spotify", 0, 0, false, 0L);
        }
    }
}
