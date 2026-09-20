package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.references.ItemIds;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class EmergencyOrder extends Module {
    private static final int TIMEOUT_TICKS = 400;
    private static final int RETRY_TICKS = 2;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTrigger = settings.createGroup("Trigger");
    private final SettingGroup sgOrder = settings.createGroup("Order");

    private final Setting<Item> item = sgGeneral.add(new ItemSetting.Builder()
        .name("item")
        .description("What to hand over.")
        .defaultValue(Items.AIR)
        .build()
    );

    private final Setting<Integer> minCount = sgGeneral.add(new IntSetting.Builder()
        .name("min-count")
        .description("Don't bother unless you're holding at least this many.")
        .defaultValue(1)
        .min(1)
        .max(2304)
        .sliderRange(1, 64)
        .build()
    );

    private final Setting<String> recipient = sgGeneral.add(new StringSetting.Builder()
        .name("recipient")
        .description("Who gets the order. Leave blank to send it to whoever set this off.")
        .defaultValue("")
        .build()
    );

    private final Setting<Boolean> notify = sgGeneral.add(new BoolSetting.Builder()
        .name("notify")
        .description("Say something in chat when it fires.")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<String>> triggerNames = sgTrigger.add(new StringListSetting.Builder()
        .name("names")
        .description("Players that set this off when they get close.")
        .defaultValue(Arrays.asList(
            "archivePedro", "Frwost", "W1zoX_", "Fluffymaster07", "bautiedgar",
            "Showered", "PastaGamer08", "Itszdeath", "0Gsummer", "aepmiro", "Munkerlich"
        ))
        .build()
    );

    private final Setting<Integer> range = sgTrigger.add(new IntSetting.Builder()
        .name("range")
        .description("How close they have to be.")
        .defaultValue(64)
        .min(1)
        .max(128)
        .sliderRange(1, 128)
        .build()
    );

    private final Setting<Integer> cooldown = sgOrder.add(new IntSetting.Builder()
        .name("cooldown")
        .description("Ticks before it's allowed to order again.")
        .defaultValue(40)
        .min(0)
        .max(200)
        .sliderRange(0, 200)
        .build()
    );

    private final Setting<Boolean> swapHotbar = sgOrder.add(new BoolSetting.Builder()
        .name("swap-hotbar")
        .description("Hold the item before typing the command.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoDeliver = sgOrder.add(new BoolSetting.Builder()
        .name("auto-deliver")
        .description("Click through the order screens too, not just send the command.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> guiDelay = sgOrder.add(new IntSetting.Builder()
        .name("gui-delay")
        .description("Ticks between clicks in the order screens.")
        .defaultValue(4)
        .min(0)
        .max(40)
        .sliderRange(0, 40)
        .visible(autoDeliver::get)
        .build()
    );

    private enum Step {
        IDLE,
        OPENING,
        PICK_ITEM,
        FILL_DELIVERY,
        CLOSE_DELIVERY,
        CONFIRM
    }

    private Step step = Step.IDLE;
    private int waitTicks;
    private int stepTicks;
    private int cooldownTicks;
    private String triggeredBy = "";

    public EmergencyOrder() {
        super(GlazedAddon.CATEGORY, "emergency-order", "Dumps an item into /order the moment a watched player gets close.");
    }

    @Override
    public void onActivate() {
        clear();
    }

    @Override
    public void onDeactivate() {
        if (mc.player != null && mc.gui.screen() != null) mc.player.closeContainer();
        clear();
    }

    private void clear() {
        step = Step.IDLE;
        waitTicks = 0;
        stepTicks = 0;
        cooldownTicks = 0;
        triggeredBy = "";
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;

        if (step != Step.IDLE) {
            driveScreens();
            return;
        }

        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }

        Item wanted = wantedItem();
        if (wanted == Items.AIR) return;
        if (countOf(wanted) < minCount.get()) return;

        String spotted = spotSomeone();
        if (spotted == null) return;

        fire(wanted, spotted);
    }

    private void fire(Item wanted, String spotted) {
        triggeredBy = spotted;
        if (swapHotbar.get()) holdItem(wanted);

        String target = recipient.get().isBlank() ? triggeredBy : recipient.get().trim();
        ChatUtils.sendPlayerMsg("/order " + target);

        if (notify.get()) info("%s is close. Ordering %s to %s.", triggeredBy, wanted.getName(new ItemStack(wanted)).getString(), target);

        cooldownTicks = cooldown.get();

        if (autoDeliver.get()) {
            step = Step.OPENING;
            waitTicks = guiDelay.get();
            stepTicks = 0;
        }
    }

    private void driveScreens() {
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }

        Item wanted = wantedItem();
        if (wanted == Items.AIR) {
            clear();
            return;
        }

        if (++stepTicks > TIMEOUT_TICKS) {
            if (notify.get()) warning("Order screens never showed up, giving up.");
            if (mc.player != null) mc.player.closeContainer();
            clear();
            return;
        }

        // oof
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen)) {
            if (step == Step.CLOSE_DELIVERY) {
                step = Step.CONFIRM;
                waitTicks = guiDelay.get();
            } else {
                waitTicks = RETRY_TICKS;
            }
            return;
        }

        AbstractContainerMenu handler = screen.getMenu();
        String title = strip(screen.getTitle().getString());

        switch (step) {
            case OPENING -> {
                step = isConfirm(title) ? Step.CONFIRM : isDelivery(title) ? Step.FILL_DELIVERY : Step.PICK_ITEM;
                waitTicks = guiDelay.get();
            }
            case PICK_ITEM -> pickItem(handler, title, wanted);
            case FILL_DELIVERY -> fillDelivery(handler, title, wanted);
            case CLOSE_DELIVERY -> {
                if (mc.player != null) mc.player.closeContainer();
                step = Step.CONFIRM;
                waitTicks = guiDelay.get();
            }
            case CONFIRM -> confirm(handler, title);
            default -> clear();
        }
    }

    private void pickItem(AbstractContainerMenu handler, String title, Item wanted) {
        if (jumpedAhead(title)) return;

        int slot = containerSlotOf(handler, wanted);
        if (slot == -1) {
            waitTicks = RETRY_TICKS;
            return;
        }

        click(handler, slot, ContainerInput.PICKUP);
        step = Step.OPENING;
        waitTicks = guiDelay.get();
    }

    private void fillDelivery(AbstractContainerMenu handler, String title, Item wanted) {
        if (isConfirm(title)) {
            step = Step.CONFIRM;
            waitTicks = guiDelay.get();
            return;
        }

        if (!isDelivery(title)) {
            step = Step.CONFIRM;
            waitTicks = guiDelay.get();
            return;
        }

        if (containerSlotOf(handler, wanted) != -1) {
            step = Step.CLOSE_DELIVERY;
            waitTicks = guiDelay.get();
            return;
        }

        // rip
        ItemStack cursor = handler.getCarried();
        if (!cursor.isEmpty() && cursor.is(wanted)) {
            int empty = emptyContainerSlot(handler);
            if (empty != -1) {
                click(handler, empty, ContainerInput.PICKUP);
                waitTicks = guiDelay.get();
            }
            return;
        }

        int mine = playerSlotOf(handler, wanted);
        if (mine == -1) {
            waitTicks = RETRY_TICKS;
            return;
        }

        click(handler, mine, ContainerInput.QUICK_MOVE);
        waitTicks = guiDelay.get();
    }

    private void confirm(AbstractContainerMenu handler, String title) {
        if (isDelivery(title)) {
            step = Step.FILL_DELIVERY;
            waitTicks = guiDelay.get();
            return;
        }

        if (!isConfirm(title)) {
            waitTicks = RETRY_TICKS;
            return;
        }

        int button = confirmSlot(handler);
        if (button == -1) {
            waitTicks = RETRY_TICKS;
            return;
        }

        click(handler, button, ContainerInput.PICKUP);
        clear();
    }

    private boolean jumpedAhead(String title) {
        if (isConfirm(title)) {
            step = Step.CONFIRM;
            waitTicks = guiDelay.get();
            return true;
        }
        if (isDelivery(title)) {
            step = Step.FILL_DELIVERY;
            waitTicks = guiDelay.get();
            return true;
        }
        return false;
    }

    private void click(AbstractContainerMenu handler, int slotId, ContainerInput action) {
        if (mc.gameMode == null || mc.player == null) return;
        mc.gameMode.handleContainerInput(handler.containerId, slotId, 0, action, mc.player);
    }

    private int containerSize(AbstractContainerMenu handler) {
        return Math.max(0, handler.slots.size() - 36);
    }

    private int containerSlotOf(AbstractContainerMenu handler, Item wanted) {
        int size = containerSize(handler);

        for (Slot slot : handler.slots) {
            if (slot.index >= size) continue;
            if (slot.getItem().is(wanted) && !slot.getItem().isEmpty()) return slot.index;
        }
        return -1;
    }

    private int playerSlotOf(AbstractContainerMenu handler, Item wanted) {
        int size = containerSize(handler);

        for (Slot slot : handler.slots) {
            if (slot.index < size) continue;
            if (slot.getItem().is(wanted) && !slot.getItem().isEmpty()) return slot.index;
        }
        return -1;
    }

    private int emptyContainerSlot(AbstractContainerMenu handler) {
        int size = containerSize(handler);

        for (Slot slot : handler.slots) {
            if (slot.index < size && slot.getItem().isEmpty()) return slot.index;
        }
        return -1;
    }

    private int confirmSlot(AbstractContainerMenu handler) {
        int size = containerSize(handler);
        int fallback = -1;

        for (Slot slot : handler.slots) {
            if (slot.index >= size) continue;

            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;

            if (stack.is(Items.STAINED_GLASS_PANE.lime())) return slot.index;
            if (fallback != -1) continue;

            if (stack.is(Items.STAINED_GLASS_PANE.green())) {
                fallback = slot.index;
                continue;
            }

            String name = strip(stack.getHoverName().getString());
            if (name.contains("confirm") || name.contains("accept") || name.contains("yes")) fallback = slot.index;
        }

        return fallback;
    }

    private boolean isDelivery(String title) {
        return title.contains("deliver") && !title.contains("confirm");
    }

    private boolean isConfirm(String title) {
        return title.contains("confirm");
    }

    private Item wantedItem() {
        Item chosen = item.get();
        return chosen == null ? Items.AIR : chosen;
    }

    private int countOf(Item wanted) {
        int total = 0;

        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.is(wanted)) total += stack.getCount();
        }

        return total;
    }

    private void holdItem(Item wanted) {
        for (int slot = 0; slot < 9; slot++) {
            if (mc.player.getInventory().getItem(slot).is(wanted)) {
                InvUtils.swap(slot, false);
                return;
            }
        }

        for (int slot = 9; slot < 36; slot++) {
            if (!mc.player.getInventory().getItem(slot).is(wanted)) continue;

            InvUtils.move().from(slot).toHotbar(0);
            InvUtils.swap(0, false);
            return;
        }
    }

    private String spotSomeone() {
        List<String> watching = triggerNames.get();
        if (watching.isEmpty()) return null;

        for (Player player : mc.level.players()) {
            if (player == mc.player) continue;
            if (mc.player.distanceTo(player) > range.get()) continue;

            String name = player.getName().getString();
            for (String watched : watching) {
                if (watched != null && watched.equalsIgnoreCase(name)) return name;
            }
        }

        return null;
    }

    private static String strip(String text) {
        return text.replaceAll("§.", "").toLowerCase(Locale.ROOT);
    }
}
