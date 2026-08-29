package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import com.nnpg.glazed.utils.GlazedShop;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

public class SlabCrafter extends Module {
    private static final int RESULT_SLOT = 0;
    private static final int GRID_FIRST = 1;
    private static final int GRID_LAST = 9;
    private static final int ROW_LAST = 3;
    private static final int INV_FIRST = 10;
    private static final int MENU_SLOTS = 46;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgOrders = settings.createGroup("Orders menu");
    private final SettingGroup sgTiming = settings.createGroup("Timing");

    private final Setting<Boolean> fetchLogs = sgGeneral.add(new BoolSetting.Builder()
        .name("fetch-logs")
        .description("Walk the /orders menus for logs. Off crafts what is already in your inventory and nothing else.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> logStacks = sgGeneral.add(new IntSetting.Builder()
        .name("log-stacks")
        .description("Stacks of logs to pull per trip. One stack turns into about eight stacks of slabs, so this is capped by the free space you actually have.")
        .defaultValue(4)
        .min(1)
        .max(16)
        .sliderMax(8)
        .build()
    );

    private final Setting<Boolean> craftSlabs = sgGeneral.add(new BoolSetting.Builder()
        .name("craft-slabs")
        .description("Carry on from planks to slabs. Off stops at planks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> requireTableLook = sgGeneral.add(new BoolSetting.Builder()
        .name("require-table-look")
        .description("Only start a cycle while your crosshair is on a crafting table, which is how you pause it. Off takes the nearest table instead.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> tableRange = sgGeneral.add(new IntSetting.Builder()
        .name("table-range")
        .description("How far to look for a crafting table when the crosshair is not on one.")
        .defaultValue(4)
        .min(1)
        .max(6)
        .sliderMax(6)
        .build()
    );

    private final Setting<Boolean> dropSlabs = sgGeneral.add(new BoolSetting.Builder()
        .name("drop-slabs")
        .description("Throw the slabs on the floor once the crafting is done, the same as holding Q over them.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> notifications = sgGeneral.add(new BoolSetting.Builder()
        .name("notifications")
        .description("Show chat notifications.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> ordersCommand = sgOrders.add(new StringSetting.Builder()
        .name("orders-command")
        .description("Command that opens the orders menu.")
        .defaultValue("/orders")
        .build()
    );

    private final Setting<Integer> ordersSlotFromEnd = sgOrders.add(new IntSetting.Builder()
        .name("orders-slot-from-end")
        .description("Which chest to click in the orders menu, counted back from the last slot. 3 is the third from the end.")
        .defaultValue(3)
        .min(1)
        .max(54)
        .sliderMax(9)
        .build()
    );

    private final Setting<Integer> firstSlot = sgOrders.add(new IntSetting.Builder()
        .name("first-slot")
        .description("Slot clicked in the menu after that. 0 is the top left corner.")
        .defaultValue(0)
        .min(0)
        .max(53)
        .sliderMax(53)
        .build()
    );

    private final Setting<Integer> storageSlot = sgOrders.add(new IntSetting.Builder()
        .name("storage-slot")
        .description("Slot of the chest in the menu after that. If it is not a chest the module looks for the one nearest the middle instead.")
        .defaultValue(13)
        .min(0)
        .max(53)
        .sliderMax(53)
        .build()
    );

    private final Setting<TakeMode> takeMode = sgOrders.add(new EnumSetting.Builder<TakeMode>()
        .name("take-mode")
        .description("How logs come out of the order. Auto shift clicks and switches to a plain click if the server ignores that.")
        .defaultValue(TakeMode.Auto)
        .build()
    );

    private final Setting<Integer> menuTimeout = sgOrders.add(new IntSetting.Builder()
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

    private final Setting<Integer> menuDelay = sgTiming.add(new IntSetting.Builder()
        .name("menu-delay")
        .description("Ticks between clicks while walking the orders menus.")
        .defaultValue(8)
        .min(1)
        .max(60)
        .sliderMax(30)
        .build()
    );

    private final Setting<Integer> takeDelayMin = sgTiming.add(new IntSetting.Builder()
        .name("take-delay-min")
        .description("Fastest gap between clicks while pulling logs out. Each click picks a fresh number between min and max.")
        .defaultValue(4)
        .min(1)
        .max(40)
        .sliderMax(20)
        .build()
    );

    private final Setting<Integer> takeDelayMax = sgTiming.add(new IntSetting.Builder()
        .name("take-delay-max")
        .description("Slowest gap between clicks while pulling logs out. Keep it above the min or the rhythm is dead flat.")
        .defaultValue(9)
        .min(1)
        .max(40)
        .sliderMax(20)
        .build()
    );

    private final Setting<Integer> dropDelayMin = sgTiming.add(new IntSetting.Builder()
        .name("drop-delay-min")
        .description("Fastest gap between dropped stacks. Each one picks a fresh number between min and max.")
        .defaultValue(2)
        .min(1)
        .max(40)
        .sliderMax(20)
        .build()
    );

    private final Setting<Integer> dropDelayMax = sgTiming.add(new IntSetting.Builder()
        .name("drop-delay-max")
        .description("Slowest gap between dropped stacks. Keep it above the min or the rhythm is dead flat.")
        .defaultValue(6)
        .min(1)
        .max(40)
        .sliderMax(20)
        .build()
    );

    private final Setting<Integer> craftDelay = sgTiming.add(new IntSetting.Builder()
        .name("craft-delay")
        .description("Ticks between clicks at the crafting table. The result only appears once the server has checked the recipe, so this cannot be much lower.")
        .defaultValue(6)
        .min(2)
        .max(60)
        .sliderMax(30)
        .build()
    );

    private final Setting<Integer> screenDelay = sgTiming.add(new IntSetting.Builder()
        .name("screen-delay")
        .description("Ticks to wait after a menu opens or closes.")
        .defaultValue(8)
        .min(1)
        .max(60)
        .sliderMax(30)
        .build()
    );

    private final Setting<Integer> craftWatchdog = sgTiming.add(new IntSetting.Builder()
        .name("craft-watchdog")
        .description("Ticks the crafting half is allowed to run before it gives up. A full inventory or a server that refuses a click cannot turn into an endless loop.")
        .defaultValue(3000)
        .min(200)
        .max(20000)
        .sliderMax(6000)
        .build()
    );

    private final Setting<Integer> cycleGap = sgTiming.add(new IntSetting.Builder()
        .name("cycle-gap")
        .description("Ticks between one batch finishing and the next trip starting.")
        .defaultValue(60)
        .min(5)
        .max(600)
        .sliderMax(300)
        .build()
    );

    private final Setting<Integer> idleBackoff = sgTiming.add(new IntSetting.Builder()
        .name("idle-backoff")
        .description("Ticks to wait before retrying when a cycle did no work, so an empty order or a full inventory cannot turn into command spam.")
        .defaultValue(600)
        .min(40)
        .max(6000)
        .sliderMax(1200)
        .build()
    );

    public enum TakeMode { Auto, ShiftClick, Click }

    private enum State {
        IDLE,
        ORDERS_SEND, ORDERS_WAIT, ORDERS_CLICK,
        CATEGORY_WAIT, CATEGORY_CLICK,
        STORAGE_WAIT, STORAGE_CLICK,
        LOGS_WAIT, TAKE, TAKE_SETTLE,
        ORDERS_CLOSE,
        TABLE_OPEN, TABLE_WAIT,
        PLANKS_PLACE, PLANKS_WAIT, PLANKS_CRAFT,
        SLABS_FILL, SLABS_WAIT, SLABS_CRAFT,
        DROP,
        TABLE_CLOSE,
        COOLDOWN
    }

    private final Random random = new Random();

    private State state = State.IDLE;
    private int delayCounter = 0;
    private int waited = 0;
    private int stalled = 0;

    private int lastMenuId = Integer.MIN_VALUE;
    private long lastSignature = 0;

    private int targetStacks = 0;
    private int grabbed = 0;
    private int logsBefore = 0;
    private int takeSource = -1;
    private boolean plainClick = false;

    private int craftSnapshot = -1;
    private int outputSnapshot = -1;
    private Item plankType = null;
    private final java.util.Set<Item> skippedPlanks = new java.util.HashSet<>();
    private int carrySource = -1;
    private int carryTries = 0;
    private int tidySlot = -1;
    private int tidyCount = -1;
    private int tidyTries = 0;
    private int craftTicks = 0;
    private int idleNags = 0;

    private int madePlanks = 0;
    private int madeSlabs = 0;
    private int dropped = 0;
    private int dropSlot = -1;
    private int dropCount = -1;
    private int dropTries = 0;

    private BlockPos tablePos = null;

    public SlabCrafter() {
        super(GlazedAddon.CATEGORY, "slab-crafter", "Pulls logs from /orders and crafts them into slabs on the crafting table you are looking at.");
    }

    @Override
    public void onActivate() {
        resetCycle();
        state = State.IDLE;
        delayCounter = 0;
        madePlanks = 0;
        madeSlabs = 0;
        idleNags = 0;
    }

    @Override
    public void onDeactivate() {
        closeAnyMenu();
        state = State.IDLE;
    }

    private void resetCycle() {
        waited = 0;
        stalled = 0;
        grabbed = 0;
        targetStacks = 0;
        logsBefore = 0;
        takeSource = -1;
        plainClick = false;
        craftSnapshot = -1;
        outputSnapshot = -1;
        plankType = null;
        skippedPlanks.clear();
        carrySource = -1;
        carryTries = 0;
        tidySlot = -1;
        tidyCount = -1;
        tidyTries = 0;
        craftTicks = 0;
        dropped = 0;
        dropSlot = -1;
        dropCount = -1;
        dropTries = 0;
        tablePos = null;
        lastMenuId = Integer.MIN_VALUE;
        lastSignature = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null || mc.getConnection() == null) return;

        if (delayCounter > 0) {
            delayCounter--;
            return;
        }

        if (isCrafting() && ++craftTicks > craftWatchdog.get()) {
            if (notifications.get()) warning("Crafting is taking far too long, backing off.");
            endCycleBackoff();
            return;
        }

        switch (state) {
            case IDLE -> tickIdle();
            case ORDERS_SEND -> tickOrdersSend();
            case ORDERS_WAIT -> tickOrdersWait();
            case ORDERS_CLICK -> tickOrdersClick();
            case CATEGORY_WAIT -> tickMenuWait(State.CATEGORY_CLICK, "category menu");
            case CATEGORY_CLICK -> tickCategoryClick();
            case STORAGE_WAIT -> tickMenuWait(State.STORAGE_CLICK, "storage menu");
            case STORAGE_CLICK -> tickStorageClick();
            case LOGS_WAIT -> tickMenuWait(State.TAKE, "log menu");
            case TAKE -> tickTake();
            case TAKE_SETTLE -> tickTakeSettle();
            case ORDERS_CLOSE -> tickOrdersClose();
            case TABLE_OPEN -> tickTableOpen();
            case TABLE_WAIT -> tickTableWait();
            case PLANKS_PLACE -> tickPlanksPlace();
            case PLANKS_WAIT -> tickPlanksWait();
            case PLANKS_CRAFT -> tickPlanksCraft();
            case SLABS_FILL -> tickSlabsFill();
            case SLABS_WAIT -> tickSlabsWait();
            case SLABS_CRAFT -> tickSlabsCraft();
            case DROP -> tickDrop();
            case TABLE_CLOSE -> tickTableClose();
            case COOLDOWN -> {
                resetCycle();
                state = State.IDLE;
            }
        }
    }

    private void tickIdle() {
        BlockPos target = resolveTable(true);

        if (target == null) {
            announceWaiting();
            delayCounter = jitter(12, 4);
            return;
        }

        tablePos = target;
        idleNags = 0;

        int free = freeSlots();

        if (free < 6) {
            if (notifications.get()) warning("Not enough room to craft, waiting for space.");
            endCycleBackoff();
            return;
        }

        targetStacks = Math.max(1, Math.min(logStacks.get(), free / 9));

        if (!fetchLogs.get() || countLogs() >= targetStacks * 64) {
            state = State.TABLE_OPEN;
            return;
        }

        state = State.ORDERS_SEND;
    }

    private BlockPos lookedAtTable() {
        if (!(mc.hitResult instanceof BlockHitResult hit)) return null;
        if (hit.getType() != HitResult.Type.BLOCK) return null;

        return isTable(hit.getBlockPos()) ? hit.getBlockPos() : null;
    }

    private boolean isTable(BlockPos pos) {
        Block block = mc.level.getBlockState(pos).getBlock();
        return block == Blocks.CRAFTING_TABLE || block instanceof CraftingTableBlock;
    }

    private BlockPos resolveTable(boolean starting) {
        BlockPos looked = lookedAtTable();
        if (looked != null) return looked;

        if (!starting && tablePos != null && isTable(tablePos)) return tablePos;
        if (starting && requireTableLook.get()) return null;

        return nearbyTable();
    }

    private BlockPos nearbyTable() {
        BlockPos origin = mc.player.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        int range = tableRange.get();

        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    if (!isTable(pos)) continue;

                    double distance = pos.distSqr(origin);

                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = pos;
                    }
                }
            }
        }

        return best;
    }

    private void announceWaiting() {
        if (!notifications.get()) return;
        if (idleNags++ % 8 != 0) return;

        if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            info("Waiting: your crosshair is on %s, not a crafting table.",
                mc.level.getBlockState(hit.getBlockPos()).getBlock().getName().getString());
            return;
        }

        info("Waiting: point your crosshair at a crafting table.");
    }

