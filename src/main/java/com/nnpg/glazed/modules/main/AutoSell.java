package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import com.nnpg.glazed.utils.GlazedSell;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.List;

public class AutoSell extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<SellMode> mode = sgGeneral.add(new EnumSetting.Builder<SellMode>()
        .name("mode")
        .description("Whether to whitelist or blacklist the selected items.")
        .defaultValue(SellMode.Whitelist)
        .build()
    );

    private final Setting<List<Item>> itemList = sgGeneral.add(new ItemListSetting.Builder()
        .name("items")
        .description("Items to sell.")
        .defaultValue(List.of(Items.SEA_PICKLE))
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks between batches. Zero sells as fast as the game ticks.")
        .defaultValue(0)
        .min(0)
        .max(20)
        .sliderMax(20)
        .build()
    );

    private final Setting<Integer> batchSize = sgGeneral.add(new IntSetting.Builder()
        .name("stacks-per-tick")
        .description("How many stacks to shift-click in a single tick. Higher empties the inventory in one go but sends that many packets at once.")
        .defaultValue(36)
        .min(1)
        .max(36)
        .sliderRange(1, 36)
        .build()
    );

    private final Setting<Integer> openDelay = sgGeneral.add(new IntSetting.Builder()
        .name("open-delay")
        .description("Ticks to wait for the sell menu to open before trying again.")
        .defaultValue(10)
        .min(1)
        .max(40)
        .sliderRange(1, 40)
        .build()
    );

    private final Setting<Boolean> continuous = sgGeneral.add(new BoolSetting.Builder()
        .name("continuous")
        .description("Keep running after the inventory is empty and sell anything new that shows up, until you turn the module off.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> idleDelay = sgGeneral.add(new IntSetting.Builder()
        .name("idle-delay")
        .description("Ticks between inventory checks while there is nothing to sell.")
        .defaultValue(20)
        .min(1)
        .max(200)
        .sliderRange(1, 200)
        .visible(continuous::get)
        .build()
    );

    private final Setting<Boolean> notifications = sgGeneral.add(new BoolSetting.Builder()
        .name("notifications")
        .description("Show chat feedback.")
        .defaultValue(true)
        .build()
    );

    private int delayCounter;
    private boolean needsReopen;

    public AutoSell() {
        super(GlazedAddon.CATEGORY, "auto-sell", "Automatically sells items.");
    }

    @Override
    public void onActivate() {
        delayCounter = openDelay.get();
        needsReopen = false;
    }

    @Override
    public void onDeactivate() {
        needsReopen = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.gameMode == null) return;

        if (delayCounter > 0) {
            delayCounter--;
            return;
        }

        if (needsReopen) {
            GlazedSell.openSell();
            needsReopen = false;
            delayCounter = openDelay.get();
            return;
        }

        ChestMenu container = GlazedSell.container();

        if (container == null) {
            if (continuous.get() && !hasSellableInInventory()) {
                delayCounter = idleDelay.get();
                return;
            }

            GlazedSell.openSell();
            delayCounter = openDelay.get();
            return;
        }

        if (GlazedSell.firstEmptyUsableSlot(container) < 0) {
            GlazedSell.close();
            needsReopen = hasMatchingItems(container);

            if (needsReopen) delayCounter = delay.get();
            else finish();

            return;
        }

        if (sellBatch(container) > 0) {
            delayCounter = delay.get();
            return;
        }

        GlazedSell.close();
        finish();
    }

    private void finish() {
        if (continuous.get()) {
            delayCounter = idleDelay.get();
            return;
        }

        if (notifications.get()) info("All items sold.");
        toggle();
    }

    private boolean hasSellableInInventory() {
        if (mc.player == null) return false;

        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (!stack.isEmpty() && shouldSellItem(stack.getItem())) return true;
        }

        return false;
    }

    private int sellBatch(ChestMenu container) {
        int containerSlots = GlazedSell.containerSlots(container);
        int budget = batchSize.get();
        int moved = 0;

        for (int slot = containerSlots; slot < container.slots.size() && moved < budget; slot++) {
            if (GlazedSell.firstEmptyUsableSlot(container) < 0) break;

            ItemStack stack = container.getSlot(slot).getItem();
            if (stack.isEmpty() || !shouldSellItem(stack.getItem())) continue;

            mc.gameMode.handleContainerInput(container.containerId, slot, 0, ContainerInput.QUICK_MOVE, mc.player);
            moved++;
        }

        return moved;
    }

    private boolean hasMatchingItems(ChestMenu container) {
        int containerSlots = GlazedSell.containerSlots(container);
        for (int slot = containerSlots; slot < container.slots.size(); slot++) {
            ItemStack stack = container.getSlot(slot).getItem();
            if (!stack.isEmpty() && shouldSellItem(stack.getItem())) return true;
        }
        return false;
    }

    private boolean shouldSellItem(Item item) {
        List<Item> selectedItems = itemList.get();
        if (mode.get() == SellMode.Whitelist) {
            return selectedItems.contains(item);
        } else {
            return !selectedItems.contains(item);
        }
    }

    public enum SellMode {
        Whitelist,
        Blacklist
    }
}
