package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class SpawnerDropper extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> notifications = sgGeneral.add(new BoolSetting.Builder()
        .name("notifications")
        .description("Show chat feedback.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay between clicks in ticks.")
        .defaultValue(5)
        .min(1)
        .max(20)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> boneOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("bone-only")
        .description("Only drop bones. Stops when arrows are detected in slots 0-44.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> autoReopenInterval = sgGeneral.add(new IntSetting.Builder()
        .name("auto-reopen-interval")
        .description("Time in minutes to auto-reopen spawner.")
        .defaultValue(10)
        .min(1)
        .max(600)
        .sliderMax(600)
        .build()
    );

    private int tickCounter = 0;
    private int currentStep = 0;
    private int checkDelayCounter = 0;
    private int reopenTimer = 0;
    private BlockPos spawnerPos = null;
    private boolean waitingForInterval = false;

    private static final int CHECK_DELAY = 3;

    public SpawnerDropper() {
        super(GlazedAddon.CATEGORY, "spawner-dropper", "Drops all items from spawners");
    }

    private boolean hasArrowsInInventory(AbstractContainerScreen<?> screen) {
        for (int i = 0; i <= 44; i++) {
            if (!screen.getMenu().getSlot(i).getItem().isEmpty() &&
                screen.getMenu().getSlot(i).getItem().getItem() == Items.ARROW) {
                return true;
            }
        }
        return false;
    }

    private BlockPos findNearbySpawner() {
        if (mc.player == null || mc.level == null) return null;

        BlockPos playerPos = mc.player.blockPosition();
        for (int x = -6; x <= 6; x++) {
            for (int y = -6; y <= 6; y++) {
                for (int z = -6; z <= 6; z++) {
                    BlockPos pos = playerPos.offset(x, y, z);
                    if (mc.level.getBlockState(pos).getBlock() == net.minecraft.world.level.block.Blocks.SPAWNER) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private void openSpawner() {
        if (spawnerPos == null) {
            spawnerPos = findNearbySpawner();
        }

        if (spawnerPos != null && mc.gameMode != null && mc.player != null) {
            BlockHitResult hitResult = new BlockHitResult(
                Vec3.atCenterOf(spawnerPos),
                Direction.UP,
                spawnerPos,
                false
            );
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);
            currentStep = 0;
            checkDelayCounter = 0;
            tickCounter = 0;
            waitingForInterval = false;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.gameMode == null || mc.level == null) return;

        if (spawnerPos == null) {
            spawnerPos = findNearbySpawner();
            if (spawnerPos != null) {
                if (notifications.get()) info("Spawner detected nearby!");
            }
        }

        reopenTimer++;
        int reopenIntervalTicks = autoReopenInterval.get() * 60 * 20;

        // just count and wait ty
        if (waitingForInterval) {
            if (reopenTimer >= reopenIntervalTicks) {
                if (notifications.get()) info("Interval reached - opening spawner.");
                openSpawner();
                reopenTimer = 0;
            }
            return;
        }

        if (!(mc.gui.screen() instanceof AbstractContainerScreen)) {
            if (reopenTimer >= reopenIntervalTicks || reopenTimer == 20) {
                if (spawnerPos != null) {
                    openSpawner();
                    reopenTimer = 0;
                }
            }
            return;
        }

        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) mc.gui.screen();

        // arrows found, close and wait for interval sigma
        if (boneOnly.get() && hasArrowsInInventory(screen)) {
            if (notifications.get()) info("Arrows detected - closing spawner and waiting for interval.");
            mc.gui.screen().onClose();
            waitingForInterval = true;
            reopenTimer = 0;
            return;
        }

        if (currentStep == 2 || currentStep == 5) {
            checkDelayCounter++;
            if (checkDelayCounter >= CHECK_DELAY) {
                if (screen.getMenu().getSlot(0).getItem().isEmpty()) {
                    if (notifications.get()) info("All bones dropped - closing and waiting for interval.");
                    mc.gui.screen().onClose();
                    waitingForInterval = true;
                    reopenTimer = 0;
                    return;
                } else {
                    if (currentStep == 2) {
                        currentStep = 3;
                    } else if (currentStep == 5) {
                        currentStep = 0;
                    }
                    checkDelayCounter = 0;
                }
            }
            return;
        }

        tickCounter++;
        if (tickCounter < delay.get()) {
            return;
        }

        tickCounter = 0;

        switch (currentStep) {
            case 0:
                mc.gameMode.handleContainerInput(screen.getMenu().containerId, 50, 0, ContainerInput.PICKUP, mc.player);
                currentStep = 1;
                break;
            case 1:
                mc.gameMode.handleContainerInput(screen.getMenu().containerId, 53, 0, ContainerInput.PICKUP, mc.player);
                currentStep = 2;
                checkDelayCounter = 0;
                break;
            case 3:
                mc.gameMode.handleContainerInput(screen.getMenu().containerId, 50, 0, ContainerInput.PICKUP, mc.player);
                currentStep = 4;
                break;
            case 4:
                mc.gameMode.handleContainerInput(screen.getMenu().containerId, 53, 0, ContainerInput.PICKUP, mc.player);
                currentStep = 5;
                checkDelayCounter = 0;
                break;
        }
    }

    @Override
    public void onActivate() {
        tickCounter = 0;
        currentStep = 0;
        checkDelayCounter = 0;
        reopenTimer = 0;
        spawnerPos = null;
        waitingForInterval = false;
        if (notifications.get()) info("SpawnerDropper activated - will auto-open spawner.");
    }

    @Override
    public void onDeactivate() {
        currentStep = 0;
        checkDelayCounter = 0;
        reopenTimer = 0;
        spawnerPos = null;
        waitingForInterval = false;
        if (mc.gui.screen() != null) {
            mc.gui.setScreen(null);
        }
    }
}