    private void tickOrdersSend() {
        markMenu();
        ChatUtils.sendPlayerMsg(ordersCommand.get());

        waited = 0;
        delayCounter = jitter(screenDelay.get(), 2);
        state = State.ORDERS_WAIT;
    }

    private void tickOrdersWait() {
        if (GlazedShop.openContainer() != null) {
            delayCounter = jitter(menuDelay.get(), 2);
            state = State.ORDERS_CLICK;
            return;
        }

        if (++waited >= menuTimeout.get()) {
            if (notifications.get()) warning("Orders menu never opened, backing off.");
            endCycleBackoff();
        }
    }

    private void tickOrdersClick() {
        ChestMenu menu = GlazedShop.openContainer();

        if (menu == null) {
            endCycleBackoff();
            return;
        }

        int total = containerSlots(menu);
        int wanted = total - ordersSlotFromEnd.get();
        int slot = isChestItem(itemAt(menu, wanted)) ? wanted : lastChestSlot(menu, total);

        if (slot < 0) {
            if (notifications.get()) warning("No chest in the orders menu, backing off.");
            endCycleBackoff();
            return;
        }

        clickMenu(menu, slot, State.CATEGORY_WAIT);
    }

    private void tickCategoryClick() {
        ChestMenu menu = GlazedShop.openContainer();

        if (menu == null) {
            endCycleBackoff();
            return;
        }

        int total = containerSlots(menu);
        int wanted = firstSlot.get();
        int slot = wanted < total && !itemAt(menu, wanted).isEmpty() ? wanted : firstFilledSlot(menu, total);

        if (slot < 0) {
            if (notifications.get()) warning("That menu came up empty, backing off.");
            endCycleBackoff();
            return;
        }

        clickMenu(menu, slot, State.STORAGE_WAIT);
    }

