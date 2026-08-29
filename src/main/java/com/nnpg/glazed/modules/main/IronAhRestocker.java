package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import com.nnpg.glazed.utils.GlazedSell;
import com.nnpg.glazed.VersionUtil;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IronAhRestocker extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPrice = settings.createGroup("Price lookup");
    private final SettingGroup sgRemover = settings.createGroup("Listing remover");
    private final SettingGroup sgTiming = settings.createGroup("Timing");

    private final Setting<Item> item = sgGeneral.add(new ItemSetting.Builder()
        .name("item")
        .description("What to restock. The price lookup matches this item in the ah listings.")
        .defaultValue(Items.IRON_INGOT)
        .build()
    );

    private final Setting<Integer> undercut = sgGeneral.add(new IntSetting.Builder()
        .name("undercut")
        .description("List this far below the cheapest per-unit price on the ah.")
        .defaultValue(100)
        .min(1)
        .max(100000)
        .sliderMax(1000)
        .build()
    );

    private final Setting<Integer> failUndercut = sgGeneral.add(new IntSetting.Builder()
        .name("undercut-after-fail")
        .description("After a listing bounces back, go at least this much below the price that failed, so the retry is not the same number again.")
        .defaultValue(100)
        .min(0)
        .max(100000)
        .sliderMax(1000)
        .build()
    );

    private final Setting<Integer> minPrice = sgGeneral.add(new IntSetting.Builder()
        .name("min-price")
        .description("Never list below this. The module stops instead of going under it.")
        .defaultValue(1)
        .min(1)
        .max(1000000)
        .sliderMax(10000)
        .build()
    );

    private final Setting<Integer> cycleCooldownSeconds = sgGeneral.add(new IntSetting.Builder()
        .name("limit-cooldown-seconds")
        .description("Seconds to sit out after the server says you have listed too many items. Nothing else parks the module this long.")
        .defaultValue(300)
        .min(20)
        .max(300)
        .sliderMin(20)
        .sliderMax(300)
        .build()
    );

    private final Setting<String> limitRegex = sgGeneral.add(new StringSetting.Builder()
        .name("limit-message")
        .description("Case insensitive regex for the red 'sold too many items' warning. Matching it is the only thing that parks the module for the long cooldown.")
        .defaultValue("(sold too many|too many (items|listed|listings)|listing limit|sell limit|max(imum)? listings|reached .{0,20}limit|can only (have|list|sell))")
        .build()
    );

    private final Setting<Boolean> requireChestLook = sgGeneral.add(new BoolSetting.Builder()
        .name("require-chest-look")
        .description("Only start a cycle while your crosshair is on a chest. The chest is what gets opened for the restock.")
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

    private final Setting<String> priceCommand = sgPrice.add(new StringSetting.Builder()
        .name("price-command")
        .description("Command run to open the listings for this item. No leading slash.")
        .defaultValue("ah iron ingot")
        .build()
    );

    private final Setting<String> priceRegex = sgPrice.add(new StringSetting.Builder()
        .name("price-regex")
        .description("Regex matched against the listing's name and lore. Group 1 is the number, optional group 2 is a K/M/B suffix. Handles 2.5k, $2,500 and plain 2500.")
        .defaultValue("([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([KkMmBb])?")
        .build()
    );

    private final Setting<Boolean> firstListingOnly = sgPrice.add(new BoolSetting.Builder()
        .name("read-first-listing")
        .description("Read the price off the first listing in the menu, which is the cheapest when the ah is sorted by price. Off scans every slot and takes the lowest it can find.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> perUnit = sgPrice.add(new BoolSetting.Builder()
        .name("divide-by-stack-size")
        .description("Treat a listing's price as being for the whole stack and divide by its count to get the per-unit price.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> priceTimeout = sgPrice.add(new IntSetting.Builder()
        .name("price-timeout")
        .description("Ticks to wait for the listings menu before giving up on this cycle.")
        .defaultValue(80)
        .min(20)
        .max(400)
        .sliderMax(200)
        .build()
    );

    private final Setting<Boolean> removeListings = sgRemover.add(new BoolSetting.Builder()
        .name("remove-listings")
        .description("After the limit cooldown, pull your unsold listings back off the ah before re-pricing and listing again.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ClearWhen> clearWhen = sgRemover.add(new EnumSetting.Builder<ClearWhen>()
        .name("clear-when")
        .description("When the clearing pass runs. OnlyWhenFull is the old behaviour: only after the server says you have listed too many.")
        .defaultValue(ClearWhen.OnlyWhenFull)
        .build()
    );

    private final Setting<Integer> clearEveryCycles = sgRemover.add(new IntSetting.Builder()
        .name("clear-every-cycles")
        .description("How many inventories to sell between clearing passes. Only used when clear-when is EveryNCycles.")
        .defaultValue(3)
        .min(1)
        .max(50)
        .sliderMax(20)
        .build()
    );

    private final Setting<Item> listingsButton = sgRemover.add(new ItemSetting.Builder()
        .name("your-listings-button")
        .description("The item in the ah menu that opens your own listings. Usually the chest.")
        .defaultValue(Items.CHEST)
        .build()
    );

    private final Setting<Integer> removeDelay = sgRemover.add(new IntSetting.Builder()
        .name("remove-delay")
        .description("Ticks between each listing click. Deliberately slower than the other clicks so a full clear is not a burst.")
        .defaultValue(12)
        .min(2)
        .max(60)
        .sliderMax(40)
        .build()
    );

    private final Setting<Integer> removeMax = sgRemover.add(new IntSetting.Builder()
        .name("remove-max")
        .description("Stop after this many listings in one pass, whatever is left.")
        .defaultValue(64)
        .min(1)
        .max(500)
        .sliderMax(128)
        .build()
    );

    private final Setting<String> goneRegex = sgRemover.add(new StringSetting.Builder()
        .name("gone-message")
        .description("Case insensitive regex for the server saying a listing you tried to take back is not there any more. Only listened for during a removal pass.")
        .defaultValue("(already (been )?(bought|sold|purchased|taken|claimed)|(was|has been) (bought|sold|purchased|claimed)|someone (else )?(bought|purchased|took) (it|this|that)|no longer (available|exists|listed|for sale)|(listing|item|auction) (is |was |has been |has )?(gone|expired|removed|unavailable)|not found|does ?n.?t exist)")
        .build()
    );

    private final Setting<Integer> goneRetryMinutes = sgRemover.add(new IntSetting.Builder()
        .name("gone-retry-minutes")
        .description("Minutes to sit out after a listing vanishes mid-pickup, before picking the pass back up.")
        .defaultValue(1)
        .min(1)
        .max(30)
        .sliderMax(10)
        .build()
    );

    private final Setting<Integer> goneRetryMax = sgRemover.add(new IntSetting.Builder()
        .name("gone-retry-max")
        .description("Give up on the removal pass after this many vanished listings in a row, so it cannot pause forever.")
        .defaultValue(3)
        .min(1)
        .max(20)
        .sliderMax(10)
        .build()
    );

    private final Setting<Integer> timingJitter = sgTiming.add(new IntSetting.Builder()
        .name("timing-jitter")
        .description("Randomly varies every delay by up to this percent.")
        .defaultValue(25)
        .min(0)
        .max(60)
        .sliderMax(50)
        .build()
    );

    private final Setting<Integer> clickDelay = sgTiming.add(new IntSetting.Builder()
        .name("click-delay")
        .description("Ticks between each slot click while moving ingots around.")
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

    private final Setting<Integer> slotDelay = sgTiming.add(new IntSetting.Builder()
        .name("slot-delay")
        .description("Ticks between switching hotbar slot and sending /ah sell. At least 1 so the server sees the slot change first.")
        .defaultValue(2)
        .min(1)
        .max(20)
        .sliderMax(10)
        .build()
    );

    private final Setting<Integer> confirmDelay = sgTiming.add(new IntSetting.Builder()
        .name("confirm-delay")
        .description("Ticks before clicking the sell confirm button.")
        .defaultValue(10)
        .min(0)
        .max(100)
        .sliderMax(20)
        .build()
    );

    private final Setting<Integer> confirmTimeout = sgTiming.add(new IntSetting.Builder()
        .name("confirm-timeout")
        .description("Give up waiting for the confirm screen after this many ticks.")
        .defaultValue(60)
        .min(10)
        .max(200)
        .sliderMax(100)
        .build()
    );

    private final Setting<Integer> verifyDelay = sgTiming.add(new IntSetting.Builder()
        .name("verify-delay")
        .description("Ticks after a listing before checking the ingot actually left, so a refund has time to arrive.")
        .defaultValue(15)
        .min(1)
        .max(100)
        .sliderMax(40)
        .build()
    );

    private final Setting<Integer> cycleGap = sgTiming.add(new IntSetting.Builder()
        .name("cycle-gap")
        .description("Ticks between one restock finishing and the next starting. Randomised like every other delay.")
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

    private final Setting<Integer> gapDelay = sgTiming.add(new IntSetting.Builder()
        .name("gap-delay")
        .description("Ticks between one listing finishing and the next starting.")
        .defaultValue(5)
        .min(0)
        .max(40)
        .sliderMax(20)
        .build()
    );

    private enum State {
        IDLE,
        PRICE_SEND, PRICE_WAIT, PRICE_CLOSE,
        CHEST_OPEN, CHEST_WAIT, GRAB_PICKUP, GRAB_PLACE, GRAB_RETURN, CHEST_CLOSE,
        SPREAD_OPEN, SPREAD_PICKUP, SPREAD_PLACE, SPREAD_RETURN, SPREAD_CLOSE,
        SELL_SELECT, SELL_SEND, SELL_CONFIRM, SELL_VERIFY, SELL_GAP,
        PULL_OPEN, PULL, PULL_CLOSE,
        REMOVE_SEND, REMOVE_OPEN_MINE, REMOVE_MINE_WAIT, REMOVE_CLICK, REMOVE_RETRY, REMOVE_CLOSE,
        COOLDOWN
    }

    private final Random random = new Random();

    private State state = State.IDLE;
    private int delayCounter = 0;
    private int waited = 0;
    private long listPrice = 0;
    private long lastAttemptPrice = 0;
    private boolean lastCycleFailed = false;
    private int currentSlot = 0;
    private int listed = 0;
    private int grabbed = 0;
    private int sourceChestSlot = -1;
    private int spreadSourceSlot = -1;
    private int spread = 0;
    private int stalledPulls = 0;
    private boolean handlingMessage = false;
    private boolean pendingRemoval = false;
    private boolean removerTestOnly = false;
    private int removed = 0;
    private int menuIdBefore = -1;
    private int stalledRemovals = 0;
    private int goneRetries = 0;
    private int cyclesSinceClear = 0;
    private ItemStack soldRef = ItemStack.EMPTY;
    private int countBeforeSale = 0;
    private BlockPos chestPos = null;

    public IronAhRestocker() {
        super(GlazedAddon.CATEGORY, "iron-ah-restocker", "Looks at a chest, pulls single ingots out of it and keeps the ah stocked just under the cheapest listing.");
    }

    @Override
    public void onActivate() {
        resetCycle();
        state = State.IDLE;
        delayCounter = 0;
        lastAttemptPrice = 0;
        lastCycleFailed = false;
        handlingMessage = false;
        pendingRemoval = false;
        cyclesSinceClear = 0;
    }

    @Override
    public void onDeactivate() {
        returnSpreadCursor();
        closeAnyMenu();
        state = State.IDLE;
    }

    private void resetCycle() {
        currentSlot = 0;
        listed = 0;
        grabbed = 0;
        waited = 0;
        sourceChestSlot = -1;
        spreadSourceSlot = -1;
        spread = 0;
        stalledPulls = 0;
        soldRef = ItemStack.EMPTY;
        countBeforeSale = 0;
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
            case PRICE_SEND -> tickPriceSend();
            case PRICE_WAIT -> tickPriceWait();
            case PRICE_CLOSE -> tickPriceClose();
            case CHEST_OPEN -> tickChestOpen();
            case CHEST_WAIT -> tickChestWait();
            case GRAB_PICKUP -> tickGrabPickup();
            case GRAB_PLACE -> tickGrabPlace();
            case GRAB_RETURN -> tickGrabReturn();
            case CHEST_CLOSE -> tickChestClose();
            case SPREAD_OPEN -> tickSpreadOpen();
            case SPREAD_PICKUP -> tickSpreadPickup();
            case SPREAD_PLACE -> tickSpreadPlace();
            case SPREAD_RETURN -> tickSpreadReturn();
            case SPREAD_CLOSE -> tickSpreadClose();
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
            case REMOVE_SEND -> tickRemoveSend();
            case REMOVE_OPEN_MINE -> tickRemoveOpenMine();
            case REMOVE_MINE_WAIT -> tickRemoveMineWait();
            case REMOVE_CLICK -> tickRemoveClick();
            case REMOVE_RETRY -> tickRemoveRetry();
            case REMOVE_CLOSE -> tickRemoveClose();
            case COOLDOWN -> {
                resetCycle();
                state = State.IDLE;
            }
        }
    }

    private void tickIdle() {
        if (pendingRemoval) {
            pendingRemoval = false;

            if (removeListings.get()) {
                removed = 0;
                waited = 0;
                stalledRemovals = 0;
                goneRetries = 0;
                state = State.REMOVE_SEND;
                return;
            }
        }

        if (hasAnyItemInInventory()) {
            currentSlot = 0;
            listed = 0;
            grabbed = 0;
            waited = 0;
            sourceChestSlot = -1;
            state = State.PRICE_SEND;
            return;
        }

        BlockPos target = lookedAtChest();

        if (requireChestLook.get() && target == null) {
            delayCounter = jitter(12, 4);
            return;
        }

        chestPos = target;
        currentSlot = 0;
        listed = 0;
        grabbed = 0;
        waited = 0;
        sourceChestSlot = -1;
        state = State.PRICE_SEND;
    }

    private BlockPos lookedAtChest() {
        if (!(mc.hitResult instanceof BlockHitResult hit)) return null;
        if (hit.getType() != HitResult.Type.BLOCK) return null;

        Block block = mc.level.getBlockState(hit.getBlockPos()).getBlock();

        if (block instanceof ChestBlock) return hit.getBlockPos();
        if (allowBarrels.get() && block instanceof BarrelBlock) return hit.getBlockPos();

        return null;
    }

    private void tickPriceSend() {
        mc.getConnection().sendCommand(priceCommand.get().trim());
        waited = 0;
        delayCounter = jitter(screenDelay.get(), 1);
        state = State.PRICE_WAIT;
    }

    private void tickPriceWait() {
        if (mc.player.containerMenu != mc.player.inventoryMenu && mc.player.containerMenu instanceof ChestMenu menu) {
            long cheapest = cheapestPerUnit(menu);

            if (cheapest <= 0) {
                if (notifications.get()) warning("No %s listings found in the menu, backing off.", itemName());
                listPrice = 0;
                state = State.PRICE_CLOSE;
                return;
            }

            long price = cheapest - undercut.get();

            if (lastCycleFailed && lastAttemptPrice > 0) {
                price = Math.min(price, lastAttemptPrice - failUndercut.get());
            }

            if (price < minPrice.get()) {
                if (notifications.get()) error("Cheapest is %d, undercut would land under min-price %d. Stopping.", cheapest, minPrice.get());
                closeAnyMenu();
                toggle();
                return;
            }

            listPrice = price;
            if (notifications.get()) info("Cheapest %s is %d each, listing at %d.", itemName(), cheapest, listPrice);
            state = State.PRICE_CLOSE;
            return;
        }

        if (++waited >= priceTimeout.get()) {
            if (notifications.get()) warning("Listings menu never opened, backing off.");
            endCycleBackoff();
        }
    }

    private void tickPriceClose() {
        closeAnyMenu();
        delayCounter = jitter(screenDelay.get(), 1);

        if (listPrice <= 0) {
            endCycleBackoff();
            return;
        }

        state = hasAnyItemInInventory() ? State.SPREAD_OPEN : State.CHEST_OPEN;
    }

    private long cheapestPerUnit(ChestMenu menu) {
        Pattern pattern;

        try {
            pattern = Pattern.compile(priceRegex.get());
        } catch (Exception e) {
            if (notifications.get()) error("price-regex does not compile: " + e.getMessage());
            return -1;
        }

        int containerSlots = Math.min(menu.getRowCount() * 9, menu.slots.size());
        long best = -1;

        for (int slot = 0; slot < containerSlots; slot++) {
            ItemStack stack = menu.getSlot(slot).getItem();
            if (stack.isEmpty() || !stack.is(item.get())) continue;

            long price = parseListingPrice(stack, pattern);
            if (price <= 0) continue;

            long unit = perUnit.get() ? Math.max(1, price / Math.max(1, stack.getCount())) : price;

            if (firstListingOnly.get()) return unit;

            if (best < 0 || unit < best) best = unit;
        }

        return best;
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
        if (text == null || text.isEmpty()) return -1;

        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) return -1;

        String number = matcher.group(1);
        if (number == null) return -1;

        number = number.replace(",", "");

        double value;
        try {
            value = Double.parseDouble(number);
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
            state = State.GRAB_PICKUP;
            return;
        }

        if (++waited >= priceTimeout.get()) {
            if (notifications.get()) warning("Chest never opened, backing off.");
            endCycleBackoff();
        }
    }

    private ChestMenu openChest() {
        if (mc.player.containerMenu == mc.player.inventoryMenu) return null;
        return mc.player.containerMenu instanceof ChestMenu menu ? menu : null;
    }

    private void tickGrabPickup() {
        ChestMenu menu = openChest();

        if (menu == null) {
            endCycleBackoff();
            return;
        }

        if (!mc.player.containerMenu.getCarried().isEmpty()) {
            state = State.GRAB_PLACE;
            return;
        }

        if (firstEmptyPlayerSlot(menu) < 0) {
            state = State.CHEST_CLOSE;
            return;
        }

        int containerSlots = Math.min(menu.getRowCount() * 9, menu.slots.size());
        int source = -1;

        for (int slot = 0; slot < containerSlots; slot++) {
            ItemStack stack = menu.getSlot(slot).getItem();
            if (!stack.isEmpty() && stack.is(item.get())) {
                source = slot;
                break;
            }
        }

        if (source < 0) {
            if (notifications.get()) {
                info(grabbed > 0 ? "Chest is out of %s, listing the %d taken." : "No %s in the chest.", itemName(), grabbed);
            }
            state = State.CHEST_CLOSE;
            return;
        }

        sourceChestSlot = source;
        mc.gameMode.handleContainerInput(menu.containerId, source, 0, ContainerInput.PICKUP, mc.player);
        delayCounter = jitter(clickDelay.get(), 1);
        state = State.GRAB_PLACE;
    }

    private void tickGrabPlace() {
        ChestMenu menu = openChest();

        if (menu == null) {
            endCycleBackoff();
            return;
        }

        if (mc.player.containerMenu.getCarried().isEmpty()) {
            state = firstEmptyPlayerSlot(menu) < 0 ? State.CHEST_CLOSE : State.GRAB_PICKUP;
            return;
        }

        int target = firstEmptyPlayerSlot(menu);

        if (target < 0) {
            state = State.GRAB_RETURN;
            return;
        }

        mc.gameMode.handleContainerInput(menu.containerId, target, 1, ContainerInput.PICKUP, mc.player);
        grabbed++;
        delayCounter = jitter(clickDelay.get(), 1);
    }

    private void tickGrabReturn() {
        ChestMenu menu = openChest();

        if (menu == null) {
            endCycleBackoff();
            return;
        }

        if (mc.player.containerMenu.getCarried().isEmpty() || sourceChestSlot < 0) {
            state = State.CHEST_CLOSE;
            return;
        }

        mc.gameMode.handleContainerInput(menu.containerId, sourceChestSlot, 0, ContainerInput.PICKUP, mc.player);
        delayCounter = jitter(clickDelay.get(), 1);
        state = State.CHEST_CLOSE;
    }

    private void tickChestClose() {
        closeAnyMenu();
        delayCounter = jitter(screenDelay.get(), 1);

        if (grabbed <= 0 && !hasAnyItemInInventory()) {
            endCycleBackoff();
            return;
        }

        if (notifications.get()) info("Took %d %s, listing at %d each.", grabbed, itemName(), listPrice);

        currentSlot = 0;
        state = State.SPREAD_OPEN;
    }

    private int firstEmptyPlayerSlot(ChestMenu menu) {
        int containerSlots = menu.getRowCount() * 9;

        for (int slot = containerSlots; slot < menu.slots.size(); slot++) {
            if (menu.getSlot(slot).getItem().isEmpty()) return slot;
        }

        return -1;
    }

    private void tickSpreadOpen() {
        if (mc.player.containerMenu != mc.player.inventoryMenu) {
            closeAnyMenu();
            delayCounter = jitter(screenDelay.get(), 1);
            return;
        }

        if (!(mc.screen instanceof InventoryScreen)) {
            mc.setScreen(new InventoryScreen(mc.player));
            delayCounter = jitter(screenDelay.get(), 1);
        }

        spreadSourceSlot = -1;
        state = State.SPREAD_PICKUP;
    }

    private void tickSpreadPickup() {
        if (mc.player.containerMenu != mc.player.inventoryMenu || !(mc.screen instanceof InventoryScreen)) {
            state = State.SPREAD_OPEN;
            return;
        }

        if (!mc.player.inventoryMenu.getCarried().isEmpty()) {
            state = State.SPREAD_PLACE;
            return;
        }

        int source = firstStackedInventorySlot();
        if (source < 0) {
            state = State.SPREAD_CLOSE;
            return;
        }

        spreadSourceSlot = source;
        mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId,
            inventoryMenuSlot(source), 0, ContainerInput.PICKUP, mc.player);
        delayCounter = jitter(clickDelay.get(), 1);
        state = State.SPREAD_PLACE;
    }

    private void tickSpreadPlace() {
        if (mc.player.containerMenu != mc.player.inventoryMenu || !(mc.screen instanceof InventoryScreen)) {
            returnSpreadCursor();
            state = State.SPREAD_OPEN;
            return;
        }

        if (mc.player.inventoryMenu.getCarried().isEmpty()) {
            spreadSourceSlot = -1;
            state = State.SPREAD_CLOSE;
            return;
        }

        int target = firstEmptyInventorySlotExcept(spreadSourceSlot);
        if (target < 0) {
            state = State.SPREAD_RETURN;
            return;
        }

        mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId,
            inventoryMenuSlot(target), 1, ContainerInput.PICKUP, mc.player);
        spread++;
        delayCounter = jitter(clickDelay.get(), 1);
    }

    private void tickSpreadReturn() {
        if (mc.player.containerMenu != mc.player.inventoryMenu) {
            state = State.SPREAD_OPEN;
            return;
        }

        returnSpreadCursor();
        delayCounter = jitter(clickDelay.get(), 1);
        state = State.SPREAD_CLOSE;
    }

    private void tickSpreadClose() {
        returnSpreadCursor();
        if (mc.screen instanceof InventoryScreen screen) screen.onClose();

        currentSlot = 0;
        delayCounter = jitter(screenDelay.get(), 1);
        state = State.SELL_SELECT;
    }

    private void tickSellSelect() {
        if (currentSlot > 8) {
            if (hasSingleInMainInventory()) {
                state = State.PULL_OPEN;
                return;
            }

            if (hasStackedItemInInventory()) {
                if (freeInventorySlots() > 0) {
                    state = State.SPREAD_OPEN;
                } else {
                    if (notifications.get()) warning("Keeping an ingot stack because there is no room to spread it into singles.");
                    endCycleBackoff();
                }
                return;
            }

            if (notifications.get()) info("Listed %d %s at %d each, restocking.", listed, itemName(), listPrice);
            endCycle(false);
            return;
        }

        ItemStack stack = mc.player.getInventory().getItem(currentSlot);

        if (stack.isEmpty() || !stack.is(item.get()) || stack.getCount() != 1) {
            currentSlot++;
            return;
        }

        VersionUtil.setSelectedSlot(mc.player, currentSlot);
        delayCounter = jitter(slotDelay.get(), 1);
        state = State.SELL_SEND;
    }

    private void tickSellSend() {
        ItemStack stack = mc.player.getInventory().getItem(currentSlot);

        if (stack.isEmpty() || !stack.is(item.get()) || stack.getCount() != 1) {
            currentSlot++;
            state = State.SELL_SELECT;
            return;
        }

        soldRef = stack.copy();
        countBeforeSale = countMatching(soldRef);
        lastAttemptPrice = listPrice;

        mc.getConnection().sendCommand("ah sell " + listPrice);
        waited = 0;
        delayCounter = jitter(confirmDelay.get(), 0);
        state = State.SELL_CONFIRM;
    }

    private void tickSellConfirm() {
        if (GlazedSell.isDialogOpen()) {
            if (GlazedSell.clickDialogYes()) {
                delayCounter = jitter(verifyDelay.get(), 1);
                state = State.SELL_VERIFY;
            }
            return;
        }

        AbstractContainerMenu menu = mc.player.containerMenu;

        if (menu instanceof ChestMenu handler && handler.getRowCount() == 3) {
            if (GlazedSell.clickConfirm(handler)) {
                delayCounter = jitter(verifyDelay.get(), 1);
                state = State.SELL_VERIFY;
                return;
            }
        }

        if (++waited >= confirmTimeout.get()) {
            if (notifications.get()) warning("No confirm screen for slot " + currentSlot + ", treating as a failed listing.");
            GlazedSell.close();
            endCycle(true);
        }
    }

    private void tickSellVerify() {
        int after = countMatching(soldRef);

        if (after >= countBeforeSale) {
            if (notifications.get()) warning("Listing came back (%d -> %d), re-pricing lower.", countBeforeSale, after);
            soldRef = ItemStack.EMPTY;
            endCycle(true);
            return;
        }

        listed++;
        soldRef = ItemStack.EMPTY;
        delayCounter = jitter(gapDelay.get(), 0);
        state = State.SELL_GAP;
    }

    private void tickPullOpen() {
        if (mc.player.containerMenu != mc.player.inventoryMenu) {
            closeAnyMenu();
            delayCounter = jitter(screenDelay.get(), 1);
            return;
        }

        if (mc.screen instanceof InventoryScreen) {
            state = State.PULL;
            return;
        }

        mc.setScreen(new InventoryScreen(mc.player));
        delayCounter = jitter(screenDelay.get(), 1);
        state = State.PULL;
    }

    private void tickPull() {
        if (mc.player.containerMenu != mc.player.inventoryMenu) {
            state = State.PULL_OPEN;
            return;
        }

        if (!(mc.screen instanceof InventoryScreen)) {
            state = State.PULL_OPEN;
            return;
        }

        int target = firstEmptyHotbarSlot();
        int source = firstSingleMainInventorySlot();

        if (target < 0 || source < 0) {
            state = State.PULL_CLOSE;
            return;
        }

        ItemStack before = mc.player.getInventory().getItem(source).copy();

        mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, source, target, ContainerInput.SWAP, mc.player);

        if (ItemStack.matches(before, mc.player.getInventory().getItem(source))) {
            if (++stalledPulls >= 3) {
                if (notifications.get()) warning("Could not move ingots up into the hotbar, restocking instead.");
                state = State.PULL_CLOSE;
                return;
            }
        } else {
            stalledPulls = 0;
        }

        delayCounter = jitter(clickDelay.get(), 1);
    }

    private void tickPullClose() {
        if (mc.screen instanceof InventoryScreen screen) screen.onClose();

        stalledPulls = 0;
        currentSlot = 0;
        delayCounter = jitter(screenDelay.get(), 1);
        state = State.SELL_SELECT;
    }

    private int firstEmptyHotbarSlot() {
        for (int slot = 0; slot <= 8; slot++) {
            if (mc.player.getInventory().getItem(slot).isEmpty()) return slot;
        }
        return -1;
    }

    private int firstSingleMainInventorySlot() {
        for (int slot = 9; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.is(item.get()) && stack.getCount() == 1) return slot;
        }
        return -1;
    }

    private boolean hasSingleInMainInventory() {
        return firstSingleMainInventorySlot() >= 0;
    }

    private int firstStackedInventorySlot() {
        int size = Math.min(36, mc.player.getInventory().getContainerSize());

        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.is(item.get()) && stack.getCount() > 1) return slot;
        }

        return -1;
    }

    private boolean hasStackedItemInInventory() {
        return firstStackedInventorySlot() >= 0;
    }

    private boolean hasAnyItemInInventory() {
        int size = Math.min(36, mc.player.getInventory().getContainerSize());

        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.is(item.get())) return true;
        }

        return false;
    }

    private int freeInventorySlots() {
        int free = 0;
        int size = Math.min(36, mc.player.getInventory().getContainerSize());

        for (int slot = 0; slot < size; slot++) {
            if (mc.player.getInventory().getItem(slot).isEmpty()) free++;
        }

        return free;
    }

    private int firstEmptyInventorySlotExcept(int excluded) {
        int size = Math.min(36, mc.player.getInventory().getContainerSize());

        for (int slot = 0; slot < size; slot++) {
            if (slot != excluded && mc.player.getInventory().getItem(slot).isEmpty()) return slot;
        }

        return -1;
    }

    private int inventoryMenuSlot(int inventorySlot) {
        if (inventorySlot >= 0 && inventorySlot <= 8) {
            return InventoryMenu.USE_ROW_SLOT_START + inventorySlot;
        }

        return inventorySlot;
    }

    private void returnSpreadCursor() {
        if (mc.player == null || mc.gameMode == null) return;
        if (mc.player.containerMenu != mc.player.inventoryMenu) return;
        if (mc.player.inventoryMenu.getCarried().isEmpty()) {
            spreadSourceSlot = -1;
            return;
        }

        int target = spreadSourceSlot;
        if (target < 0 || target >= 36) target = firstEmptyInventorySlotExcept(-1);

        if (target >= 0) {
            ItemStack destination = mc.player.getInventory().getItem(target);

            if (destination.isEmpty() || destination.is(item.get())) {
                mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId,
                    inventoryMenuSlot(target), 0, ContainerInput.PICKUP, mc.player);
            }
        }

        spreadSourceSlot = -1;
    }

    public void startRemoverTest() {
        removerTestOnly = true;
        pendingRemoval = false;
        removed = 0;
        waited = 0;
        stalledRemovals = 0;
        goneRetries = 0;
        delayCounter = 0;
        state = State.REMOVE_SEND;
    }

    private void tickRemoveSend() {
        closeAnyMenu();
        mc.getConnection().sendCommand(priceCommand.get().trim());
        menuIdBefore = mc.player.inventoryMenu.containerId;
        waited = 0;
        delayCounter = jitter(screenDelay.get(), 1);
        state = State.REMOVE_OPEN_MINE;
    }

    private void tickRemoveOpenMine() {
        ChestMenu menu = openChest();

        if (menu == null) {
            if (++waited >= priceTimeout.get()) {
                if (notifications.get()) warning("Ah menu never opened, skipping the removal pass.");
                finishRemoval();
            }
            return;
        }

        int containerSlots = Math.min(menu.getRowCount() * 9, menu.slots.size());
        int button = -1;

        for (int slot = 0; slot < containerSlots; slot++) {
            if (menu.getSlot(slot).getItem().is(listingsButton.get())) {
                button = slot;
                break;
            }
        }

        if (button < 0) {
            if (++waited >= priceTimeout.get()) {
                if (notifications.get()) warning("No %s button in the ah menu, skipping the removal pass.", listingsButton.get().getDefaultInstance().getHoverName().getString());
                finishRemoval();
            }
            return;
        }

        menuIdBefore = menu.containerId;
        mc.gameMode.handleContainerInput(menu.containerId, button, 0, ContainerInput.PICKUP, mc.player);
        waited = 0;
        delayCounter = jitter(screenDelay.get(), 1);
        state = State.REMOVE_MINE_WAIT;
    }

    private void tickRemoveMineWait() {
        ChestMenu menu = openChest();

        if (menu != null && menu.containerId != menuIdBefore) {
            waited = 0;
            state = State.REMOVE_CLICK;
            return;
        }

        if (++waited >= priceTimeout.get()) {
            if (menu != null) {
                waited = 0;
                state = State.REMOVE_CLICK;
                return;
            }

            if (notifications.get()) warning("Your listings menu never opened, skipping the removal pass.");
            finishRemoval();
        }
    }

    private void tickRemoveClick() {
        ChestMenu menu = openChest();

        if (menu == null) {
            finishRemoval();
            return;
        }

        if (removed >= removeMax.get()) {
            if (notifications.get()) info("Hit remove-max (%d), leaving the rest.", removeMax.get());
            finishRemoval();
            return;
        }

        int containerSlots = Math.min(menu.getRowCount() * 9, menu.slots.size());
        int target = -1;

        for (int slot = 0; slot < containerSlots; slot++) {
            if (menu.getSlot(slot).getItem().is(item.get())) {
                target = slot;
                break;
            }
        }

        if (target < 0) {
            finishRemoval();
            return;
        }

        ItemStack before = menu.getSlot(target).getItem().copy();
        mc.gameMode.handleContainerInput(menu.containerId, target, 0, ContainerInput.PICKUP, mc.player);

        if (ItemStack.matches(before, menu.getSlot(target).getItem())) {
            if (++stalledRemovals >= 6) {
                if (notifications.get()) warning("Listings are not clearing, stopping the removal pass.");
                finishRemoval();
                return;
            }
        } else {
            stalledRemovals = 0;
            removed++;
        }

        delayCounter = jitter(removeDelay.get(), 2);
    }

    private void startRemoveRetry() {
        closeAnyMenu();

        if (++goneRetries > goneRetryMax.get()) {
            if (notifications.get()) warning("Pickup keeps missing, leaving the rest for later.");
            goneRetries = 0;
            delayCounter = jitter(screenDelay.get(), 1);
            state = State.REMOVE_CLOSE;
            return;
        }

        waited = 0;
        stalledRemovals = 0;
        delayCounter = jitter(goneRetryMinutes.get() * 60 * 20, 20 * 10);
        state = State.REMOVE_RETRY;

        if (notifications.get()) info("Pausing about %d minute(s), then trying the pickup again.", goneRetryMinutes.get());
    }

    private void tickRemoveRetry() {
        state = State.REMOVE_SEND;
    }

    private void tickRemoveClose() {
        closeAnyMenu();
        goneRetries = 0;
        cyclesSinceClear = 0;
        delayCounter = jitter(screenDelay.get(), 1);

        if (removerTestOnly) {
            removerTestOnly = false;
            if (notifications.get()) info("Remover test done, pulled back %d listing(s).", removed);
            toggle();
            return;
        }

        if (notifications.get()) info("Pulled back %d listing(s), carrying on.", removed);
        state = State.IDLE;
    }

    private void finishRemoval() {
        state = State.REMOVE_CLOSE;
    }

    private void endCycle(boolean failed) {
        lastCycleFailed = failed;
        cyclesSinceClear++;

        if (clearDue()) pendingRemoval = true;

        closeAnyMenu();
        delayCounter = jitter(cycleGap.get(), 5);
        state = State.COOLDOWN;
    }

    private boolean clearDue() {
        if (!removeListings.get()) return false;

        return switch (clearWhen.get()) {
            case OnlyWhenFull -> false;
            case EveryCycle -> true;
            case EveryNCycles -> cyclesSinceClear >= clearEveryCycles.get();
        };
    }

    private void endCycleBackoff() {
        closeAnyMenu();
        delayCounter = jitter(idleBackoff.get(), 40);
        state = State.COOLDOWN;
    }

    private void startLimitCooldown() {
        lastCycleFailed = true;
        pendingRemoval = true;
        closeAnyMenu();
        delayCounter = jitter(cycleCooldownSeconds.get() * 20, 20 * 10);
        state = State.COOLDOWN;

        if (notifications.get()) info("Parking for about %d seconds.", cycleCooldownSeconds.get());
    }

    private void closeAnyMenu() {
        if (mc.player != null && mc.player.containerMenu != mc.player.inventoryMenu) mc.player.closeContainer();
        if (mc.screen != null) mc.setScreen(null);
    }

    private int countMatching(ItemStack ref) {
        if (ref.isEmpty()) return 0;

        int total = 0;
        int size = mc.player.getInventory().getContainerSize();

        for (int slot = 0; slot < 36 && slot < size; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, ref)) total += stack.getCount();
        }

        return total;
    }

    private String itemName() {
        return item.get().getDefaultInstance().getHoverName().getString();
    }

    private int jitter(int ticks, int floor) {
        int pct = timingJitter.get();
        if (pct <= 0) return Math.max(floor, ticks);

        double factor = 1.0 + (random.nextDouble() * 2.0 - 1.0) * (pct / 100.0);
        return Math.max(floor, (int) Math.round(ticks * factor));
    }

    @EventHandler
    private void onChatMessage(ReceiveMessageEvent event) {
        if (!isActive()) return;

        if (state == State.COOLDOWN) return;

        if (handlingMessage) return;

        String msg = event.getMessage().getString();
        if (msg == null || msg.isEmpty()) return;

        if (msg.contains("[Meteor]")) return;

        if (inRemovalPickup() && matchesGoneMessage(msg)) {
            handlingMessage = true;
            try {
                startRemoveRetry();
            } finally {
                handlingMessage = false;
            }
            return;
        }

        if (!matchesLimitMessage(msg)) return;

        handlingMessage = true;
        try {
            startLimitCooldown();
        } finally {
            handlingMessage = false;
        }
    }

    private boolean inRemovalPickup() {
        return state == State.REMOVE_SEND || state == State.REMOVE_OPEN_MINE
            || state == State.REMOVE_MINE_WAIT || state == State.REMOVE_CLICK;
    }

    private boolean matchesGoneMessage(String msg) {
        try {
            return Pattern.compile(goneRegex.get(), Pattern.CASE_INSENSITIVE).matcher(msg).find();
        } catch (Exception e) {
            String lower = msg.toLowerCase(Locale.ROOT);
            return lower.contains("already") || lower.contains("no longer") || lower.contains("not found");
        }
    }

    public enum ClearWhen {
        OnlyWhenFull,
        EveryCycle,
        EveryNCycles
    }

    private boolean matchesLimitMessage(String msg) {
        try {
            return Pattern.compile(limitRegex.get(), Pattern.CASE_INSENSITIVE).matcher(msg).find();
        } catch (Exception e) {
            String lower = msg.toLowerCase(Locale.ROOT);
            return lower.contains("too many") || lower.contains("limit");
        }
    }
}
