package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import com.nnpg.glazed.mixins.ClientPlayerInteractionManagerAccessor;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class GlazedSpeedMine extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> hasteAmplifier = sgGeneral.add(new IntSetting.Builder()
            .name("haste-amplifier")
            .description("Haste level applied client side. Lower it if blocks fail to break or you lag.")
            .defaultValue(6)
            .range(0, 6)
            .sliderRange(0, 6)
            .build()
    );

    private final Setting<Integer> breakCooldown = sgGeneral.add(new IntSetting.Builder()
            .name("break-cooldown")
            .description("Ticks between blocks. Raise it if blocks fail to break or you lag.")
            .defaultValue(0)
            .range(0, 5)
            .sliderRange(0, 5)
            .build()
    );

    public GlazedSpeedMine() {
        super(GlazedAddon.CATEGORY, "glazed-speed-mine", "Mines blocks faster by applying Haste and removing the break delay.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.gameMode == null) return;

        int amplifier = hasteAmplifier.get();
        MobEffectInstance haste = mc.player.getEffect(MobEffects.DIG_SPEED);
        if (haste == null || haste.getAmplifier() != amplifier) {
            mc.player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, -1, amplifier, false, false, false));
        }

        ((ClientPlayerInteractionManagerAccessor) mc.gameMode).setBlockBreakingCooldown(breakCooldown.get());
    }

    @Override
    public void onDeactivate() {
        if (mc.player == null) return;

        MobEffectInstance haste = mc.player.getEffect(MobEffects.DIG_SPEED);
        if (haste != null && haste.isInfiniteDuration()) {
            mc.player.removeEffect(MobEffects.DIG_SPEED);
        }
    }
}