    private void tickStorageClick() {
        ChestMenu menu = GlazedShop.openContainer();

        if (menu == null) {
            endCycleBackoff();
            return;
        }

        int slot = chestNearMiddle(menu);

        if (slot < 0) {
            if (notifications.get()) warning("No chest to open in that menu, backing off.");
            endCycleBackoff();
            return;
        }

        clickMenu(menu, slot, State.LOGS_WAIT);
    }

    private void tickMenuWait(State next, String what) {
        ChestMenu menu = GlazedShop.openContainer();

        if (menu != null && isNewMenu(menu)) {
            waited = 0;
            stalled = 0;
            delayCounter = jitter(menuDelay.get(), 2);
            state = next;
            return;
        }

        if (++waited >= menuTimeout.get()) {
            if (notifications.get()) warning("The %s never opened, backing off.", what);
            endCycleBackoff();
        }
    }

    private void tickTake() {
        ChestMenu menu = GlazedShop.openContainer();

        if (menu == null) {
            if (grabbed > 0) {
                state = State.TABLE_OPEN;
                return;
            }

            if (notifications.get()) warning("Log menu closed early, backing off.");
            endCycleBackoff();
            return;
        }

        if (grabbed >= targetStacks || freeSlots() <= 2) {
            state = State.ORDERS_CLOSE;
            return;
        }

        int source = findLog(menu, 0, containerSlots(menu));

        if (source < 0) {
            if (notifications.get() && grabbed == 0) info("No logs in that order.");
            state = State.ORDERS_CLOSE;
            return;
        }

        logsBefore = countLogs();
        takeSource = source;

        boolean plain = takeMode.get() == TakeMode.Click || (takeMode.get() == TakeMode.Auto && plainClick);
        mc.gameMode.handleContainerInput(menu.containerId, source, 0,
            plain ? ContainerInput.PICKUP : ContainerInput.QUICK_MOVE, mc.player);

        delayCounter = takeDelay();
        state = State.TAKE_SETTLE;
    }

