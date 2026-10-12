package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.ItemStack;

public class ChestAndShulkerStealer extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("steal-delay")
        .description("Delay in ticks between stealing items.")
        .defaultValue(4)
        .min(1)
        .sliderMax(20)
        .build()
    );

    private int tickCounter = 0;
    private int currentSlot = 0;

    // syncId changes per container. without this the next chest carried on from the old slot
    // and skipped everything before it
    private int activeSyncId = -1;

    public ChestAndShulkerStealer() {
        super(GlazedAddon.CATEGORY, "storage-stealer", "Steals items from chests and shulkers.");
    }
    
    @Override
    public void onActivate() {
        tickCounter = 0;
        currentSlot = 0;
        activeSyncId = -1;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.gameMode == null || mc.gui.screen() == null) return;

        int containerSize = 0;

        if (mc.player.containerMenu instanceof ShulkerBoxMenu) {
            containerSize = 27;
        } else if (mc.player.containerMenu instanceof ChestMenu handler) {
            containerSize = handler.getContainer().getContainerSize();
        } else {
            return;
        }

        if (mc.player.containerMenu.containerId != activeSyncId) {
            activeSyncId = mc.player.containerMenu.containerId;
            currentSlot = 0;
        }

        if (++tickCounter < Math.max(1, delay.get())) return;
        tickCounter = 0;

        while (currentSlot < containerSize) {
            ItemStack stack = mc.player.containerMenu.getSlot(currentSlot).getItem();
            if (!stack.isEmpty()) {
                mc.gameMode.handleContainerInput(
                    mc.player.containerMenu.containerId,
                    currentSlot,
                    0,
                    ContainerInput.QUICK_MOVE,
                    mc.player
                );
                currentSlot++;
                return;
            }
            currentSlot++;
        }

        currentSlot = 0;
    }
}
