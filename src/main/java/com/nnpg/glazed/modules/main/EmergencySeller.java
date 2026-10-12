package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import com.nnpg.glazed.utils.GlazedSell;
import com.nnpg.glazed.VersionUtil;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.*;

public class EmergencySeller extends Module {

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<List<Item>> itemsToSell = sg.add(new ItemListSetting.Builder()
        .name("items-to-sell")
        .description("Items to sell.")
        .defaultValue(List.of(Items.ELYTRA, Items.NETHERITE_SWORD))
        .build()
    );

    private final Setting<Boolean> notifications = sg.add(new BoolSetting.Builder()
        .name("notifications")
        .description("Show chat feedback.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> sellSlot0 = sg.add(new BoolSetting.Builder()
        .name("sell-hotbar-slot-1")
        .description("Sell Sells any item that occupies slot 1 (leave it ON)")
        .defaultValue(true)
        .build()
    );

    private final List<Integer> targets = new ArrayList<>();
    private final Map<Integer, Integer> retries = new HashMap<>();

    private int index = 0;
    private int ticksSinceCommand = 0;
    private boolean commandSent = false;
    private final int maxRetries = 3;
    private final int timeoutTicks = 30;

    private Item currentItem;

    public EmergencySeller() {
        super(GlazedAddon.CATEGORY, "emergency-seller", "Panic sell selected items.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.player.getInventory() == null) {
            toggle();
            return;
        }

        targets.clear();
        retries.clear();
        index = 0;
        commandSent = false;
        ticksSinceCommand = 0;

        for (int i = 0; i < 40; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);

            if (i == 0 && sellSlot0.get() && !stack.isEmpty()) {
                targets.add(i);
            }
            else if (i != 0 && !stack.isEmpty() && itemsToSell.get().contains(stack.getItem())) {
                targets.add(i);
            }
        }

        if (targets.isEmpty()) {
            ChatUtils.warning("No items to sell.");
            toggle();
        } else {
            if (notifications.get()) {
                ChatUtils.info("Found " + targets.size() + " items to sell.");
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.gameMode == null || index >= targets.size()) {
            toggle();
            return;
        }

        int slot = targets.get(index);
        ItemStack stack = mc.player.getInventory().getItem(slot);
        currentItem = stack.getItem();

        int retry = retries.getOrDefault(slot, 0);

        if (!commandSent) {

            if (slot != 0 && mc.player.containerMenu != null) {
                int realId = getSlotId(slot);
                mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, realId, 0, ContainerInput.SWAP, mc.player);
            }

            VersionUtil.setSelectedSlot(mc.player, 0);

            if (mc.player.getMainHandItem().is(currentItem)) {
                ticksSinceCommand++;

                if (ticksSinceCommand >= 5) {
                    mc.player.connection.sendCommand("ah sell 1b");
                    mc.gui.setScreen(null);
                    commandSent = true;
                    ticksSinceCommand = 0;
                }
            } else {
                ticksSinceCommand = 0;
            }
        }

        if (commandSent) {
            ticksSinceCommand++;

            // the confirm is sometimes a server dialog with Yes / No buttons instead of a chest
            if (GlazedSell.isDialogOpen()) {
                if (GlazedSell.clickDialogYes()) {
                    if (notifications.get()) ChatUtils.info("Sold " + currentItem.getName(ItemStack.EMPTY).getString());
                    nextItem();
                }
                return;
            }

            if (mc.gui.screen() instanceof ContainerScreen screen) {
                AbstractContainerMenu handler = screen.getMenu();

                if (confirmClick(handler, 15)) return;


                for (int i = 0; i < handler.slots.size(); i++) {
                    if (confirmClick(handler, i)) return;
                }
            }

            if (ticksSinceCommand > timeoutTicks) {
                retries.put(slot, retry + 1);
                if (retry + 1 >= maxRetries) {
                    if (notifications.get()) {
                        ChatUtils.warning("Timeout for item %s.", currentItem.getName(ItemStack.EMPTY).getString());
                    }
                    nextItem();
                } else {
                    if (notifications.get()) {
                        ChatUtils.info("Retrying item", currentItem.getName(ItemStack.EMPTY).getString(), retry + 2, maxRetries);
                    }
                    commandSent = false; // retry
                    ticksSinceCommand = 0;
                }
            }
        }
    }

    private boolean confirmClick(AbstractContainerMenu handler, int slotId) {
        if (slotId >= handler.slots.size()) return false;

        Slot slot = handler.getSlot(slotId);
        if (slot != null && !slot.getItem().isEmpty() && GlazedSell.isConfirmButton(slot.getItem())) {
            mc.gameMode.handleContainerInput(handler.containerId, slot.index, 0, ContainerInput.PICKUP, mc.player);
            if (notifications.get()) {
                ChatUtils.info("Sold " + currentItem.getName(ItemStack.EMPTY).getString());
            }
            nextItem();
            return true;
        }
        return false;
    }

    private void nextItem() {
        index++;
        commandSent = false;
        ticksSinceCommand = 0;
    }

    private boolean isGreenGlass(ItemStack stack) {
        return stack.getItem() == Items.STAINED_GLASS_PANE.lime() || stack.getItem() == Items.STAINED_GLASS_PANE.green();
    }

    private int getSlotId(int invIndex) {
        if (mc.player.containerMenu != null) {
            for (Slot slot : mc.player.containerMenu.slots) {
                if (slot.container == mc.player.getInventory() && slot.getContainerSlot() == invIndex) {
                    return slot.index;
                }
            }
        }
        return invIndex;
    }

    @Override
    public void onDeactivate() {
        targets.clear();
        retries.clear();
        index = 0;
        commandSent = false;
        ticksSinceCommand = 0;
        currentItem = null;
    }
}