    private void tickTakeSettle() {
        ChestMenu menu = GlazedShop.openContainer();

        if (menu != null && !menu.getCarried().isEmpty() && takeSource >= 0) {
            mc.gameMode.handleContainerInput(menu.containerId, takeSource, 0, ContainerInput.PICKUP, mc.player);
            delayCounter = takeDelay();
            state = State.TAKE;
            return;
        }

        if (countLogs() > logsBefore) {
            grabbed++;
            stalled = 0;
            state = State.TAKE;
            return;
        }

        stalled++;

        if (takeMode.get() == TakeMode.Auto && !plainClick && stalled >= 2) {
            plainClick = true;
            stalled = 0;
            if (notifications.get()) info("Shift clicking did nothing, trying a plain click.");
            state = State.TAKE;
            return;
        }

        if (stalled >= 3) {
            if (notifications.get()) warning("Logs are not coming out of the order, stopping the pull.");
            state = State.ORDERS_CLOSE;
            return;
        }

        state = State.TAKE;
    }

    private void tickOrdersClose() {
        ChestMenu menu = GlazedShop.openContainer();

        if (menu != null && !menu.getCarried().isEmpty() && takeSource >= 0) {
            mc.gameMode.handleContainerInput(menu.containerId, takeSource, 0, ContainerInput.PICKUP, mc.player);
            takeSource = -1;
            delayCounter = takeDelay();
            return;
        }

        closeAnyMenu();
        delayCounter = jitter(screenDelay.get(), 2);

        if (countLogs() <= 0) {
            endCycleBackoff();
            return;
        }

        if (notifications.get()) info("Pulled %d stack(s) of logs, crafting.", grabbed);
        state = State.TABLE_OPEN;
    }

