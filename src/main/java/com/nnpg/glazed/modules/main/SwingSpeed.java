package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;

public class SwingSpeed extends Module {
    public static SwingSpeed INSTANCE;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Double> level = sgGeneral.add(new DoubleSetting.Builder()
            .name("level")
            .description("5 = normal vanilla speed. Lower = slower, higher = faster.")
            .defaultValue(1.0)
            .min(1)
            .max(10)
            .sliderMin(1)
            .sliderMax(10)
            .build()
    );

    public SwingSpeed() {
        super(GlazedAddon.CATEGORY, "swing-speed", "Speeds up or slows down your hand swing/mining animation.");
        INSTANCE = this;
    }

    public double getMultiplier() {
        return level.get() / 5.0;
    }
}