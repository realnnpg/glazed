package com.nnpg.glazed.antivanish;

import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import com.nnpg.glazed.modules.main.AdminHud;

import java.util.UUID;

public final class WatchingEffect {
    private static final WatchingEffect INSTANCE = new WatchingEffect();

    private static final float FLASH_SECONDS = 1.0f;
    private static final float FLASH_RAMP = 0.15f;
    private static final float FLASH_PEAK = 0.55f;
    private static final float POP_SECONDS = 0.28f;
    private static final float HOLD_UNTIL = 4.0f;
    private static final float END_SECONDS = 4.5f;

    private static final int BEAT_TICKS = 24;
    private static final int GONE_TICKS = 60;
    private static final float PULSE_BASE = 0.05f;
    private static final float PULSE_THUMP = 0.13f;

    private static final int FACE = 40;
    private static final int GAP = 12;
    private static final int PAD = 12;
    private static final float TEXT_SCALE = 1.5f;

    private UUID watcher;
    private String watcherName = "";
    private String title = "";
    private FaceSkins.Skin skin;
    private long startNanos;
    private boolean active;
    private boolean looping;
    private int beatTicks;
    private int absentTicks;
    private long lastBeatNanos;
    private long lastFrameNanos;
    private float level;

    private long scheduledAtNanos;
    private UUID scheduledId;
    private String scheduledName;
    private Runnable scheduledAction;
    private boolean scheduled;

    private WatchingEffect() {
    }

    public static WatchingEffect get() {
        return INSTANCE;
    }

    public void schedule(UUID id, String name, double seconds, Runnable action) {
        if (name == null || name.isBlank()) return;

        FaceSkins.preload(name);

        scheduledId = id;
        scheduledName = name.trim();
        scheduledAction = action;
        scheduledAtNanos = System.nanoTime() + (long) (Math.max(0.0, seconds) * 1.0E9);
        scheduled = true;
    }