    private void tickTableOpen() {
        tablePos = resolveTable(false);

        if (tablePos == null) {
            if (notifications.get()) warning("No crafting table to work at any more, backing off.");
            endCycleBackoff();
            return;
        }

        BlockHitResult hit = mc.hitResult instanceof BlockHitResult looked
            && looked.getType() == HitResult.Type.BLOCK
            && looked.getBlockPos().equals(tablePos)
                ? looked
                : new BlockHitResult(Vec3.atCenterOf(tablePos), Direction.UP, tablePos, false);

        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        mc.player.swing(InteractionHand.MAIN_HAND);

        waited = 0;
        stalled = 0;
        craftSnapshot = -1;
        outputSnapshot = -1;
        plankType = null;
        skippedPlanks.clear();
        carryTries = 0;
        tidyTries = 0;
        craftTicks = 0;
        dropped = 0;
        dropSlot = -1;
        dropCount = -1;
        dropTries = 0;
        delayCounter = jitter(screenDelay.get(), 2);
        state = State.TABLE_WAIT;
    }

    private void tickTableWait() {
        if (craftingMenu() != null) {
            stalled = 0;
            state = State.PLANKS_PLACE;
            return;
        }

        if (++waited >= menuTimeout.get()) {
            if (notifications.get()) warning("Crafting table never opened, backing off.");
            endCycleBackoff();
        }
    }

    private boolean isCrafting() {
        return switch (state) {
            case PLANKS_PLACE, PLANKS_WAIT, PLANKS_CRAFT, SLABS_FILL, SLABS_WAIT, SLABS_CRAFT, DROP, TABLE_CLOSE -> true;
            default -> false;
        };
    }

    private CraftingMenu craftingMenu() {
        if (!(mc.player.containerMenu instanceof CraftingMenu menu)) return null;
        return menu.slots.size() >= MENU_SLOTS ? menu : null;
    }

    private void tickPlanksPlace() {
        CraftingMenu menu = craftingMenu();

        if (menu == null) {
            endCraftEarly();
            return;
        }

        if (dropCarried(menu)) return;
        checkCraftProgress(false);

        if (stalled >= 3) {
            if (notifications.get()) warning("Planks stopped coming out, moving on.");
            stalled = 0;
            state = craftSlabs.get() ? State.SLABS_FILL : finishState();
            return;
        }

        if (tidyGrid(menu, GRID_FIRST, this::isLog)) return;

        if (!itemAt(menu, GRID_FIRST).isEmpty()) {
            waited = 0;
            state = State.PLANKS_WAIT;
            return;
        }

        int source = findLog(menu, INV_FIRST, MENU_SLOTS);

        if (source < 0) {
            stalled = 0;
            state = craftSlabs.get() ? State.SLABS_FILL : finishState();
            return;
        }

        mc.gameMode.handleContainerInput(menu.containerId, source, 0, ContainerInput.QUICK_MOVE, mc.player);

        waited = 0;
        delayCounter = jitter(craftDelay.get(), 2);
        state = State.PLANKS_WAIT;
    }

    private void tickPlanksWait() {
        CraftingMenu menu = craftingMenu();

        if (menu == null) {
            endCraftEarly();
            return;
        }

        if (!itemAt(menu, RESULT_SLOT).isEmpty()) {
            state = State.PLANKS_CRAFT;
            return;
        }

        if (++waited >= menuTimeout.get()) {
            if (notifications.get()) warning("Those logs have no plank recipe, moving on.");
            stalled = 3;
            state = State.PLANKS_PLACE;
        }
    }

