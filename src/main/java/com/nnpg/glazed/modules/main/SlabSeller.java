package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import com.nnpg.glazed.utils.GlazedSell;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Random;

public class SlabSeller extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSell = settings.createGroup("Sell menu");
    private final SettingGroup sgTiming = settings.createGroup("Timing");

    private final Setting<Boolean> anySlab = sgGeneral.add(new BoolSetting.Builder()
        .name("any-slab")
        .description("Take every kind of slab. Off restricts it to the item below.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Item> item = sgGeneral.add(new ItemSetting.Builder()
        .name("item")
        .description("The only item taken when any-slab is off.")
        .defaultValue(Items.SMOOTH_STONE_SLAB)
        .build()
    );

    private final Setting<Boolean> requireChestLook = sgGeneral.add(new BoolSetting.Builder()
        .name("require-chest-look")
        .description("Only start a cycle while your crosshair is on a chest. That chest is where the slabs come from, and looking away is how you pause it.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> allowBarrels = sgGeneral.add(new BoolSetting.Builder()
        .name("allow-barrels")
        .description("Count barrels as chests for the look check.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> notifications = sgGeneral.add(new BoolSetting.Builder()
        .name("notifications")
        .description("Show chat notifications.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> clickConfirm = sgSell.add(new BoolSetting.Builder()
        .name("click-confirm")
        .description("Click the confirm button in the bottom row before closing. Closing sells on its own, so this is belt and braces.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> menuTimeout = sgSell.add(new IntSetting.Builder()
        .name("menu-timeout")
        .description("Ticks to wait for a menu before giving up on this cycle.")
        .defaultValue(80)
        .min(20)
        .max(400)
        .sliderMax(200)
        .build()
    );

    private final Setting<Integer> timingJitter = sgTiming.add(new IntSetting.Builder()
        .name("timing-jitter")
        .description("Randomly varies every delay by up to this percent, so no two cycles have the same rhythm.")
        .defaultValue(25)
        .min(0)
        .max(60)
        .sliderMax(50)
        .build()
    );

    private final Setting<Integer> grabDelayMin = sgTiming.add(new IntSetting.Builder()
        .name("grab-delay-min")
        .description("Fastest gap between clicks while emptying the chest. Each click picks a fresh number between min and max.")
        .defaultValue(1)
        .min(1)
        .max(40)
        .sliderMax(20)
        .build()
    );

    private final Setting<Integer> grabDelayMax = sgTiming.add(new IntSetting.Builder()
        .name("grab-delay-max")
        .description("Slowest gap between clicks while emptying the chest. Keep it above the min or the rhythm is dead flat.")
        .defaultValue(4)
        .min(1)
        .max(40)
        .sliderMax(20)
        .build()
    );

    private final Setting<Integer> clickDelay = sgTiming.add(new IntSetting.Builder()
        .name("click-delay")
        .description("Ticks between each slot click while loading the sell chest.")
        .defaultValue(3)
        .min(1)
        .max(40)
        .sliderMax(20)
        .build()
    );

    private final Setting<Integer> screenDelay = sgTiming.add(new IntSetting.Builder()
        .name("screen-delay")
        .description("Ticks to wait after a menu opens or closes.")
        .defaultValue(6)
        .min(1)
        .max(60)
        .sliderMax(30)
        .build()
    );

    private final Setting<Integer> confirmDelay = sgTiming.add(new IntSetting.Builder()
        .name("confirm-delay")
        .description("Ticks to wait after the chest is filled before clicking the confirm pane.")
        .defaultValue(8)
        .min(1)
        .max(100)
        .sliderMax(30)
        .build()
    );

    private final Setting<Integer> cycleGap = sgTiming.add(new IntSetting.Builder()
        .name("cycle-gap")
        .description("Ticks between one sale finishing and the next fill starting.")
        .defaultValue(40)
        .min(5)
        .max(400)
        .sliderMax(200)
        .build()
    );

    private final Setting<Integer> idleBackoff = sgTiming.add(new IntSetting.Builder()
        .name("idle-backoff")
        .description("Ticks to wait before retrying when a cycle did no work, so an empty chest cannot turn into command spam.")
        .defaultValue(400)
        .min(40)
        .max(6000)
        .sliderMax(1200)
        .build()
    );

    private enum State {
        IDLE,
        CHEST_OPEN, CHEST_WAIT, GRAB, CHEST_CLOSE,
        SELL_OPEN, SELL_WAIT, FILL, CONFIRM, SELL_CLOSE,
        COOLDOWN
    }

    private final Random random = new Random();

    private State state = State.IDLE;
    private int delayCounter = 0;
    private int waited = 0;
    private int grabbed = 0;
    private int filled = 0;
    private int sold = 0;
    private int stalled = 0;
    private int lastLeftover = Integer.MAX_VALUE;
    private BlockPos chestPos = null;

    public SlabSeller() {
        super(GlazedAddon.CATEGORY, "slab-seller", "Fills up on slabs from a chest and sells them through /sell, over and over.");
    }

    @Override
    public void onActivate() {
        resetCycle();
        state = State.IDLE;
        delayCounter = 0;
        sold = 0;
    }

    @Override
    public void onDeactivate() {
        closeAnyMenu();
        state = State.IDLE;
    }

    private void resetCycle() {
        grabbed = 0;
        filled = 0;
        waited = 0;
        stalled = 0;
        lastLeftover = Integer.MAX_VALUE;
        chestPos = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null || mc.getConnection() == null) return;

        if (delayCounter > 0) {
            delayCounter--;
            return;
        }

        switch (state) {
            case IDLE -> tickIdle();
            case CHEST_OPEN -> tickChestOpen();
            case CHEST_WAIT -> tickChestWait();
            case GRAB -> tickGrab();
            case CHEST_CLOSE -> tickChestClose();
            case SELL_OPEN -> tickSellOpen();
            case SELL_WAIT -> tickSellWait();
            case FILL -> tickFill();
            case CONFIRM -> tickConfirm();
            case SELL_CLOSE -> tickSellClose();
            case COOLDOWN -> {
                resetCycle();
                state = State.IDLE;
            }
        }
    }

    private void tickIdle() {
        BlockPos target = lookedAtChest();

        if (requireChestLook.get() && target == null) {
            delayCounter = jitter(12, 4);
            return;
        }

        chestPos = target;
        grabbed = 0;
        filled = 0;
        waited = 0;
        state = State.CHEST_OPEN;
    }

    private BlockPos lookedAtChest() {
        if (!(mc.hitResult instanceof BlockHitResult hit)) return null;
        if (hit.getType() != HitResult.Type.BLOCK) return null;

        Block block = mc.level.getBlockState(hit.getBlockPos()).getBlock();

        if (block instanceof ChestBlock) return hit.getBlockPos();
        if (allowBarrels.get() && block instanceof BarrelBlock) return hit.getBlockPos();

        return null;
    }

    private void tickChestOpen() {
        BlockPos target = lookedAtChest();

        if (target == null) {
            if (requireChestLook.get()) {
                if (notifications.get()) warning("Not looking at a chest any more, backing off.");
                endCycleBackoff();
                return;
            }
            target = chestPos;
        }

        if (target == null || !(mc.hitResult instanceof BlockHitResult hit)) {
            endCycleBackoff();
            return;
        }

        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        mc.player.swing(InteractionHand.MAIN_HAND);

        waited = 0;
        delayCounter = jitter(screenDelay.get(), 1);
        state = State.CHEST_WAIT;
    }

    private void tickChestWait() {
        if (openChest() != null) {
            stalled = 0;
            state = State.GRAB;
            return;
        }

        if (++waited >= menuTimeout.get()) {
            if (notifications.get()) warning("Chest never opened, backing off.");
            endCycleBackoff();
        }
    }

    private ChestMenu openChest() {
        if (mc.player.containerMenu == mc.player.inventoryMenu) return null;
        return mc.player.containerMenu instanceof ChestMenu menu ? menu : null;
    }

    private void tickGrab() {
        ChestMenu menu = openChest();

        if (menu == null) {
            endCycleBackoff();
            return;
        }

        if (firstEmptyPlayerSlot(menu) < 0) {
            state = State.CHEST_CLOSE;
            return;
        }

        int source = findSlab(menu, 0, GlazedSell.containerSlots(menu));

        if (source < 0) {
            if (notifications.get() && grabbed == 0) info("No slabs in the chest.");
            state = State.CHEST_CLOSE;
            return;
        }

        ItemStack before = menu.getSlot(source).getItem().copy();
        mc.gameMode.handleContainerInput(menu.containerId, source, 0, ContainerInput.QUICK_MOVE, mc.player);

        if (ItemStack.matches(before, menu.getSlot(source).getItem())) {
            if (++stalled >= 4) {
                if (notifications.get()) warning("Slabs are not moving out of the chest, stopping the grab.");
                state = State.CHEST_CLOSE;
                return;
            }
        } else {
            stalled = 0;
            grabbed++;
        }

        delayCounter = grabDelay();
    }

    private void tickChestClose() {
        closeAnyMenu();
        delayCounter = jitter(screenDelay.get(), 1);

        if (countInInventory() <= 0) {
            endCycleBackoff();
            return;
        }

        if (notifications.get()) info("Took %d stack(s) of slabs, selling.", grabbed);
        state = State.SELL_OPEN;
    }

    private void tickSellOpen() {
        GlazedSell.openSell();
        filled = 0;
        waited = 0;
        stalled = 0;
        delayCounter = jitter(screenDelay.get(), 1);
        state = State.SELL_WAIT;
    }

    private void tickSellWait() {
        if (GlazedSell.container() != null) {
            stalled = 0;
            state = State.FILL;
            return;
        }

        if (++waited >= menuTimeout.get()) {
            if (notifications.get()) warning("Sell menu never opened, backing off.");
            endCycleBackoff();
        }
    }

    private void tickFill() {
        ChestMenu menu = GlazedSell.container();

        if (menu == null) {
            if (notifications.get()) warning("Sell menu closed early, backing off.");
            endCycleBackoff();
            return;
        }

        if (GlazedSell.firstEmptyUsableSlot(menu) < 0) {
            delayCounter = jitter(confirmDelay.get(), 1);
            state = State.CONFIRM;
            return;
        }

        int source = findSlab(menu, GlazedSell.containerSlots(menu), menu.slots.size());

        if (source < 0) {
            if (filled == 0) {
                if (notifications.get()) warning("Nothing went into the sell chest, backing off.");
                endCycleBackoff();
                return;
            }

            delayCounter = jitter(confirmDelay.get(), 1);
            state = State.CONFIRM;
            return;
        }

        ItemStack before = menu.getSlot(source).getItem().copy();
        mc.gameMode.handleContainerInput(menu.containerId, source, 0, ContainerInput.QUICK_MOVE, mc.player);

        if (ItemStack.matches(before, menu.getSlot(source).getItem())) {
            if (++stalled >= 4) {
                if (notifications.get()) warning("Slabs are not going into the sell chest.");

                if (filled == 0) {
                    endCycleBackoff();
                    return;
                }

                delayCounter = jitter(confirmDelay.get(), 1);
                state = State.CONFIRM;
                return;
            }
        } else {
            stalled = 0;
            filled++;
        }

        delayCounter = jitter(clickDelay.get(), 1);
    }

    private void tickConfirm() {
        ChestMenu menu = GlazedSell.container();

        if (menu == null) {
            finishLoad();
            return;
        }

        if (clickConfirm.get()) {
            int button = findButtonRowConfirm(menu);

            if (button >= 0) {
                mc.gameMode.handleContainerInput(menu.containerId, button, 0, ContainerInput.PICKUP, mc.player);
            } else if (notifications.get()) {
                info("No confirm button found, closing to sell instead.");
            }
        }

        delayCounter = jitter(confirmDelay.get(), 1);
        state = State.SELL_CLOSE;
    }

    private void tickSellClose() {
        GlazedSell.close();
        if (mc.screen != null) mc.setScreen(null);
        finishLoad();
    }

    private void finishLoad() {
        int left = countInInventory();
        sold += filled;

        if (left > 0 && left < lastLeftover) {
            lastLeftover = left;
            if (notifications.get()) info("Sold %d stack(s), %d slot(s) still to go.", filled, left);
            delayCounter = jitter(screenDelay.get(), 1);
            state = State.SELL_OPEN;
            return;
        }

        if (left > 0 && notifications.get()) {
            warning("%d slot(s) of slabs would not sell, going back to the chest.", left);
        } else if (notifications.get()) {
            info("Sold %d stack(s), %d this session.", filled, sold);
        }

        endCycle();
    }

    private int findSlab(ChestMenu menu, int from, int to) {
        for (int slot = from; slot < to && slot < menu.slots.size(); slot++) {
            if (isSlab(menu.getSlot(slot).getItem())) return slot;
        }
        return -1;
    }

    private boolean isSlab(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (!anySlab.get()) return stack.is(item.get());

        return stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof SlabBlock;
    }

    private int findButtonRowConfirm(ChestMenu menu) {
        int total = Math.min(GlazedSell.containerSlots(menu), menu.slots.size());

        for (int slot = total - 1; slot >= GlazedSell.usableSlots(menu); slot--) {
            if (GlazedSell.isConfirmButton(menu.getSlot(slot).getItem())) return slot;
        }

        return -1;
    }

    private int firstEmptyPlayerSlot(ChestMenu menu) {
        for (int slot = GlazedSell.containerSlots(menu); slot < menu.slots.size(); slot++) {
            if (menu.getSlot(slot).getItem().isEmpty()) return slot;
        }
        return -1;
    }

    private int countInInventory() {
        int total = 0;
        int size = mc.player.getInventory().getContainerSize();

        for (int slot = 0; slot < 36 && slot < size; slot++) {
            if (isSlab(mc.player.getInventory().getItem(slot))) total++;
        }

        return total;
    }

    private int grabDelay() {
        int min = Math.max(1, grabDelayMin.get());
        int max = Math.max(min, grabDelayMax.get());

        return min + random.nextInt(max - min + 1);
    }

    private void endCycle() {
        closeAnyMenu();
        delayCounter = jitter(cycleGap.get(), 5);
        state = State.COOLDOWN;
    }

    private void endCycleBackoff() {
        closeAnyMenu();
        delayCounter = jitter(idleBackoff.get(), 40);
        state = State.COOLDOWN;
    }

    private void closeAnyMenu() {
        if (mc.player != null && mc.player.containerMenu != mc.player.inventoryMenu) mc.player.closeContainer();
        if (mc.screen != null) mc.setScreen(null);
    }

    private int jitter(int ticks, int floor) {
        int pct = timingJitter.get();
        if (pct <= 0) return Math.max(floor, ticks);

        double factor = 1.0 + (random.nextDouble() * 2.0 - 1.0) * (pct / 100.0);
        return Math.max(floor, (int) Math.round(ticks * factor));
    }
}