    public void play(UUID id, String name, int others) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || name == null || name.isBlank()) return;

        watcher = id;
        watcherName = name;
        title = others > 0 ? name + " +" + others : name;
        skin = FaceSkins.get(id, name);
        startNanos = System.nanoTime();
        lastBeatNanos = startNanos;
        lastFrameNanos = startNanos;
        beatTicks = 0;
        absentTicks = 0;
        level = 0f;
        looping = true;
        active = true;
    }

    public void cancel() {
        scheduled = false;
        scheduledAction = null;
        active = false;
        looping = false;
    }

    public boolean isBusy() {
        return active || scheduled;
    }

    public long remainingScheduledMs() {
        return scheduled ? Math.max(0L, (scheduledAtNanos - System.nanoTime()) / 1000000L) : 0L;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.level == null || mc.player == null) {
            scheduled = false;
            scheduledAction = null;
            active = false;
            looping = false;
            return;
        }

        if (scheduled && System.nanoTime() >= scheduledAtNanos) {
            scheduled = false;

            Runnable action = scheduledAction;
            scheduledAction = null;

            play(scheduledId, scheduledName, 0);
            if (action != null) action.run();
        }

        if (!active) return;

        if (looping) {
            if (present()) {
                absentTicks = 0;
            } else if (++absentTicks > GONE_TICKS) {
                looping = false;
            }

            if (looping && ++beatTicks >= BEAT_TICKS) {
                beatTicks = 0;
                lastBeatNanos = System.nanoTime();
                beat(mc);
            }
        }

        if (!looping && level <= 0.01f && elapsed() >= END_SECONDS) active = false;
    }

    private boolean present() {
        return AdminHud.isPresent(watcher, watcherName) || AntiVanish.isWatching(watcherName);
    }

    private static void beat(Minecraft mc) {
        if (mc.getSoundManager() == null) return;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, 0.85f, 0.9f));
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!active || mc.level == null) return;

        float seconds = elapsed();

        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrameNanos) / 1.0E9f);
        lastFrameNanos = now;
        level = looping ? Math.min(1f, level + dt * 3f) : Math.max(0f, level - dt * 2f);

        if (skin == null || skin.pending()) skin = FaceSkins.get(watcher, watcherName);

        GuiGraphics g = event.drawContext;
        int w = g.guiWidth();
        int h = g.guiHeight();

        if (seconds < FLASH_SECONDS) {
            drawFlash(g, w, h, seconds);
        } else if (level > 0f) {
            drawPulse(g, w, h, now);
        }

        if (seconds >= FLASH_SECONDS && seconds < END_SECONDS) drawCard(g, mc, w, h, seconds - FLASH_SECONDS);
    }

    private void drawFlash(GuiGraphics g, int w, int h, float seconds) {
        float level = seconds < FLASH_RAMP ? seconds / FLASH_RAMP : 1f - (seconds - FLASH_RAMP) / (FLASH_SECONDS - FLASH_RAMP);
        int alpha = Math.round(clamp(level) * FLASH_PEAK * 255f);
        g.fill(0, 0, w, h, (alpha << 24) | 0xC80000);
    }

    private void drawPulse(GuiGraphics g, int w, int h, long now) {
        float phase = (now - lastBeatNanos) / (BEAT_TICKS * 5.0E7f);
        float thump = Math.max(0f, 1f - phase * 2.5f);
        float alpha = (PULSE_BASE + PULSE_THUMP * thump * thump) * level;
        g.fill(0, 0, w, h, (Math.round(clamp(alpha) * 255f) << 24) | 0xC80000);
    }

    private void drawCard(GuiGraphics g, Minecraft mc, int w, int h, float seconds) {
        float fadeOut = END_SECONDS - FLASH_SECONDS - seconds < 0.5f ? (END_SECONDS - FLASH_SECONDS - seconds) / 0.5f : 1f;
        float alpha = clamp(Math.min(fadeOut, seconds / 0.08f));
        float pop = seconds < POP_SECONDS ? easeOutBack(seconds / POP_SECONDS) : 1f;

        String text = title + " staff is watching you";
        int textW = Math.round(mc.font.width(text) * TEXT_SCALE);
        int textH = Math.round(mc.font.lineHeight * TEXT_SCALE);

        int cardW = PAD + FACE + GAP + textW + PAD;
        int cardH = PAD + FACE + PAD;

        float cx = w / 2f;
        float cy = h * 0.22f + cardH / 2f;

        g.pose().pushPose();
        g.pose().translate(cx, cy, 0f);
        g.pose().scale(pop, pop, 1f);
        g.pose().translate(-cardW / 2f, -cardH / 2f, 0f);

        int bg = Math.round(alpha * 215f) << 24;
        SmoothRoundRect.draw(g, 0, 0, cardW, cardH, 12, bg | 0x1A0505);

        drawFace(g, PAD, PAD, alpha);

        int textY = PAD + (FACE - textH) / 2;
        g.pose().pushPose();
        g.pose().translate(PAD + FACE + GAP, textY, 0f);
        g.pose().scale(TEXT_SCALE, TEXT_SCALE, 1f);
        g.drawString(mc.font, text, 0, 0, (Math.round(alpha * 255f) << 24) | 0xFF5555, true);
        g.pose().popPose();

        g.pose().popPose();
    }

    private void drawFace(GuiGraphics g, int x, int y, float alpha) {
        int color = (Math.round(alpha * 255f) << 24) | 0xFFFFFF;

        if (skin == null || skin.texture() == null) {
            g.fill(x, y, x + FACE, y + FACE, (Math.round(alpha * 255f) << 24) | 0x555555);
            return;
        }

        g.blit(RenderType::guiTextured, skin.texture(), x, y, 8f, 8f, FACE, FACE, 8, 8, skin.width(), skin.height(), color);
        g.blit(RenderType::guiTextured, skin.texture(), x, y, 40f, 8f, FACE, FACE, 8, 8, skin.width(), skin.height(), color);
    }

    private float elapsed() {
        return (float) (System.nanoTime() - startNanos) / 1.0E9f;
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        float u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }
}
