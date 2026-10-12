package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import com.nnpg.glazed.utils.GlazedSell;
import com.nnpg.glazed.VersionUtil;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class AHSell extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> sellPrice = sgGeneral.add(new StringSetting.Builder()
        .name("sell-price")
        .description("The price to list each hotbar item for. Supports K/M/B.")
        .defaultValue("30k")
        .build()
    );

    private final Setting<Integer> slotDelay = sgGeneral.add(new IntSetting.Builder()
        .name("slot-delay")
        .description("Ticks to wait after switching hotbar slot before sending /ah sell. Must be at least 1 so the server sees the slot change first.")
        .defaultValue(2)
        .min(1)
        .max(20)
        .sliderMax(10)
        .build()
    );

    private final Setting<Integer> confirmDelay = sgGeneral.add(new IntSetting.Builder()
        .name("confirm-delay")
        .description("Delay in ticks before clicking the confirm button.")
        .defaultValue(10)
        .min(0)
        .max(100)
        .sliderMax(20)
        .build()
    );

    private final Setting<Integer> confirmTimeout = sgGeneral.add(new IntSetting.Builder()
        .name("confirm-timeout")
        .description("Give up waiting for the confirm screen after this many ticks and move to the next slot.")
        .defaultValue(60)
        .min(10)
        .max(200)
        .sliderMax(100)
        .build()
    );

    private final Setting<Integer> gapDelay = sgGeneral.add(new IntSetting.Builder()
        .name("gap-delay")
        .description("Ticks to wait after a sale completes before starting the next slot, so the previous menu can close.")
        .defaultValue(5)
        .min(0)
        .max(40)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> notifications = sgGeneral.add(new BoolSetting.Builder()
        .name("notifications")
        .description("Show chat notifications.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> enableFilter = sgGeneral.add(new BoolSetting.Builder()
        .name("enable-item-filter")
        .description("Only sell selected item type from the hotbar.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Item> filterItem = sgGeneral.add(new ItemSetting.Builder()
        .name("filter-item")
        .description("Only this item will be sold when filter is enabled.")
        .defaultValue(Items.DIAMOND)
        .build()
    );

    private enum State { SELECT, SEND, AWAIT_CONFIRM, GAP }

    private State state = State.SELECT;
    private int delayCounter = 0;
    private int currentSlot = 0;
    private int waited = 0;
    private int sold = 0;

    public AHSell() {
        super(GlazedAddon.CATEGORY, "ah-sell", "Automatically sells all hotbar items using /ah sell.");
    }

    @Override
    public void onActivate() {
        if (!isValidPrice(sellPrice.get())) {
            if (notifications.get()) error("Invalid price format: " + sellPrice.get());
            toggle();
            return;
        }

        if (!hasSellableItemsInHotbar()) {
            if (notifications.get()) error("No sellable items found in hotbar.");
            toggle();
            return;
        }

        currentSlot = 0;
        sold = 0;
        waited = 0;
        delayCounter = 0;
        state = State.SELECT;
    }

    @Override
    public void onDeactivate() {
        state = State.SELECT;
        delayCounter = 0;
        waited = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.gameMode == null || mc.getConnection() == null) return;

        if (delayCounter > 0) {
            delayCounter--;
            return;
        }

        switch (state) {
            case SELECT -> tickSelect();
            case SEND -> tickSend();
            case AWAIT_CONFIRM -> tickAwaitConfirm();
            case GAP -> {
                currentSlot++;
                state = State.SELECT;
            }
        }
    }

    private void tickSelect() {
        if (currentSlot > 8) {
            if (notifications.get()) info("Finished processing hotbar. Sold %d item(s).", sold);
            toggle();
            return;
        }

        ItemStack stack = mc.player.getInventory().getItem(currentSlot);

        if (stack.isEmpty()) {
            currentSlot++;
            return;
        }

        if (enableFilter.get() && !stack.is(filterItem.get())) {
            if (notifications.get()) info("Skipping slot " + currentSlot + " (does not match filter).");
            currentSlot++;
            return;
        }

        VersionUtil.setSelectedSlot(mc.player, currentSlot);
        delayCounter = slotDelay.get();
        state = State.SEND;
    }

    private void tickSend() {
        String price = sellPrice.get().trim();
        double parsedPrice = parsePrice(price);

        if (parsedPrice <= 0) {
            if (notifications.get()) error("Invalid price format: " + price);
            toggle();
            return;
        }

        ItemStack stack = mc.player.getInventory().getItem(currentSlot);
        if (stack.isEmpty()) {
            currentSlot++;
            state = State.SELECT;
            return;
        }

        if (notifications.get()) {
            info("Sending /ah sell %s for slot %d", formatPrice(parsedPrice), currentSlot);
        }

        mc.getConnection().sendCommand("ah sell " + price);
        delayCounter = confirmDelay.get();
        waited = 0;
        state = State.AWAIT_CONFIRM;
    }

    private void tickAwaitConfirm() {
        if (GlazedSell.isDialogOpen()) {
            if (GlazedSell.clickDialogYes()) {
                sold++;
                if (notifications.get()) info("Sold item in hotbar slot " + currentSlot + ".");
                delayCounter = gapDelay.get();
                state = State.GAP;
            }
            return;
        }

        AbstractContainerMenu screenHandler = mc.player.containerMenu;

        if (screenHandler instanceof ChestMenu handler && handler.getRowCount() == 3) {
            if (GlazedSell.clickConfirm(handler)) {
                sold++;
                if (notifications.get()) info("Sold item in hotbar slot " + currentSlot + ".");
                delayCounter = gapDelay.get();
                state = State.GAP;
                return;
            }
        }

        if (++waited >= confirmTimeout.get()) {
            if (notifications.get()) warning("No confirm screen for slot " + currentSlot + ", skipping.");
            GlazedSell.close();
            delayCounter = gapDelay.get();
            state = State.GAP;
        }
    }

    @EventHandler
    private void onChatMessage(ReceiveMessageEvent event) {
        String msg = event.getMessage().getString();

        if (msg.contains("[Meteor]")) return;

        if (msg.contains("You have too many listed items.")) {
            if (notifications.get()) warning("Sell cap reached, disabling module.");
            toggle();
        }
    }

    private boolean hasSellableItemsInHotbar() {
        for (int slot = 0; slot <= 8; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;

            if (enableFilter.get()) {
                if (stack.is(filterItem.get())) return true;
            } else {
                return true;
            }
        }
        return false;
    }

    private boolean isValidPrice(String priceStr) {
        return parsePrice(priceStr) > 0;
    }

    private double parsePrice(String priceStr) {
        if (priceStr == null || priceStr.isEmpty()) return -1.0;

        String cleaned = priceStr.trim().toUpperCase();
        double multiplier = 1.0;

        if (cleaned.endsWith("B")) {
            multiplier = 1_000_000_000.0;
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        } else if (cleaned.endsWith("M")) {
            multiplier = 1_000_000.0;
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        } else if (cleaned.endsWith("K")) {
            multiplier = 1_000.0;
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }

        try {
            return Double.parseDouble(cleaned) * multiplier;
        } catch (NumberFormatException e) {
            return -1.0;
        }
    }

    private String formatPrice(double price) {
        if (price >= 1_000_000_000) {
            return String.format("%.2fB", price / 1_000_000_000);
        } else if (price >= 1_000_000) {
            return String.format("%.2fM", price / 1_000_000);
        } else if (price >= 1_000) {
            return String.format("%.2fK", price / 1_000);
        } else {
            return String.format("%.2f", price);
        }
    }
}
