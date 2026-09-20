package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public class RTPBaseFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> notifications = sgGeneral.add(new BoolSetting.Builder()
        .name("notifications")
        .description("Show chat feedback.")
        .defaultValue(true)
        .build()
    );

    private BlockPos currentTarget = null;
    private boolean waitingForTeleport = false;
    private long lastTeleportTime = 0;

    public RTPBaseFinder() {
        super(GlazedAddon.CATEGORY, "rtp-base-finder", "Aimbots downward, holds left click to mine to Y=-58, then runs /rtp east.");
    }

    @Override
    public void onActivate() {
        currentTarget = mc.player.blockPosition().below();
        waitingForTeleport = false;
        if (notifications.get()) info("RTPBaseFinder activated. Mining straight down.");
    }

    @Override
    public void onDeactivate() {
        releaseLeftClick();
        if (notifications.get()) info("RTPBaseFinder disabled.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null || mc.options == null || mc.gui.screen() != null) return;

        if (waitingForTeleport) {
            if (System.currentTimeMillis() - lastTeleportTime > 3000) {
                currentTarget = mc.player.blockPosition().below();
                waitingForTeleport = false;
            }
            return;
        }

        aimDownward();

        if (currentTarget == null || currentTarget.getY() <= -58) {
            triggerTeleport();
            return;
        }

        holdLeftClick();

        if (mc.level.getBlockState(currentTarget).isAir()) {
            currentTarget = currentTarget.below();
        }
    }

    private void aimDownward() {
        mc.player.setXRot(90f); // Look straight down
        Vec3 eyePos = mc.player.getEyePosition();
        Vec3 targetVec = Vec3.atCenterOf(currentTarget);
        Vec3 dir = targetVec.subtract(eyePos).normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        mc.player.setYRot(yaw);
    }

    private void holdLeftClick() {
        KeyMapping.set(mc.options.keyAttack.getDefaultKey(), true);
    }

    private void releaseLeftClick() {
        KeyMapping.set(mc.options.keyAttack.getDefaultKey(), false);
    }

    private void triggerTeleport() {
        releaseLeftClick();
        mc.player.connection.sendCommand("rtp east");
        lastTeleportTime = System.currentTimeMillis();
        waitingForTeleport = true;
        if (notifications.get()) info("Reached Y=-58. Teleporting east.");
    }
}
