package com.nnpg.glazed.antivanish;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayDeque;

public final class StaffAlarm {
    private static final int GUARDIAN_INTERVAL = 60;
    private static final int MAX_RINGING = 8;

    private float volume;
    private int ticksLeft;
    private int step;
    private final ArrayDeque<SoundInstance> ringing = new ArrayDeque<>(MAX_RINGING);

    public void start(float seconds, float volume) {
        if (volume <= 0.0f || seconds <= 0.0f) return;

        int ticks = Math.max(1, Math.round(seconds * 20.0f));
        this.volume = Math.min(1.0f, volume);

        if (isPlaying()) {
            ticksLeft = Math.max(ticksLeft, ticks);
            return;
        }

        stop();
        ticksLeft = ticks;
        step = 0;
    }

    public void tick() {
        if (ticksLeft <= 0) return;

        if (step % GUARDIAN_INTERVAL == 0) play();

        step++;

        if (--ticksLeft == 0) step = 0;
    }

    public void stop() {
        ticksLeft = 0;
        step = 0;

        if (ringing.isEmpty()) return;

        SoundManager sounds = Minecraft.getInstance().getSoundManager();
        if (sounds != null) ringing.forEach(sounds::stop);

        ringing.clear();
    }

    public boolean isPlaying() {
        return ticksLeft > 0;
    }

    private void play() {
        SoundManager sounds = Minecraft.getInstance().getSoundManager();
        if (sounds == null) return;

        SoundInstance beat = SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, 0.85f, volume);
        SoundInstance charge = SimpleSoundInstance.forUI(SoundEvents.WARDEN_SONIC_CHARGE, 1.4f, volume * 0.5f);

        for (SoundInstance instance : new SoundInstance[]{beat, charge}) {
            if (ringing.size() >= MAX_RINGING) ringing.removeFirst();

            ringing.addLast(instance);
            sounds.play(instance);
        }
    }

    public static void stinger(float volume) {
        SoundManager sounds = Minecraft.getInstance().getSoundManager();
        if (sounds == null) return;

        sounds.play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, 0.85f, volume));
        sounds.play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_SONIC_CHARGE, 1.4f, volume * 0.5f));
    }
}