    private void tickPlanksCraft() {
        CraftingMenu menu = craftingMenu();

        if (menu == null) {
            endCraftEarly();
            return;
        }

        craftSnapshot = countLogs();
        outputSnapshot = countPlanks();

        mc.gameMode.handleContainerInput(menu.containerId, RESULT_SLOT, 0, ContainerInput.QUICK_MOVE, mc.player);

        delayCounter = jitter(craftDelay.get(), 2);
        state = State.PLANKS_PLACE;
    }

    private void tickSlabsFill() {
        CraftingMenu menu = craftingMenu();

        if (menu == null) {
            endCraftEarly();
            return;
        }

        if (dropCarried(menu)) return;
        checkCraftProgress(true);

        if (stalled >= 3) {
            if (notifications.get()) warning("Slabs stopped coming out, closing up.");
            state = finishState();
            return;
        }

        if (plankType == null) {
            plankType = bestPlank(menu);

            if (plankType == null) {
                state = finishState();
                return;
            }
        }

        if (tidyGrid(menu, ROW_LAST, stack -> stack.is(plankType))) return;

        if (rowFilled(menu)) {
            waited = 0;
            state = State.SLABS_WAIT;
            return;
        }

        int source = findItem(menu, INV_FIRST, MENU_SLOTS, stack -> stack.is(plankType));

        if (source >= 0) {
            mc.gameMode.handleContainerInput(menu.containerId, source, 0, ContainerInput.QUICK_MOVE, mc.player);
            delayCounter = jitter(craftDelay.get(), 2);
            return;
        }

        if (splitIntoRow(menu)) {
            delayCounter = jitter(craftDelay.get(), 2);
            return;
        }

        if (tidyGrid(menu, 0, stack -> false)) return;

        plankType = null;
    }

    private void tickSlabsWait() {
        CraftingMenu menu = craftingMenu();

        if (menu == null) {
            endCraftEarly();
            return;
        }

        if (!itemAt(menu, RESULT_SLOT).isEmpty()) {
            state = State.SLABS_CRAFT;
            return;
        }

        if (++waited >= menuTimeout.get()) {
            if (notifications.get()) warning("Those planks have no slab recipe, trying another type.");
            if (plankType != null) skippedPlanks.add(plankType);
            plankType = null;
            state = State.SLABS_FILL;
        }
    }

    private void tickSlabsCraft() {
        CraftingMenu menu = craftingMenu();

        if (menu == null) {
            endCraftEarly();
            return;
        }

        craftSnapshot = countPlanks();
        outputSnapshot = countSlabs();

        mc.gameMode.handleContainerInput(menu.containerId, RESULT_SLOT, 0, ContainerInput.QUICK_MOVE, mc.player);

        delayCounter = jitter(craftDelay.get(), 2);
        state = State.SLABS_FILL;
    }

    private void tickDrop() {
        CraftingMenu menu = craftingMenu();

        if (menu == null) {
            endCraftEarly();
            return;
        }

        if (dropCarried(menu)) return;

        int slot = findItem(menu, INV_FIRST, MENU_SLOTS, this::isSlab);

        if (slot < 0) {
            if (notifications.get() && dropped > 0) info("Dropped %d stack(s) of slabs.", dropped);
            state = State.TABLE_CLOSE;
            return;
        }

        ItemStack stack = itemAt(menu, slot);

        if (slot == dropSlot && stack.getCount() == dropCount) {
            if (++dropTries >= 5) {
                if (notifications.get()) warning("Slabs will not drop, closing the table.");
                state = State.TABLE_CLOSE;
                return;
            }
        } else {
            dropSlot = slot;
            dropCount = stack.getCount();
            dropTries = 0;
            dropped++;
        }

        mc.gameMode.handleContainerInput(menu.containerId, slot, 1, ContainerInput.THROW, mc.player);

        delayCounter = dropDelay();
    }

    private State finishState() {
        return dropSlabs.get() ? State.DROP : State.TABLE_CLOSE;
    }

    private void tickTableClose() {
        CraftingMenu menu = craftingMenu();

        if (menu != null) {
            if (dropCarried(menu)) return;
            if (tidyGrid(menu, 0, stack -> false)) return;
        }

        closeAnyMenu();

        if (notifications.get()) info("Crafted %d plank(s) and %d slab(s) this session.", madePlanks, madeSlabs);

        endCycle();
    }

    private void endCraftEarly() {
        if (notifications.get()) warning("Crafting table closed early, backing off.");
        endCycleBackoff();
    }

