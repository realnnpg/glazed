package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import com.nnpg.glazed.VersionUtil;
import com.nnpg.glazed.settings.RandomBetweenIntSetting;
import com.nnpg.glazed.utils.GlazedSell;
import com.nnpg.glazed.utils.GlazedShop;
import com.nnpg.glazed.utils.RandomBetweenInt;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BoatSeller extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgOrders = settings.createGroup("Orders menu");
    private final SettingGroup sgPrice = settings.createGroup("Price lookup");
    private final SettingGroup sgRelisting = settings.createGroup("Relisting");
    private final SettingGroup sgTiming = settings.createGroup("Timing");

    private final Setting<Item> boat = sgGeneral.add(new ItemSetting.Builder()
        .name("boat")
        .description("Boat type to take from orders and sell.")
        .defaultValue(Items.SPRUCE_BOAT)
        .filter(item -> item instanceof BoatItem)
        .build()
    );

    private final Setting<Integer> undercut = sgGeneral.add(new IntSetting.Builder()
        .name("undercut")
        .description("List this far below the cheapest matching auction.")
        .defaultValue(50)
        .min(1)
        .max(100000)
        .sliderMax(1000)
        .build()
    );

    private final Setting<Integer> minPrice = sgGeneral.add(new IntSetting.Builder()
        .name("min-price")
        .description("Never list below this price. The module waits instead.")
        .defaultValue(1)
        .min(1)
        .max(1000000)
        .sliderMax(10000)
        .build()
    );

    private final Setting<Integer> limitCooldownSeconds = sgGeneral.add(new IntSetting.Builder()
        .name("listing-limit-cooldown")
        .description("Seconds to wait after the auction house says there are too many listings.")
        .defaultValue(60)
        .min(20)
        .max(600)
        .sliderMax(300)
        .build()
    );

    private final Setting<String> limitRegex = sgGeneral.add(new StringSetting.Builder()
        .name("limit-message")
        .description("Case-insensitive regex for the server's auction listing limit message.")
        .defaultValue("(sold too many|too many (items|listed|listings)|listing limit|sell limit|no (more )?(free )?(ah |auction )?slots|max(imum)? listings|reached .{0,20}limit|can only (have|list|sell))")
        .build()
    );

    private final Setting<Boolean> notifications = sgGeneral.add(new BoolSetting.Builder()
        .name("notifications")
        .description("Show chat feedback.")
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
        .description("The first chest to click, counted backward from the end of the orders menu.")
        .defaultValue(3)
        .min(1)
        .max(54)
        .sliderMax(9)
        .build()
    );

    private final Setting<Integer> orderSlot = sgOrders.add(new IntSetting.Builder()
        .name("order-slot")
        .description("Exact slot of the order to use after opening orders. -1 automatically finds the selected boat.")
        .defaultValue(-1)
        .min(-1)
        .max(53)
        .sliderMin(-1)
        .sliderMax(53)
        .build()
    );

    private final Setting<Integer> storageSlot = sgOrders.add(new IntSetting.Builder()
        .name("storage-slot")
        .description("Storage chest slot after selecting the boat. Falls back to the chest nearest the middle.")
        .defaultValue(13)
        .min(0)
        .max(53)
        .sliderMax(53)
        .build()
    );

    private final Setting<TakeMode> takeMode = sgOrders.add(new EnumSetting.Builder<TakeMode>()
        .name("take-mode")
        .description("Auto tries shift-clicking first, then a plain click if the server ignores it.")
        .defaultValue(TakeMode.Auto)
        .build()
    );

    private final Setting<Integer> menuTimeout = sgOrders.add(new IntSetting.Builder()
        .name("menu-timeout")
        .description("Ticks to wait for an orders or auction menu before backing off.")
        .defaultValue(100)
        .min(20)
        .max(500)
        .sliderMax(200)
        .build()
    );

    private final Setting<String> priceCommand = sgPrice.add(new StringSetting.Builder()
        .name("price-command-override")
        .description("Optional AH lookup command without a leading slash. Empty automatically uses the selected boat name.")
        .defaultValue("")
        .build()
    );

    private final Setting<String> priceRegex = sgPrice.add(new StringSetting.Builder()
        .name("price-regex")
        .description("Regex for listing prices. Group 1 is the number and optional group 2 is K, M, or B.")
        .defaultValue("([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([KkMmBb])?")
        .build()
    );

    private final Setting<Boolean> firstListingOnly = sgPrice.add(new BoolSetting.Builder()
        .name("read-first-listing")
        .description("Trust the first matching listing as cheapest. Off scans every matching listing.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Item> listingsButton = sgRelisting.add(new ItemSetting.Builder()
        .name("your-listings-button")
        .description("Item in the auction menu that opens your own listings.")
        .defaultValue(Items.CHEST)
        .build()
    );

    private final Setting<Integer> collectMax = sgRelisting.add(new IntSetting.Builder()
        .name("collect-max")
        .description("Maximum differently-priced boat listings to take back in one pass.")
        .defaultValue(64)
        .min(1)
        .max(500)
        .sliderMax(128)
        .build()
    );

    private final Setting<RandomBetweenInt> menuDelay = sgTiming.add(new RandomBetweenIntSetting.Builder()
        .name("menu-delay-range")
        .description("Random ticks between orders-menu clicks.")
        .defaultRange(6, 12)
        .range(1, 200)
        .sliderRange(1, 60)
        .build()
    );

    private final Setting<RandomBetweenInt> takeDelay = sgTiming.add(new RandomBetweenIntSetting.Builder()
        .name("take-delay-range")
        .description("Random ticks between taking boats from the order.")
        .defaultRange(4, 9)
        .range(1, 200)
        .sliderRange(1, 60)
        .build()
    );

    private final Setting<RandomBetweenInt> collectDelay = sgTiming.add(new RandomBetweenIntSetting.Builder()
        .name("collect-delay-range")
        .description("Random ticks between boats reclaimed from your AH listings.")
        .defaultRange(8, 15)
        .range(1, 200)
        .sliderRange(1, 80)
        .build()
    );

    private final Setting<RandomBetweenInt> screenDelay = sgTiming.add(new RandomBetweenIntSetting.Builder()
        .name("screen-delay-range")
        .description("Random ticks after a screen opens or closes.")
        .defaultRange(5, 10)
        .range(1, 200)
        .sliderRange(1, 60)
        .build()
    );

    private final Setting<RandomBetweenInt> slotDelay = sgTiming.add(new RandomBetweenIntSetting.Builder()
        .name("slot-delay-range")
        .description("Random ticks between selecting a hotbar boat and sending /ah sell.")
        .defaultRange(1, 3)
        .range(1, 100)
        .sliderRange(1, 30)
        .build()
    );

    private final Setting<RandomBetweenInt> confirmDelay = sgTiming.add(new RandomBetweenIntSetting.Builder()
        .name("confirm-delay-range")
        .description("Random ticks before clicking the auction confirmation.")
        .defaultRange(8, 15)
        .range(0, 200)
        .sliderRange(0, 80)
        .build()
    );

    private final Setting<Integer> confirmTimeout = sgTiming.add(new IntSetting.Builder()
        .name("confirm-timeout")
        .description("Ticks to wait for the auction confirmation screen.")
        .defaultValue(80)
        .min(10)
        .max(300)
        .sliderMax(150)
        .build()
    );

    private final Setting<RandomBetweenInt> verifyDelay = sgTiming.add(new RandomBetweenIntSetting.Builder()
        .name("verify-delay-range")
        .description("Random ticks before checking that a listed boat left the inventory.")
        .defaultRange(12, 20)
        .range(1, 200)
        .sliderRange(1, 80)
        .build()
    );

    private final Setting<RandomBetweenInt> gapDelay = sgTiming.add(new RandomBetweenIntSetting.Builder()
        .name("listing-gap-range")
        .description("Random ticks between finished auction listings.")
        .defaultRange(3, 8)
        .range(0, 200)
        .sliderRange(0, 60)
        .build()
    );

    private final Setting<RandomBetweenInt> cycleDelay = sgTiming.add(new RandomBetweenIntSetting.Builder()
        .name("cycle-delay-range")
        .description("Random ticks between selling a batch and checking orders again.")
        .defaultRange(30, 60)
        .range(1, 2000)
        .sliderRange(1, 300)
        .build()
    );

    private final Setting<RandomBetweenInt> idleBackoff = sgTiming.add(new RandomBetweenIntSetting.Builder()
        .name("idle-backoff-range")
        .description("Random ticks before retrying an empty order or failed action.")
        .defaultRange(300, 600)
        .range(20, 6000)
        .sliderRange(20, 1200)
        .build()
    );

    private final Setting<Integer> hesitationChance = sgTiming.add(new IntSetting.Builder()
        .name("hesitation-chance")
        .description("Chance for an item click to add an extra human-like pause.")
        .defaultValue(8)
        .min(0)
        .max(50)
        .sliderMax(30)
        .build()
    );

    private final Setting<RandomBetweenInt> hesitationDelay = sgTiming.add(new RandomBetweenIntSetting.Builder()
        .name("hesitation-delay-range")
        .description("Extra ticks added when a hesitation occurs.")
        .defaultRange(8, 30)
        .range(1, 400)
        .sliderRange(1, 100)
        .build()
    );

    public enum TakeMode { Auto, ShiftClick, Click }

    private enum State {
        IDLE,
        ORDERS_SEND, ORDERS_WAIT, ORDERS_CLICK,
        BOAT_MENU_WAIT, BOAT_MENU_CLICK,
        STORAGE_WAIT, STORAGE_CLICK,
        ITEMS_WAIT, TAKE, TAKE_SETTLE, ORDERS_CLOSE,
        PRICE_SEND, PRICE_WAIT, PRICE_CLOSE,
        COLLECT_SEND, COLLECT_OPEN_MINE, COLLECT_MINE_WAIT,
        COLLECT_CLICK, COLLECT_SETTLE, COLLECT_CLOSE,
        SELL_SELECT, SELL_SEND, SELL_CONFIRM, SELL_VERIFY, SELL_GAP,
        PULL_OPEN, PULL, PULL_CLOSE,
        COOLDOWN
    }

    private final Random random = new Random();
    private State state = State.IDLE;
    private int delayCounter;
    private int waited;
    private int lastMenuId;
    private long lastSignature;
    private int takeSource;
    private int boatsBeforeTake;
    private boolean plainClick;
    private int stalled;
    private int grabbed;
    private int collected;
    private int collectSource;
    private int boatsBeforeCollect;
    private ItemStack collectSnapshot = ItemStack.EMPTY;
    private int currentSlot;
    private int listed;
    private int sessionListed;
    private int stalledPulls;
    private int displacedMainSlot;
    private int displacedHotbarSlot;
    private ItemStack displacedHotbarItem = ItemStack.EMPTY;
    private long listPrice;
    private ItemStack soldRef = ItemStack.EMPTY;
    private int countBeforeSale;

    public BoatSeller() {
        super(GlazedAddon.CATEGORY, "boat-seller", "Takes boats from /orders, undercuts the cheapest matching AH listing, and lists them all.");
    }

    @Override
    public void onActivate() {
        resetCycle();
        sessionListed = 0;
        delayCounter = 0;
        state = State.IDLE;
    }

    @Override
    public void onDeactivate() {
        closeAnyMenu();
        restoreDisplacedHotbarItem();
        state = State.IDLE;
    }

    private void resetCycle() {
        waited = 0;
        lastMenuId = Integer.MIN_VALUE;
        lastSignature = 0;
        takeSource = -1;
        boatsBeforeTake = 0;
        plainClick = false;
        stalled = 0;
        grabbed = 0;
        collected = 0;
        collectSource = -1;
        boatsBeforeCollect = 0;
        collectSnapshot = ItemStack.EMPTY;
        currentSlot = 0;
        listed = 0;
        stalledPulls = 0;
        displacedMainSlot = -1;
        displacedHotbarSlot = -1;
        displacedHotbarItem = ItemStack.EMPTY;
        listPrice = 0;
        soldRef = ItemStack.EMPTY;
        countBeforeSale = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.gameMode == null || mc.getConnection() == null) return;

        if (delayCounter > 0) {
            delayCounter--;
            return;
        }

        switch (state) {
            case IDLE -> tickIdle();
            case ORDERS_SEND -> tickOrdersSend();
            case ORDERS_WAIT -> tickOrdersWait();
            case ORDERS_CLICK -> tickOrdersClick();
            case BOAT_MENU_WAIT -> tickMenuWait(State.BOAT_MENU_CLICK, "boat selection menu");
            case BOAT_MENU_CLICK -> tickBoatMenuClick();
            case STORAGE_WAIT -> tickMenuWait(State.STORAGE_CLICK, "boat order menu");
            case STORAGE_CLICK -> tickStorageClick();
            case ITEMS_WAIT -> tickMenuWait(State.TAKE, "boat storage");
            case TAKE -> tickTake();
            case TAKE_SETTLE -> tickTakeSettle();
            case ORDERS_CLOSE -> tickOrdersClose();
            case PRICE_SEND -> tickPriceSend();
            case PRICE_WAIT -> tickPriceWait();
            case PRICE_CLOSE -> tickPriceClose();
            case COLLECT_SEND -> tickCollectSend();
            case COLLECT_OPEN_MINE -> tickCollectOpenMine();
            case COLLECT_MINE_WAIT -> tickCollectMineWait();
            case COLLECT_CLICK -> tickCollectClick();
            case COLLECT_SETTLE -> tickCollectSettle();
            case COLLECT_CLOSE -> tickCollectClose();
            case SELL_SELECT -> tickSellSelect();
            case SELL_SEND -> tickSellSend();
            case SELL_CONFIRM -> tickSellConfirm();
            case SELL_VERIFY -> tickSellVerify();
            case SELL_GAP -> {
                currentSlot++;
                state = State.SELL_SELECT;
            }
            case PULL_OPEN -> tickPullOpen();
            case PULL -> tickPull();
            case PULL_CLOSE -> tickPullClose();
            case COOLDOWN -> {
                resetCycle();
                state = State.IDLE;
            }
        }
    }

    private void tickIdle() {
        if (countBoats() <= 0 && freeSlots() <= 0) {
            if (notifications.get()) warning("Inventory is full; waiting for room before opening orders.");
            backoff();
            return;
        }

        state = State.PRICE_SEND;
    }

    private void tickOrdersSend() {
        markMenu();
        ChatUtils.sendPlayerMsg(ordersCommand.get());
        waited = 0;
        delayCounter = delay(screenDelay, false);
        state = State.ORDERS_WAIT;
    }

    private void tickOrdersWait() {
        if (GlazedShop.openContainer() != null) {
            delayCounter = delay(menuDelay, true);
            state = State.ORDERS_CLICK;
            return;
        }

        if (++waited >= menuTimeout.get()) fail("Orders menu never opened.");
    }

    private void tickOrdersClick() {
        ChestMenu menu = GlazedShop.openContainer();
        if (menu == null) {
            fail("Orders menu closed early.");
            return;
        }

        int total = containerSlots(menu);
        int wanted = total - ordersSlotFromEnd.get();
        int slot = isChest(itemAt(menu, wanted)) ? wanted : lastChestSlot(menu, total);

        if (slot < 0) {
            fail("No storage chest was found in the orders menu.");
            return;
        }

        clickMenu(menu, slot, State.BOAT_MENU_WAIT);
    }

    private void tickBoatMenuClick() {
        ChestMenu menu = GlazedShop.openContainer();
        if (menu == null) {
            fail("Boat selection menu closed early.");
            return;
        }

        int total = containerSlots(menu);
        int configured = orderSlot.get();
        int slot = configured >= 0 && configured < total && !itemAt(menu, configured).isEmpty()
            ? configured
            : findItem(menu, 0, total, boat.get());

        if (slot < 0) {
            fail(configured >= 0
                ? "Configured order-slot %d is empty and no %s order was found.".formatted(configured, boatName())
                : "No %s order was found.".formatted(boatName()));
            return;
        }

        clickMenu(menu, slot, State.STORAGE_WAIT);
    }

    private void tickStorageClick() {
        ChestMenu menu = GlazedShop.openContainer();
        if (menu == null) {
            fail("Boat order menu closed early.");
            return;
        }

        int slot = chestNearMiddle(menu);
        if (slot < 0) {
            fail("No storage chest was found for that boat order.");
            return;
        }

        clickMenu(menu, slot, State.ITEMS_WAIT);
    }

    private void tickMenuWait(State next, String name) {
        ChestMenu menu = GlazedShop.openContainer();

        if (menu != null && isNewMenu(menu)) {
            waited = 0;
            stalled = 0;
            delayCounter = delay(menuDelay, true);
            state = next;
            return;
        }

        if (++waited >= menuTimeout.get()) fail("The " + name + " never opened.");
    }

    private void tickTake() {
        ChestMenu menu = GlazedShop.openContainer();
        if (menu == null) {
            if (grabbed > 0) state = State.ORDERS_CLOSE;
            else fail("Boat storage closed before any boats were taken.");
            return;
        }

        if (freeSlots() <= 0) {
            state = State.ORDERS_CLOSE;
            return;
        }

        int source = findItem(menu, 0, containerSlots(menu), boat.get());
        if (source < 0) {
            if (notifications.get() && grabbed == 0) info("No %s are waiting in that order.", boatName());
            state = State.ORDERS_CLOSE;
            return;
        }

        boatsBeforeTake = countBoats();
        takeSource = source;
        boolean plain = takeMode.get() == TakeMode.Click || (takeMode.get() == TakeMode.Auto && plainClick);

        mc.gameMode.handleContainerInput(menu.containerId, source, 0,
            plain ? ContainerInput.PICKUP : ContainerInput.QUICK_MOVE, mc.player);

        delayCounter = delay(takeDelay, true);
        state = State.TAKE_SETTLE;
    }

    private void tickTakeSettle() {
        ChestMenu menu = GlazedShop.openContainer();

        if (menu != null && !menu.getCarried().isEmpty() && takeSource >= 0) {
            mc.gameMode.handleContainerInput(menu.containerId, takeSource, 0, ContainerInput.PICKUP, mc.player);
            delayCounter = delay(takeDelay, true);
            return;
        }

        int now = countBoats();
        if (now > boatsBeforeTake) {
            grabbed += now - boatsBeforeTake;
            stalled = 0;
            state = State.TAKE;
            return;
        }

        stalled++;
        if (takeMode.get() == TakeMode.Auto && !plainClick && stalled >= 2) {
            plainClick = true;
            stalled = 0;
            if (notifications.get()) info("Shift clicking did nothing; trying a plain click.");
            state = State.TAKE;
            return;
        }

        if (stalled >= 3) {
            if (notifications.get()) warning("Boats are not coming out of the order.");
            state = State.ORDERS_CLOSE;
            return;
        }

        state = State.TAKE;
    }

    private void tickOrdersClose() {
        ChestMenu menu = GlazedShop.openContainer();
        if (menu != null && !menu.getCarried().isEmpty() && takeSource >= 0) {
            mc.gameMode.handleContainerInput(menu.containerId, takeSource, 0, ContainerInput.PICKUP, mc.player);
            delayCounter = delay(takeDelay, true);
            return;
        }

        closeAnyMenu();
        if (countBoats() <= 0) {
            backoff();
            return;
        }

        if (notifications.get()) info("Took %d %s; checking the auction price.", grabbed, boatName());
        delayCounter = delay(screenDelay, false);
        state = State.PRICE_SEND;
    }

    private void tickPriceSend() {
        closeAnyMenu();
        mc.getConnection().sendCommand(resolvedPriceCommand());
        waited = 0;
        delayCounter = delay(screenDelay, false);
        state = State.PRICE_WAIT;
    }

    private void tickPriceWait() {
        ChestMenu menu = GlazedShop.openContainer();
        if (menu != null) {
            long cheapest = cheapestListing(menu);
            if (cheapest <= 0) {
                if (notifications.get()) warning("No priced %s listing was found; keeping the boats and retrying later.", boatName());
                state = State.PRICE_CLOSE;
                return;
            }

            long candidate = cheapest - undercut.get();
            if (candidate < minPrice.get()) {
                if (notifications.get()) warning("Cheapest is %d; undercutting by %d would go below min-price %d.", cheapest, undercut.get(), minPrice.get());
                state = State.PRICE_CLOSE;
                return;
            }

            listPrice = candidate;
            if (notifications.get()) info("Cheapest %s is %d; listing at %d.", boatName(), cheapest, listPrice);
            state = State.PRICE_CLOSE;
            return;
        }

        if (++waited >= menuTimeout.get()) fail("Auction listings never opened.");
    }

    private void tickPriceClose() {
        closeAnyMenu();
        delayCounter = delay(screenDelay, false);

        if (listPrice <= 0) {
            backoff();
            return;
        }

        collected = 0;
        state = State.COLLECT_SEND;
    }

    private long cheapestListing(ChestMenu menu) {
        Pattern pattern;
        try {
            pattern = Pattern.compile(priceRegex.get());
        } catch (Exception e) {
            if (notifications.get()) error("price-regex does not compile: " + e.getMessage());
            return -1;
        }

        long cheapest = -1;
        for (int slot = 0; slot < containerSlots(menu); slot++) {
            ItemStack stack = itemAt(menu, slot);
            if (stack.isEmpty() || !stack.is(boat.get())) continue;

            long price = parseListingPrice(stack, pattern);
            if (price <= 0) continue;
            if (firstListingOnly.get()) return price;
            if (cheapest < 0 || price < cheapest) cheapest = price;
        }

        return cheapest;
    }

    private long parseListingPrice(ItemStack stack, Pattern pattern) {
        List<String> lines = new ArrayList<>();
        lines.add(stack.getHoverName().getString());

        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null) {
            for (Component line : lore.lines()) lines.add(line.getString());
        }

        for (String line : lines) {
            String lower = line.toLowerCase(Locale.ROOT);
            if (!lower.contains("price") && !line.contains("$")) continue;
            long price = matchPrice(line, pattern);
            if (price > 0) return price;
        }

        for (String line : lines) {
            long price = matchPrice(line, pattern);
            if (price > 0) return price;
        }

        return -1;
    }

    private long matchPrice(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find() || matcher.group(1) == null) return -1;

        double value;
        try {
            value = Double.parseDouble(matcher.group(1).replace(",", ""));
        } catch (NumberFormatException e) {
            return -1;
        }

        if (matcher.groupCount() >= 2 && matcher.group(2) != null) {
            value *= switch (matcher.group(2).toUpperCase(Locale.ROOT)) {
                case "K" -> 1_000.0;
                case "M" -> 1_000_000.0;
                case "B" -> 1_000_000_000.0;
                default -> 1.0;
            };
        }

        return (long) value;
    }

    private void tickCollectSend() {
        closeAnyMenu();
        mc.getConnection().sendCommand(resolvedPriceCommand());
        waited = 0;
        delayCounter = delay(screenDelay, false);
        state = State.COLLECT_OPEN_MINE;
    }

    private void tickCollectOpenMine() {
        ChestMenu menu = GlazedShop.openContainer();
        if (menu == null) {
            if (++waited >= menuTimeout.get()) fail("Auction menu never opened for the relisting check.");
            return;
        }

        int button = findItem(menu, 0, containerSlots(menu), listingsButton.get());
        if (button < 0) {
            if (++waited >= menuTimeout.get()) {
                fail("No %s button for Your Listings was found.".formatted(
                    listingsButton.get().getDefaultInstance().getHoverName().getString()));
            }
            return;
        }

        markMenu();
        mc.gameMode.handleContainerInput(menu.containerId, button, 0, ContainerInput.PICKUP, mc.player);
        waited = 0;
        delayCounter = delay(menuDelay, true);
        state = State.COLLECT_MINE_WAIT;
    }

    private void tickCollectMineWait() {
        ChestMenu menu = GlazedShop.openContainer();
        if (menu != null && isNewMenu(menu)) {
            waited = 0;
            state = State.COLLECT_CLICK;
            return;
        }

        if (++waited >= menuTimeout.get()) fail("Your Listings menu never opened.");
    }

    private void tickCollectClick() {
        ChestMenu menu = GlazedShop.openContainer();
        if (menu == null) {
            state = State.COLLECT_CLOSE;
            return;
        }

        if (collected >= collectMax.get() || freeSlots() <= 0) {
            state = State.COLLECT_CLOSE;
            return;
        }

        int target = findDifferentlyPricedBoat(menu);
        if (target < 0) {
            state = State.COLLECT_CLOSE;
            return;
        }

        collectSource = target;
        boatsBeforeCollect = countBoats();
        collectSnapshot = itemAt(menu, target).copy();
        mc.gameMode.handleContainerInput(menu.containerId, target, 0, ContainerInput.PICKUP, mc.player);

        waited = 0;
        delayCounter = delay(collectDelay, true);
        state = State.COLLECT_SETTLE;
    }

    private void tickCollectSettle() {
        int now = countBoats();
        if (now > boatsBeforeCollect) {
            collected += now - boatsBeforeCollect;
            collectSnapshot = ItemStack.EMPTY;
            waited = 0;
            state = State.COLLECT_CLICK;
            return;
        }

        ChestMenu menu = GlazedShop.openContainer();
        if (menu == null) {
            if (notifications.get()) warning("Your Listings closed while reclaiming a boat.");
            state = State.COLLECT_CLOSE;
            return;
        }

        if (collectSource < 0 || !ItemStack.matches(collectSnapshot, itemAt(menu, collectSource))) {
            collectSnapshot = ItemStack.EMPTY;
            waited = 0;
            state = State.COLLECT_CLICK;
            return;
        }

        if (++waited >= menuTimeout.get()) {
            if (notifications.get()) warning("A boat listing would not return to the inventory; continuing with what was reclaimed.");
            state = State.COLLECT_CLOSE;
        }
    }

    private void tickCollectClose() {
        closeAnyMenu();
        delayCounter = delay(screenDelay, false);

        if (collected > 0 && notifications.get()) {
            info("Reclaimed %d differently-priced %s listing(s).", collected, boatName());
        }

        if (countBoats() > 0) {
            currentSlot = 0;
            listed = 0;
            state = State.SELL_SELECT;
            return;
        }

        if (freeSlots() <= 0) {
            backoff();
            return;
        }

        state = State.ORDERS_SEND;
    }

    private int findDifferentlyPricedBoat(ChestMenu menu) {
        Pattern pattern;
        try {
            pattern = Pattern.compile(priceRegex.get());
        } catch (Exception e) {
            if (notifications.get()) error("price-regex does not compile: " + e.getMessage());
            return -1;
        }

        int unpricedFallback = -1;
        for (int slot = 0; slot < containerSlots(menu); slot++) {
            ItemStack stack = itemAt(menu, slot);
            if (stack.isEmpty() || !stack.is(boat.get())) continue;

            long price = parseListingPrice(stack, pattern);
            if (price > 0 && price != listPrice) return slot;
            if (price <= 0 && unpricedFallback < 0) unpricedFallback = slot;
        }

        return unpricedFallback;
    }

    private void tickSellSelect() {
        if (currentSlot > 8) {
            if (hasBoatInMainInventory()) {
                state = State.PULL_OPEN;
                return;
            }

            if (notifications.get()) info("Listed %d %s, %d this session.", listed, boatName(), sessionListed);
            normalCooldown();
            return;
        }

        ItemStack stack = mc.player.getInventory().getItem(currentSlot);
        if (stack.isEmpty() || !stack.is(boat.get())) {
            currentSlot++;
            return;
        }

        VersionUtil.setSelectedSlot(mc.player, currentSlot);
        delayCounter = delay(slotDelay, false);
        state = State.SELL_SEND;
    }

    private void tickSellSend() {
        ItemStack stack = mc.player.getInventory().getItem(currentSlot);
        if (stack.isEmpty() || !stack.is(boat.get())) {
            currentSlot++;
            state = State.SELL_SELECT;
            return;
        }

        soldRef = stack.copy();
        countBeforeSale = countMatching(soldRef);
        mc.getConnection().sendCommand("ah sell " + listPrice);
        waited = 0;
        delayCounter = delay(confirmDelay, false);
        state = State.SELL_CONFIRM;
    }

    private void tickSellConfirm() {
        if (GlazedSell.isDialogOpen()) {
            if (GlazedSell.clickDialogYes()) {
                delayCounter = delay(verifyDelay, false);
                state = State.SELL_VERIFY;
            }
            return;
        }

        AbstractContainerMenu menu = mc.player.containerMenu;
        if (menu instanceof ChestMenu chest && chest.getRowCount() == 3 && GlazedSell.clickConfirm(chest)) {
            delayCounter = delay(verifyDelay, false);
            state = State.SELL_VERIFY;
            return;
        }

        if (++waited >= confirmTimeout.get()) {
            if (notifications.get()) warning("No confirmation appeared for hotbar slot %d; keeping that boat.", currentSlot);
            backoff();
        }
    }

    private void tickSellVerify() {
        int after = countMatching(soldRef);
        if (after >= countBeforeSale) {
            if (notifications.get()) warning("The listing came back (%d -> %d); re-pricing after a backoff.", countBeforeSale, after);
            soldRef = ItemStack.EMPTY;
            backoff();
            return;
        }

        listed++;
        sessionListed++;
        soldRef = ItemStack.EMPTY;
        delayCounter = delay(gapDelay, true);
        state = State.SELL_GAP;
    }

    private void tickPullOpen() {
        if (mc.player.containerMenu != mc.player.inventoryMenu) {
            closeAnyMenu();
            delayCounter = delay(screenDelay, false);
            return;
        }

        if (mc.screen instanceof InventoryScreen) {
            state = State.PULL;
            return;
        }

        mc.setScreen(new InventoryScreen(mc.player));
        delayCounter = delay(screenDelay, false);
        state = State.PULL;
    }

    private void tickPull() {
        if (mc.player.containerMenu != mc.player.inventoryMenu || !(mc.screen instanceof InventoryScreen)) {
            state = State.PULL_OPEN;
            return;
        }

        int source = firstMainInventoryBoat();
        int target = firstEmptyHotbarSlot();
        if (source < 0) {
            state = State.PULL_CLOSE;
            return;
        }

        if (target < 0) {
            target = firstNonBoatHotbarSlot();

            if (target < 0) {
                state = State.PULL_CLOSE;
                return;
            }

            displacedMainSlot = source;
            displacedHotbarSlot = target;
            displacedHotbarItem = mc.player.getInventory().getItem(target).copy();
        }

        ItemStack before = mc.player.getInventory().getItem(source).copy();
        mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, source, target, ContainerInput.SWAP, mc.player);

        if (ItemStack.matches(before, mc.player.getInventory().getItem(source))) {
            if (++stalledPulls >= 3) {
                if (notifications.get()) warning("Could not move boats into the hotbar.");
                state = State.PULL_CLOSE;
                return;
            }
        } else {
            stalledPulls = 0;
        }

        delayCounter = delay(takeDelay, true);
    }

    private void tickPullClose() {
        if (mc.screen instanceof InventoryScreen screen) screen.onClose();
        currentSlot = 0;
        stalledPulls = 0;
        delayCounter = delay(screenDelay, false);
        state = State.SELL_SELECT;
    }

    private String resolvedPriceCommand() {
        String override = priceCommand.get().trim();
        if (!override.isEmpty()) return stripSlash(override);
        return "ah " + boatName().toLowerCase(Locale.ROOT);
    }

    private String stripSlash(String command) {
        return command.startsWith("/") ? command.substring(1) : command;
    }

    private String boatName() {
        return boat.get().getDefaultInstance().getHoverName().getString();
    }

    private int countBoats() {
        int total = 0;
        int size = Math.min(36, mc.player.getInventory().getContainerSize());
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.is(boat.get())) total += stack.getCount();
        }
        return total;
    }

    private int countMatching(ItemStack reference) {
        if (reference.isEmpty()) return 0;
        int total = 0;
        int size = Math.min(36, mc.player.getInventory().getContainerSize());
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, reference)) total += stack.getCount();
        }
        return total;
    }

    private int freeSlots() {
        int free = 0;
        int size = Math.min(36, mc.player.getInventory().getContainerSize());
        for (int slot = 0; slot < size; slot++) {
            if (mc.player.getInventory().getItem(slot).isEmpty()) free++;
        }
        return free;
    }

    private boolean hasBoatInMainInventory() {
        return firstMainInventoryBoat() >= 0;
    }

    private int firstMainInventoryBoat() {
        for (int slot = 9; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.is(boat.get())) return slot;
        }
        return -1;
    }

    private int firstEmptyHotbarSlot() {
        for (int slot = 0; slot <= 8; slot++) {
            if (mc.player.getInventory().getItem(slot).isEmpty()) return slot;
        }
        return -1;
    }

    private int firstNonBoatHotbarSlot() {
        for (int slot = 0; slot <= 8; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (!stack.isEmpty() && !stack.is(boat.get())) return slot;
        }
        return -1;
    }

    private void restoreDisplacedHotbarItem() {
        if (displacedMainSlot < 0 || displacedHotbarSlot < 0 || displacedHotbarItem.isEmpty()) return;
        if (mc.player == null || mc.gameMode == null) return;

        ItemStack parked = mc.player.getInventory().getItem(displacedMainSlot);
        if (!ItemStack.isSameItemSameComponents(parked, displacedHotbarItem)
            || parked.getCount() != displacedHotbarItem.getCount()) {
            if (notifications.get()) warning("The temporarily parked hotbar item moved, so it was left in the inventory.");
        } else {
            mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId,
                displacedMainSlot, displacedHotbarSlot, ContainerInput.SWAP, mc.player);
        }

        displacedMainSlot = -1;
        displacedHotbarSlot = -1;
        displacedHotbarItem = ItemStack.EMPTY;
    }

    private void clickMenu(ChestMenu menu, int slot, State next) {
        markMenu();
        mc.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.PICKUP, mc.player);
        waited = 0;
        delayCounter = delay(menuDelay, true);
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
        return menu.containerId != lastMenuId || signature(menu) != lastSignature;
    }

    private long signature(ChestMenu menu) {
        long hash = 1;
        for (int slot = 0; slot < containerSlots(menu); slot++) {
            ItemStack stack = itemAt(menu, slot);
            hash = hash * 31 + (stack.isEmpty() ? 0 : stack.getItem().hashCode() * 31L + stack.getCount());
        }
        return hash;
    }

    private int containerSlots(ChestMenu menu) {
        return Math.min(GlazedShop.containerSlotCount(menu), menu.slots.size());
    }

    private ItemStack itemAt(AbstractContainerMenu menu, int slot) {
        if (slot < 0 || slot >= menu.slots.size()) return ItemStack.EMPTY;
        return menu.getSlot(slot).getItem();
    }

    private int findItem(ChestMenu menu, int from, int to, Item item) {
        for (int slot = from; slot < to && slot < menu.slots.size(); slot++) {
            if (itemAt(menu, slot).is(item)) return slot;
        }
        return -1;
    }

    private boolean isChest(ItemStack stack) {
        return stack.is(Items.CHEST) || stack.is(Items.TRAPPED_CHEST)
            || stack.is(Items.ENDER_CHEST) || stack.is(Items.BARREL);
    }

    private int lastChestSlot(ChestMenu menu, int total) {
        for (int slot = total - 1; slot >= 0; slot--) {
            if (isChest(itemAt(menu, slot))) return slot;
        }
        return -1;
    }

    private int chestNearMiddle(ChestMenu menu) {
        int total = containerSlots(menu);
        int configured = storageSlot.get();
        if (configured < total && isChest(itemAt(menu, configured))) return configured;

        int middle = total / 2;
        int best = -1;
        int distance = Integer.MAX_VALUE;
        for (int slot = 0; slot < total; slot++) {
            if (!isChest(itemAt(menu, slot))) continue;
            int candidate = Math.abs(slot - middle);
            if (candidate < distance) {
                distance = candidate;
                best = slot;
            }
        }
        return best;
    }

    private int delay(Setting<RandomBetweenInt> range, boolean allowHesitation) {
        int ticks = Math.max(0, range.get().getRandom());
        if (allowHesitation && random.nextInt(100) < hesitationChance.get()) {
            ticks += Math.max(1, hesitationDelay.get().getRandom());
        }
        return ticks;
    }

    private void normalCooldown() {
        closeAnyMenu();
        restoreDisplacedHotbarItem();
        delayCounter = Math.max(1, delay(cycleDelay, false));
        state = State.COOLDOWN;
    }

    private void backoff() {
        closeAnyMenu();
        restoreDisplacedHotbarItem();
        delayCounter = Math.max(20, delay(idleBackoff, false));
        state = State.COOLDOWN;
    }

    private void fail(String message) {
        if (notifications.get()) warning(message);
        backoff();
    }

    private void closeAnyMenu() {
        if (mc.player != null && mc.player.containerMenu != mc.player.inventoryMenu) mc.player.closeContainer();
        if (mc.screen != null) mc.setScreen(null);
    }

    private void startLimitCooldown() {
        closeAnyMenu();
        restoreDisplacedHotbarItem();
        delayCounter = limitCooldownSeconds.get() * 20 + random.nextInt(101);
        state = State.COOLDOWN;
        if (notifications.get()) info("Auction listing limit reached; keeping the remaining boats and retrying in about %d seconds.", limitCooldownSeconds.get());
    }

    @EventHandler
    private void onChatMessage(ReceiveMessageEvent event) {
        if (!isActive() || state == State.COOLDOWN) return;

        String message = event.getMessage().getString();
        if (message.contains("[Meteor]")) return;

        try {
            if (Pattern.compile(limitRegex.get(), Pattern.CASE_INSENSITIVE).matcher(message).find()) {
                startLimitCooldown();
            }
        } catch (Exception e) {
            if (notifications.get()) error("limit-message regex does not compile: " + e.getMessage());
        }
    }

    @Override
    public String getInfoString() {
        return state.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
