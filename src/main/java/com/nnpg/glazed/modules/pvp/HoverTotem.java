package com.nnpg.glazed.modules.pvp;

import com.nnpg.glazed.GlazedAddon;
import com.nnpg.glazed.mixins.HandledScreenMixin;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;

public class HoverTotem extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> hotbarTotem = sgGeneral.add(new BoolSetting.Builder()
        .name("hotbar-totem")
        .description("Also places a totem in your preferred hotbar slot.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> hotbarSlot = sgGeneral.add(new IntSetting.Builder()
        .name("hotbar-slot")
        .description("Your preferred hotbar slot for totem (1-9).")
        .defaultValue(1)
        .min(1)
        .max(9)
        .sliderMin(1)
        .sliderMax(9)
        .build()
    );

    private final Setting<Boolean> autoSwitchToTotem = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-switch-to-totem")
        .description("Automatically switches to totem slot when inventory is opened.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> autoInvOpen = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-inv-open")
        .description("Opens your inventory by itself when the offhand totem is gone, then closes it once a new one is equipped.")
        .defaultValue(false)
        .build()
    );

    private boolean shouldOpenInv;
    private boolean totemEquipped;
    private boolean wasAutoOpened;

    public HoverTotem() {
        super(GlazedAddon.pvp, "hover-totem", "Equips a totem in offhand and optionally hotbar when hovering over one in inventory.");
    }

    @Override
    public void onActivate() {
        resetState();
    }

    @Override
    public void onDeactivate() {
        resetState();
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (!autoInvOpen.get() || mc.player == null || mc.level == null) return;
        if (!(event.packet instanceof ClientboundEntityEventPacket packet)) return;
        if (packet.getEventId() != 35 || packet.getEntity(mc.level) != mc.player) return;
        if (mc.gui.screen() != null || !hasTotemInInventory()) return;

        shouldOpenInv = true;
        totemEquipped = false;
        wasAutoOpened = true;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.gameMode == null) return;

        if (autoInvOpen.get() && mc.gui.screen() == null
            && !mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)
            && hasTotemInInventory()) {
            shouldOpenInv = true;
        }

        if (shouldOpenInv && mc.gui.screen() == null) {
            if (hasTotemInInventory()) {
                mc.execute(() -> {
                    if (mc.player == null) return;

                    mc.gui.setScreen(new InventoryScreen(mc.player));
                    shouldOpenInv = false;
                    totemEquipped = false;
                    wasAutoOpened = true;
                });
            } else {
                shouldOpenInv = false;
            }

            return;
        }

        Screen currentScreen = mc.gui.screen();
        if (!(currentScreen instanceof InventoryScreen inventoryScreen)) {
            if (wasAutoOpened && !totemEquipped && mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
                totemEquipped = true;
                wasAutoOpened = false;
            }

            if (wasAutoOpened && !totemEquipped) shouldOpenInv = true;

            return;
        }

        if (autoInvOpen.get() && !totemEquipped && mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            totemEquipped = true;
            wasAutoOpened = false;

            mc.execute(() -> {
                if (mc.player != null) mc.player.closeContainer();
            });

            return;
        }

        Slot focusedSlot = getFocusedSlotSafe(inventoryScreen);

        if (focusedSlot == null || focusedSlot.getContainerSlot() > 35) return;

        if (autoSwitchToTotem.get()) {
            mc.player.getInventory().setSelectedSlot(hotbarSlot.get() - 1);
        }

        if (!focusedSlot.getItem().is(Items.TOTEM_OF_UNDYING)) return;

        int slotIndex = focusedSlot.getContainerSlot();
        int syncId = inventoryScreen.getMenu().containerId;

        if (!mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            equipOffhandTotem(syncId, slotIndex);
            return;
        }

        if (hotbarTotem.get()) {
            int hotbarIndex = hotbarSlot.get() - 1;
            if (!mc.player.getInventory().getItem(hotbarIndex).is(Items.TOTEM_OF_UNDYING)) {
                equipHotbarTotem(syncId, slotIndex, hotbarIndex);
            }
        }
    }

    private boolean hasTotemInInventory() {
        if (mc.player == null) return false;

        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getItem(i).is(Items.TOTEM_OF_UNDYING)) return true;
        }

        return false;
    }

    private void equipOffhandTotem(int syncId, int slotIndex) {
        mc.gameMode.handleContainerInput(syncId, slotIndex, 40, ContainerInput.SWAP, mc.player);
    }

    private void equipHotbarTotem(int syncId, int slotIndex, int hotbarIndex) {
        mc.gameMode.handleContainerInput(syncId, slotIndex, hotbarIndex, ContainerInput.SWAP, mc.player);
    }

    private void resetState() {
        shouldOpenInv = false;
        totemEquipped = false;
        wasAutoOpened = false;
    }

    private Slot getFocusedSlotSafe(InventoryScreen screen) {
        if (screen instanceof HandledScreenMixin mixin) {
            return mixin.glazed$getFocusedSlot();
        }

        return null;
    }
}
