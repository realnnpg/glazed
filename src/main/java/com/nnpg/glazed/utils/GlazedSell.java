package com.nnpg.glazed.utils;

import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.dialog.DialogScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.List;
import java.util.Locale;

import static meteordevelopment.meteorclient.MeteorClient.mc;

// shared /sell handling. items go anywhere except the bottom row (thats the buttons), then it
// makes you confirm twice, sometimes a glass pane and sometimes a Yes / No screen
public final class GlazedSell {

    private GlazedSell() {}

    public static void openSell() {
        ChatUtils.sendPlayerMsg("/sell");
    }

    public static ChestMenu container() {
        if (mc.player == null) return null;

        return mc.player.containerMenu instanceof ChestMenu handler ? handler : null;
    }

    public static int containerSlots(ChestMenu handler) {
        return handler.getRowCount() * 9;
    }

    public static int usableSlots(ChestMenu handler) {
        return Math.max(0, containerSlots(handler) - 9);
    }

    public static int firstEmptyUsableSlot(ChestMenu handler) {
        int limit = Math.min(usableSlots(handler), handler.slots.size());

        for (int slot = 0; slot < limit; slot++) {
            if (handler.getSlot(slot).getItem().isEmpty()) return slot;
        }

        return -1;
    }

    public static boolean isConfirmScreen(ChestMenu handler) {
        return findConfirmSlot(handler) >= 0;
    }

    public static int findConfirmSlot(ChestMenu handler) {
        int limit = Math.min(containerSlots(handler), handler.slots.size());

        for (int slot = 0; slot < limit; slot++) {
            if (isConfirmButton(handler.getSlot(slot).getItem())) return slot;
        }

        return -1;
    }

    public static boolean isConfirmButton(ItemStack stack) {
        if (stack.isEmpty()) return false;

        if (stack.is(Items.STAINED_GLASS_PANE.lime()) || stack.is(Items.STAINED_GLASS_PANE.green())
            || stack.is(Items.DYE.lime()) || stack.is(Items.DYE.green())) {
            return true;
        }

        String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);

        // "no" has to be matched exactly or it also matches "not enough", "nope" and so on
        if (name.equals("no") || name.contains("cancel") || name.contains("decline")) return false;

        return name.contains("yes") || name.contains("confirm") || name.contains("accept") || name.contains("sell");
    }

    public static boolean isDeclineButton(ItemStack stack) {
        if (stack.isEmpty()) return false;

        if (stack.is(Items.STAINED_GLASS_PANE.red()) || stack.is(Items.BARRIER)) return true;

        String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
        return name.equals("no") || name.contains("cancel") || name.contains("decline");
    }

    public static boolean clickConfirm(ChestMenu handler) {
        if (mc.gameMode == null || mc.player == null) return false;

        int slot = findConfirmSlot(handler);
        if (slot < 0) return false;

        mc.gameMode.handleContainerInput(handler.containerId, slot, 0, ContainerInput.PICKUP, mc.player);
        return true;
    }

    public static boolean depositNext(ChestMenu handler) {
        if (mc.gameMode == null || mc.player == null) return false;
        if (firstEmptyUsableSlot(handler) < 0) return false;

        int containerSlots = containerSlots(handler);

        for (int slot = containerSlots; slot < handler.slots.size(); slot++) {
            if (handler.getSlot(slot).getItem().isEmpty()) continue;

            mc.gameMode.handleContainerInput(handler.containerId, slot, 0, ContainerInput.QUICK_MOVE, mc.player);
            return true;
        }

        return false;
    }

    // porting note: server dialogs are 1.21.6+ only. going back, delete this, isDialogOpen,
    // pressMatching and the DialogScreen import. the rest of this file works on any version
    public static boolean clickDialogYes() {
        if (!(mc.gui.screen() instanceof DialogScreen<?>)) return false;

        return pressMatching(mc.gui.screen().children());
    }

    public static boolean isDialogOpen() {
        return mc.gui.screen() instanceof DialogScreen<?>;
    }

    private static boolean pressMatching(List<? extends GuiEventListener> elements) {
        for (GuiEventListener element : elements) {
            if (element instanceof AbstractButton button) {
                String label = button.getMessage().getString().toLowerCase(Locale.ROOT);

                if (label.equals("no") || label.contains("cancel") || label.contains("decline")) continue;

                if (label.contains("yes") || label.contains("confirm") || label.contains("accept")
                    || label.contains("sell") || label.contains("ok")) {
                    // vanilla buttons ignore the input arg, so null it and swallow the ones that dont
                    try {
                        button.onPress(null);
                    } catch (Throwable t) {
                        return false;
                    }

                    return true;
                }
            }

            if (element instanceof ContainerEventHandler parent && pressMatching(parent.children())) return true;
        }

        return false;
    }

    public static void close() {
        if (mc.player != null) mc.player.closeContainer();
    }

    public static boolean isOurScreen(AbstractContainerMenu handler) {
        return handler instanceof ChestMenu;
    }
}
