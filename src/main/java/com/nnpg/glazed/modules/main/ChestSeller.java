package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import com.nnpg.glazed.settings.RandomBetweenIntSetting;
import com.nnpg.glazed.utils.GlazedSell;
import com.nnpg.glazed.utils.RandomBetweenInt;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Random;

public class ChestSeller extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTiming = settings.createGroup("Timing");

    private final Setting<Boolean> notifications = sgGeneral.add(new BoolSetting.Builder()
        .name("notifications")
        .description("Show chat feedback.")
        .defaultValue(true)
        .build()
    );

    private final Setting<RandomBetweenInt> takeDelay = sgTiming.add(new RandomBetweenIntSetting.Builder()
        .name("take-delay-range")
        .description("Random tick range between stacks taken from the source chest.")
        .defaultRange(1, 4)
        .range(1, 100)
        .sliderRange(1, 40)
        .build()
    );

    private final Setting<RandomBetweenInt> depositDelay = sgTiming.add(new RandomBetweenIntSetting.Builder()
        .name("deposit-delay-range")
        .description("Random tick range between stacks placed into the sell window.")
        .defaultRange(2, 5)
        .range(1, 100)
        .sliderRange(1, 40)
        .build()
    );

    private final Setting<RandomBetweenInt> screenDelay = sgTiming.add(new RandomBetweenIntSetting.Builder()
        .name("screen-delay-range")
        .description("Random tick range after opening or closing a window.")
        .defaultRange(5, 10)
        .range(1, 200)
        .sliderRange(1, 60)
        .build()
    );

    private final Setting<RandomBetweenInt> confirmDelay = sgTiming.add(new RandomBetweenIntSetting.Builder()
        .name("confirm-delay-range")
        .description("Random tick range before and after pressing the green sell button.")
        .defaultRange(8, 15)
        .range(1, 200)
        .sliderRange(1, 80)
        .build()
    );

    private final Setting<Integer> menuTimeout = sgTiming.add(new IntSetting.Builder()
        .name("menu-timeout")
        .description("Ticks to wait for a chest or sell window before backing off.")
        .defaultValue(80)
        .min(20)
        .max(400)
        .sliderMax(200)
        .build()
    );

    private final Setting<RandomBetweenInt> cycleDelay = sgTiming.add(new RandomBetweenIntSetting.Builder()
        .name("cycle-delay-range")
        .description("Random tick range between a completed sale and reopening the source chest.")
        .defaultRange(16, 35)
        .range(1, 1000)
        .sliderRange(1, 200)
        .build()
    );

    private final Setting<RandomBetweenInt> idleBackoff = sgTiming.add(new RandomBetweenIntSetting.Builder()
        .name("idle-backoff-range")
        .description("Random tick range before retrying when the chest is empty or an action fails.")
        .defaultRange(180, 320)
        .range(20, 6000)
        .sliderRange(20, 800)
        .build()
    );

    private final Setting<Integer> hesitationChance = sgTiming.add(new IntSetting.Builder()
        .name("hesitation-chance")
        .description("Chance after an item click to add a longer random pause.")
        .defaultValue(8)
        .min(0)
        .max(50)
        .sliderMax(30)
        .build()
    );

    private final Setting<RandomBetweenInt> hesitationDelay = sgTiming.add(new RandomBetweenIntSetting.Builder()
        .name("hesitation-delay-range")
        .description("Extra tick range used when a hesitation occurs.")
        .defaultRange(8, 30)
        .range(1, 400)
        .sliderRange(1, 100)
        .build()
    );

    private enum State {
        IDLE,
        SOURCE_OPEN,
        SOURCE_WAIT,
        TAKE,
        SOURCE_CLOSE,
        SELL_OPEN,
        SELL_WAIT,
        DEPOSIT,
        CONFIRM,
        SELL_CLOSE,
        COOLDOWN
    }

    private final Random random = new Random();

    private State state = State.IDLE;
    private BlockPos sourcePos;
    private int delayCounter;
    private int waited;
    private int sourceCursor;
    private int depositCursor;
    private int failedMoves;
    private int taken;
    private int deposited;
    private int sold;
    private int inventoryBeforeSale;

    public ChestSeller() {
        super(GlazedAddon.CATEGORY, "chest-seller", "Takes everything from the chest you are looking at and repeatedly sells it through /sell.");
    }

    @Override
    public void onActivate() {
        resetCycle();
        sold = 0;
        delayCounter = 0;
        state = State.IDLE;
    }

    @Override
    public void onDeactivate() {
        closeMenu();
        state = State.IDLE;
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
            case SOURCE_OPEN -> tickSourceOpen();
            case SOURCE_WAIT -> tickSourceWait();
            case TAKE -> tickTake();
            case SOURCE_CLOSE -> tickSourceClose();
            case SELL_OPEN -> tickSellOpen();
            case SELL_WAIT -> tickSellWait();
            case DEPOSIT -> tickDeposit();
            case CONFIRM -> tickConfirm();
            case SELL_CLOSE -> tickSellClose();
            case COOLDOWN -> {
                resetCycle();
                state = State.IDLE;
            }
        }
    }

    private void tickIdle() {
        BlockHitResult hit = lookedAtChest();
        if (hit == null) {
            delayCounter = randomTicks(6, 14);
            return;
        }

        sourcePos = hit.getBlockPos();
        state = State.SOURCE_OPEN;
    }

    private void tickSourceOpen() {
        BlockHitResult hit = lookedAtChest();

        if (hit == null || !hit.getBlockPos().equals(sourcePos)) {
            if (notifications.get()) warning("Look at the source chest to continue.");
            backoff();
            return;
        }

        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        mc.player.swing(InteractionHand.MAIN_HAND);
        waited = 0;
        delayCounter = randomDelay(screenDelay);
        state = State.SOURCE_WAIT;
    }

    private void tickSourceWait() {
        if (openChest() != null) {
            sourceCursor = 0;
            failedMoves = 0;
            state = State.TAKE;
            return;
        }

        if (++waited >= menuTimeout.get()) {
            if (notifications.get()) warning("The source chest did not open.");
            backoff();
        }
    }

    private void tickTake() {
        ChestMenu menu = openChest();
        if (menu == null) {
            if (notifications.get()) warning("The source chest closed early.");
            backoff();
            return;
        }

        int end = Math.min(GlazedSell.containerSlots(menu), menu.slots.size());
        int nonEmpty = countNonEmpty(menu, 0, end);

        if (nonEmpty == 0 || failedMoves >= nonEmpty) {
            state = State.SOURCE_CLOSE;
            return;
        }

        int slot = findNextNonEmpty(menu, 0, end, sourceCursor);
        if (slot < 0) {
            state = State.SOURCE_CLOSE;
            return;
        }

        ItemStack before = menu.getSlot(slot).getItem().copy();
        mc.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.QUICK_MOVE, mc.player);

        if (ItemStack.matches(before, menu.getSlot(slot).getItem())) {
            failedMoves++;
            sourceCursor = nextSlot(slot, 0, end);
        } else {
            failedMoves = 0;
            sourceCursor = slot;
            taken++;
        }

        delayCounter = humanClickDelay(takeDelay);
    }

    private void tickSourceClose() {
        closeMenu();

        if (taken == 0) {
            if (notifications.get()) info("The source chest is empty or the inventory is full.");
            backoff();
            return;
        }

        inventoryBeforeSale = countPlayerStacks();
        if (notifications.get()) info("Took %d stack(s); opening /sell.", taken);
        delayCounter = randomDelay(screenDelay);
        state = State.SELL_OPEN;
    }

    private void tickSellOpen() {
        GlazedSell.openSell();
        waited = 0;
        depositCursor = 0;
        failedMoves = 0;
        delayCounter = randomDelay(screenDelay);
        state = State.SELL_WAIT;
    }

    private void tickSellWait() {
        if (GlazedSell.container() != null) {
            state = State.DEPOSIT;
            return;
        }

        if (++waited >= menuTimeout.get()) {
            if (notifications.get()) warning("The /sell window did not open.");
            backoff();
        }
    }

    private void tickDeposit() {
        ChestMenu menu = GlazedSell.container();
        if (menu == null) {
            if (notifications.get()) warning("The /sell window closed early.");
            backoff();
            return;
        }

        if (GlazedSell.firstEmptyUsableSlot(menu) < 0) {
            beginConfirm();
            return;
        }

        int from = GlazedSell.containerSlots(menu);
        int to = menu.slots.size();
        int nonEmpty = countNonEmpty(menu, from, to);

        if (nonEmpty == 0 || failedMoves >= nonEmpty) {
            if (deposited == 0) {
                if (notifications.get()) warning("No inventory items could be put into /sell.");
                backoff();
            } else {
                beginConfirm();
            }
            return;
        }

        int start = Math.max(from, Math.min(depositCursor, to - 1));
        int slot = findNextNonEmpty(menu, from, to, start);
        if (slot < 0) {
            beginConfirm();
            return;
        }

        ItemStack before = menu.getSlot(slot).getItem().copy();
        mc.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.QUICK_MOVE, mc.player);

        if (ItemStack.matches(before, menu.getSlot(slot).getItem())) {
            failedMoves++;
            depositCursor = nextSlot(slot, from, to);
        } else {
            failedMoves = 0;
            depositCursor = slot;
            deposited++;
        }

        delayCounter = humanClickDelay(depositDelay);
    }

    private void beginConfirm() {
        waited = 0;
        delayCounter = randomDelay(confirmDelay);
        state = State.CONFIRM;
    }

    private void tickConfirm() {
        ChestMenu menu = GlazedSell.container();
        if (menu == null) {
            if (notifications.get()) warning("The /sell window vanished before confirmation.");
            backoff();
            return;
        }

        int button = findGreenButton(menu);
        if (button >= 0) {
            mc.gameMode.handleContainerInput(menu.containerId, button, 0, ContainerInput.PICKUP, mc.player);
            delayCounter = randomDelay(confirmDelay);
            state = State.SELL_CLOSE;
            return;
        }

        if (++waited >= menuTimeout.get()) {
            if (notifications.get()) warning("No green sell button was found; nothing was confirmed.");
            backoff();
        }
    }

    private void tickSellClose() {
        closeMenu();
        sold += deposited;

        int remaining = countPlayerStacks();
        if (remaining > 0 && remaining < inventoryBeforeSale) {
            inventoryBeforeSale = remaining;
            deposited = 0;
            delayCounter = randomDelay(screenDelay);
            state = State.SELL_OPEN;
            return;
        }

        if (remaining >= inventoryBeforeSale && remaining > 0) {
            if (notifications.get()) warning("Some inventory items did not sell; retrying the chest after a backoff.");
            backoff();
            return;
        }

        if (notifications.get()) info("Sale complete: %d stack move(s), %d this session.", deposited, sold);
        delayCounter = randomDelay(cycleDelay);
        state = State.COOLDOWN;
    }

    private BlockHitResult lookedAtChest() {
        if (!(mc.hitResult instanceof BlockHitResult hit)) return null;
        if (hit.getType() != HitResult.Type.BLOCK) return null;
        return mc.level.getBlockState(hit.getBlockPos()).getBlock() instanceof ChestBlock ? hit : null;
    }

    private ChestMenu openChest() {
        if (mc.player.containerMenu == mc.player.inventoryMenu) return null;
        return mc.player.containerMenu instanceof ChestMenu menu ? menu : null;
    }

    private int findGreenButton(ChestMenu menu) {
        int end = Math.min(GlazedSell.containerSlots(menu), menu.slots.size());
        int start = Math.min(GlazedSell.usableSlots(menu), end);

        for (int slot = end - 1; slot >= start; slot--) {
            if (GlazedSell.isConfirmButton(menu.getSlot(slot).getItem())) return slot;
        }

        return -1;
    }

    private int findNextNonEmpty(ChestMenu menu, int from, int to, int start) {
        if (from >= to) return -1;

        int boundedStart = Math.max(from, Math.min(start, to - 1));
        for (int slot = boundedStart; slot < to; slot++) {
            if (!menu.getSlot(slot).getItem().isEmpty()) return slot;
        }
        for (int slot = from; slot < boundedStart; slot++) {
            if (!menu.getSlot(slot).getItem().isEmpty()) return slot;
        }
        return -1;
    }

    private int countNonEmpty(ChestMenu menu, int from, int to) {
        int count = 0;
        for (int slot = from; slot < to; slot++) {
            if (!menu.getSlot(slot).getItem().isEmpty()) count++;
        }
        return count;
    }

    private int nextSlot(int slot, int from, int to) {
        return slot + 1 < to ? slot + 1 : from;
    }

    private int countPlayerStacks() {
        int count = 0;
        int size = Math.min(36, mc.player.getInventory().getContainerSize());
        for (int slot = 0; slot < size; slot++) {
            if (!mc.player.getInventory().getItem(slot).isEmpty()) count++;
        }
        return count;
    }

    private void resetCycle() {
        sourcePos = null;
        waited = 0;
        sourceCursor = 0;
        depositCursor = 0;
        failedMoves = 0;
        taken = 0;
        deposited = 0;
        inventoryBeforeSale = 0;
    }

    private void backoff() {
        closeMenu();
        delayCounter = randomDelay(idleBackoff);
        state = State.COOLDOWN;
    }

    private void closeMenu() {
        if (mc.player != null && mc.player.containerMenu != mc.player.inventoryMenu) mc.player.closeContainer();
        if (mc.screen != null) mc.setScreen(null);
    }

    private int humanClickDelay(Setting<RandomBetweenInt> range) {
        int ticks = randomDelay(range);
        if (random.nextInt(100) < hesitationChance.get()) ticks += randomDelay(hesitationDelay);
        return ticks;
    }

    private int randomDelay(Setting<RandomBetweenInt> range) {
        return Math.max(1, range.get().getRandom());
    }

    private int randomTicks(int min, int max) {
        return min + random.nextInt(max - min + 1);
    }
}