    private boolean tidyGrid(CraftingMenu menu, int keepUntil, java.util.function.Predicate<ItemStack> keep) {
        for (int slot = GRID_FIRST; slot <= GRID_LAST; slot++) {
            ItemStack stack = itemAt(menu, slot);

            if (stack.isEmpty()) continue;
            if (slot <= keepUntil && keep.test(stack)) continue;

            if (slot == tidySlot && stack.getCount() == tidyCount) {
                if (++tidyTries >= 5) {
                    if (notifications.get()) warning("The grid will not empty, closing the table.");
                    endCycleBackoff();
                    return true;
                }
            } else {
                tidySlot = slot;
                tidyCount = stack.getCount();
                tidyTries = 0;
            }

            mc.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.QUICK_MOVE, mc.player);
            delayCounter = jitter(craftDelay.get(), 2);
            return true;
        }

        tidySlot = -1;
        tidyCount = -1;
        tidyTries = 0;

        return false;
    }

    private boolean rowFilled(CraftingMenu menu) {
        for (int slot = GRID_FIRST; slot <= ROW_LAST; slot++) {
            if (itemAt(menu, slot).isEmpty()) return false;
        }

        return true;
    }

    private boolean splitIntoRow(CraftingMenu menu) {
        int empty = -1;
        int fullest = -1;
        int most = 1;

        for (int slot = GRID_FIRST; slot <= ROW_LAST; slot++) {
            ItemStack stack = itemAt(menu, slot);

            if (stack.isEmpty()) {
                if (empty < 0) empty = slot;
                continue;
            }

            if (stack.getCount() > most) {
                most = stack.getCount();
                fullest = slot;
            }
        }

        if (empty < 0 || fullest < 0) return false;
        if (!menu.getCarried().isEmpty()) return false;

        mc.gameMode.handleContainerInput(menu.containerId, fullest, 1, ContainerInput.PICKUP, mc.player);
        mc.gameMode.handleContainerInput(menu.containerId, empty, 0, ContainerInput.PICKUP, mc.player);
        carrySource = fullest;

        return true;
    }

    private boolean dropCarried(CraftingMenu menu) {
        if (menu.getCarried().isEmpty()) {
            carrySource = -1;
            carryTries = 0;
            return false;
        }

        if (++carryTries > 3) {
            if (notifications.get()) warning("Could not put a held stack down, closing the table.");
            endCycleBackoff();
            return true;
        }

        int target = carrySource >= 0 ? carrySource : firstEmptySlot(menu, INV_FIRST, MENU_SLOTS);
        if (target < 0) target = GRID_FIRST;

        mc.gameMode.handleContainerInput(menu.containerId, target, 0, ContainerInput.PICKUP, mc.player);
        carrySource = -1;
        delayCounter = jitter(craftDelay.get(), 2);

        return true;
    }

    private void checkCraftProgress(boolean slabsPhase) {
        if (craftSnapshot < 0) return;

        int inputNow = slabsPhase ? countPlanks() : countLogs();
        int outputNow = slabsPhase ? countSlabs() : countPlanks();

        if (outputNow > outputSnapshot) {
            if (slabsPhase) madeSlabs += outputNow - outputSnapshot;
            else madePlanks += outputNow - outputSnapshot;
        }

        if (inputNow < craftSnapshot) {
            stalled = 0;
        } else {
            stalled++;
            if (notifications.get() && stalled == 1) info("No %s were used that time, retrying.", slabsPhase ? "planks" : "logs");
        }

        craftSnapshot = -1;
        outputSnapshot = -1;
    }

    private Item bestPlank(CraftingMenu menu) {
        Item best = null;
        int bestCount = 0;

        for (int slot = INV_FIRST; slot < MENU_SLOTS; slot++) {
            ItemStack stack = itemAt(menu, slot);
            if (!isPlank(stack)) continue;

            Item candidate = stack.getItem();
            if (skippedPlanks.contains(candidate)) continue;
            if (best != null && candidate == best) continue;

            int total = 0;
            for (int other = INV_FIRST; other < MENU_SLOTS; other++) {
                ItemStack found = itemAt(menu, other);
                if (found.is(candidate)) total += found.getCount();
            }

            if (total >= 3 && total > bestCount) {
                bestCount = total;
                best = candidate;
            }
        }

        return best;
    }

    private void clickMenu(ChestMenu menu, int slot, State next) {
        markMenu();
        mc.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.PICKUP, mc.player);

        waited = 0;
        delayCounter = jitter(menuDelay.get(), 2);
        state = next;
    }

    private void markMenu() {
        ChestMenu menu = GlazedShop.openContainer();

        if (menu == null) {
            lastMenuId = Integer.MIN_VALUE;
            lastSignature = 0;
            return;
        }

        lastMenuId = menu.containerId;
        lastSignature = signature(menu);
    }

    private boolean isNewMenu(ChestMenu menu) {
        if (menu.containerId != lastMenuId) return true;
        return signature(menu) != lastSignature;
    }

    private long signature(ChestMenu menu) {
        long hash = 1;
        int total = containerSlots(menu);

        for (int slot = 0; slot < total; slot++) {
            ItemStack stack = itemAt(menu, slot);
            hash = hash * 31 + (stack.isEmpty() ? 0 : stack.getItem().hashCode() * 31L + stack.getCount());
        }

        return hash;
    }

    private int containerSlots(ChestMenu menu) {
        return Math.min(GlazedShop.containerSlotCount(menu), menu.slots.size());
    }

    private ItemStack itemAt(net.minecraft.world.inventory.AbstractContainerMenu menu, int slot) {
        if (slot < 0 || slot >= menu.slots.size()) return ItemStack.EMPTY;
        return menu.getSlot(slot).getItem();
    }

    private int lastChestSlot(ChestMenu menu, int total) {
        for (int slot = total - 1; slot >= 0; slot--) {
            if (isChestItem(itemAt(menu, slot))) return slot;
        }

        return -1;
    }

    private int firstFilledSlot(ChestMenu menu, int total) {
        for (int slot = 0; slot < total; slot++) {
            if (!itemAt(menu, slot).isEmpty()) return slot;
        }

        return -1;
    }

    private int chestNearMiddle(ChestMenu menu) {
        int total = containerSlots(menu);
        int wanted = storageSlot.get();

        if (wanted < total && isChestItem(itemAt(menu, wanted))) return wanted;

        int middle = total / 2;
        int best = -1;
        int bestDistance = Integer.MAX_VALUE;

        for (int slot = 0; slot < total; slot++) {
            if (!isChestItem(itemAt(menu, slot))) continue;

            int distance = Math.abs(slot - middle);

            if (distance < bestDistance) {
                bestDistance = distance;
                best = slot;
            }
        }

        return best;
    }

    private int findLog(net.minecraft.world.inventory.AbstractContainerMenu menu, int from, int to) {
        return findItem(menu, from, to, this::isLog);
    }

    private int findItem(net.minecraft.world.inventory.AbstractContainerMenu menu, int from, int to, java.util.function.Predicate<ItemStack> test) {
        for (int slot = from; slot < to && slot < menu.slots.size(); slot++) {
            ItemStack stack = itemAt(menu, slot);
            if (!stack.isEmpty() && test.test(stack)) return slot;
        }

        return -1;
    }

    private int firstEmptySlot(net.minecraft.world.inventory.AbstractContainerMenu menu, int from, int to) {
        for (int slot = from; slot < to && slot < menu.slots.size(); slot++) {
            if (itemAt(menu, slot).isEmpty()) return slot;
        }

        return -1;
    }

    private boolean isChestItem(ItemStack stack) {
        if (stack.isEmpty()) return false;

        return stack.is(Items.CHEST) || stack.is(Items.TRAPPED_CHEST)
            || stack.is(Items.ENDER_CHEST) || stack.is(Items.BARREL);
    }

    private boolean isLog(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ItemTags.LOGS);
    }

    private boolean isPlank(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ItemTags.PLANKS);
    }

    private int countLogs() {
        return countInInventory(this::isLog);
    }

    private int countPlanks() {
        return countInInventory(this::isPlank);
    }

    private int countSlabs() {
        return countInInventory(this::isSlab);
    }

    private boolean isSlab(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ItemTags.SLABS);
    }

    private int countInInventory(java.util.function.Predicate<ItemStack> test) {
        int total = 0;
        int size = mc.player.getInventory().getContainerSize();

        for (int slot = 0; slot < 36 && slot < size; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (!stack.isEmpty() && test.test(stack)) total += stack.getCount();
        }

        return total;
    }

    private int freeSlots() {
        int free = 0;
        int size = mc.player.getInventory().getContainerSize();

        for (int slot = 0; slot < 36 && slot < size; slot++) {
            if (mc.player.getInventory().getItem(slot).isEmpty()) free++;
        }

        return free;
    }

    private int dropDelay() {
        return randomBetween(dropDelayMin.get(), dropDelayMax.get());
    }

    private int takeDelay() {
        return randomBetween(takeDelayMin.get(), takeDelayMax.get());
    }

    private int randomBetween(int low, int high) {
        int min = Math.max(1, low);
        int max = Math.max(min, high);

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

    @Override
    public String getInfoString() {
        return state.toString().toLowerCase().replace("_", " ");
    }
}
