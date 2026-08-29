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

public class AhShieldSeller extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPrice = settings.createGroup("Price lookup");
    private final SettingGroup sgCollect = settings.createGroup("Collector");
    private final SettingGroup sgTiming = settings.createGroup("Timing");

    private final Setting<Item> item = sgGeneral.add(new ItemSetting.Builder()
        .name("item")
        .description("What to sell. Shields by default.")
        .defaultValue(Items.SHIELD)
        .build()
    );

    private final Setting<Integer> undercut = sgGeneral.add(new IntSetting.Builder()
        .name("undercut")
        .description("List this far below the cheapest listing on the ah.")
        .defaultValue(100)
        .min(1)
        .max(100000)
        .sliderMax(1000)
        .build()
    );

    private final Setting<Integer> failUndercut = sgGeneral.add(new IntSetting.Builder()
        .name("undercut-after-fail")
        .description("After a listing bounces back, go at least this much below the price that failed.")
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

    private final Setting<String> limitRegex = sgGeneral.add(new StringSetting.Builder()
        .name("limit-message")
        .description("Case insensitive regex for the server telling you the ah is full. Matching it starts the collect-back run.")
        .defaultValue("(sold too many|too many (items|listed|listings)|listing limit|sell limit|no (more )?(free )?(ah |auction )?slots|max(imum)? listings|reached .{0,20}limit|can only (have|list|sell))")
        .build()
    );

    private final Setting<Boolean> requireChestLook = sgGeneral.add(new BoolSetting.Builder()
        .name("require-chest-look")
        .description("Only start a batch while your crosshair is on a chest. That chest is where the shields come from.")
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
        .description("Command run to open the shield listings. No leading slash.")
        .defaultValue("ah shield")
        .build()
    );

    private final Setting<String> priceRegex = sgPrice.add(new StringSetting.Builder()
        .name("price-regex")
        .description("Regex matched against the listing's name and lore. Group 1 is the number, optional group 2 is a K/M/B suffix.")
        .defaultValue("([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([KkMmBb])?")
        .build()
    );

    private final Setting<Boolean> firstListingOnly = sgPrice.add(new BoolSetting.Builder()
        .name("read-first-listing")
        .description("Read the price off the first listing, which is the cheapest when the ah is sorted by price.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> priceTimeout = sgPrice.add(new IntSetting.Builder()
        .name("price-timeout")
        .description("Ticks to wait for a menu before giving up on this batch.")
        .defaultValue(80)
        .min(20)
        .max(400)
        .sliderMax(200)
        .build()
    );

    private final Setting<Boolean> collectListings = sgCollect.add(new BoolSetting.Builder()
        .name("collect-listings")
        .description("Take unsold shields back off the ah. Off means it never clears, it just waits out the timer and lists lower.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ClearWhen> collectWhen = sgCollect.add(new EnumSetting.Builder<ClearWhen>()
        .name("collect-when")
        .description("When the collect run happens. OnlyWhenFull is the old behaviour: only once a shield bounces back or the server says the ah is full.")
        .defaultValue(ClearWhen.OnlyWhenFull)
        .build()
    );

    private final Setting<Integer> collectEveryCycles = sgCollect.add(new IntSetting.Builder()
        .name("collect-every-cycles")
        .description("How many inventories to sell between collect runs. Only used when collect-when is EveryNCycles.")
        .defaultValue(3)
        .min(1)
        .max(50)
        .sliderMax(20)
        .build()
    );

    private final Setting<Integer> collectWaitMinutes = sgCollect.add(new IntSetting.Builder()
        .name("collect-wait-minutes")
        .description("Minutes to wait after a listing bounces before going to collect the unsold shields back.")
        .defaultValue(1)
        .min(1)
        .max(60)
        .sliderMax(15)
        .build()
    );

    private final Setting<Item> listingsButton = sgCollect.add(new ItemSetting.Builder()
        .name("your-listings-button")
        .description("The item in the ah menu that opens your own listings. Usually the chest.")
        .defaultValue(Items.CHEST)
        .build()
    );

    private final Setting<Integer> collectDelay = sgCollect.add(new IntSetting.Builder()
        .name("collect-delay")
        .description("Ticks between each shield clicked back out of the ah. Slower than the other clicks on purpose.")
        .defaultValue(12)
        .min(2)
        .max(60)
        .sliderMax(40)
        .build()
    );

    private final Setting<Integer> collectMax = sgCollect.add(new IntSetting.Builder()
        .name("collect-max")
        .description("Stop after collecting this many in one pass.")
        .defaultValue(64)
        .min(1)
        .max(500)
        .sliderMax(128)
        .build()
    );

    private final Setting<String> goneRegex = sgCollect.add(new StringSetting.Builder()
        .name("gone-message")
        .description("Case insensitive regex for the server saying a shield you tried to take back is not there any more. Only listened for during a collect run.")
        .defaultValue("(already (been )?(bought|sold|purchased|taken|claimed)|(was|has been) (bought|sold|purchased|claimed)|someone (else )?(bought|purchased|took) (it|this|that)|no longer (available|exists|listed|for sale)|(listing|item|auction) (is |was |has been |has )?(gone|expired|removed|unavailable)|not found|does ?n.?t exist)")
        .build()
    );

    private final Setting<Integer> goneRetryMinutes = sgCollect.add(new IntSetting.Builder()
        .name("gone-retry-minutes")
        .description("Minutes to sit out after a shield vanishes mid-pickup, before picking the collect run back up.")
        .defaultValue(1)
        .min(1)
        .max(30)
        .sliderMax(10)
        .build()
    );

    private final Setting<Integer> goneRetryMax = sgCollect.add(new IntSetting.Builder()
        .name("gone-retry-max")
        .description("Give up on the collect run after this many vanished shields in a row, so it cannot pause forever.")
        .defaultValue(3)
        .min(1)
        .max(20)
        .sliderMax(10)
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

    private final Setting<Integer> clickDelay = sgTiming.add(new IntSetting.Builder()
        .name("click-delay")
        .description("Ticks between slot clicks while moving shields around.")
        .defaultValue(4)
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
        .description("Ticks after a listing before checking the shield actually left, so a bounce has time to arrive.")
        .defaultValue(15)
        .min(1)
        .max(100)
        .sliderMax(40)
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

    private final Setting<Integer> cycleGap = sgTiming.add(new IntSetting.Builder()
        .name("cycle-gap")
        .description("Ticks between one batch finishing and the next starting.")
        .defaultValue(40)
        .min(5)
        .max(400)
        .sliderMax(200)
        .build()
    );

    private final Setting<Integer> idleBackoff = sgTiming.add(new IntSetting.Builder()
        .name("idle-backoff")
        .description("Ticks to wait before retrying when a batch did no work, so an empty chest cannot turn into command spam.")
        .defaultValue(400)
        .min(40)
        .max(6000)
        .sliderMax(1200)
        .build()
    );

    private enum State {
        IDLE,
        CHEST_OPEN, CHEST_WAIT, GRAB, CHEST_CLOSE,
        PRICE_SEND, PRICE_WAIT, PRICE_CLOSE,
        SELL_SELECT, SELL_SEND, SELL_CONFIRM, SELL_VERIFY, SELL_GAP,
        PULL_OPEN, PULL, PULL_CLOSE,
        COLLECT_WAIT, COLLECT_SEND, COLLECT_OPEN_MINE, COLLECT_MINE_WAIT, COLLECT_CLICK, COLLECT_RETRY, COLLECT_CLOSE,
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
    private int collected = 0;
    private int stalled = 0;
    private int goneRetries = 0;
    private int cyclesSinceCollect = 0;
    private boolean pendingCollect = false;
    private int menuIdBefore = -1;
    private boolean handlingMessage = false;
    private int countBeforeSale = 0;
    private BlockPos chestPos = null;

    public AhShieldSeller() {
        super(GlazedAddon.CATEGORY, "ah-shield-seller", "Sells chests full of shields on the ah, undercutting the cheapest listing and collecting the unsold ones back.");
    }

    @Override
    public void onActivate() {
        resetBatch();
        state = State.IDLE;
        delayCounter = 0;
        lastAttemptPrice = 0;
        lastCycleFailed = false;
        handlingMessage = false;
        pendingCollect = false;
        cyclesSinceCollect = 0;
    }

    @Override
    public void onDeactivate() {
        closeAnyMenu();
        state = State.IDLE;
    }

    private void resetBatch() {
        currentSlot = 0;
        listed = 0;
        grabbed = 0;
        collected = 0;
        waited = 0;
        stalled = 0;
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
            case CHEST_OPEN -> tickChestOpen();
            case CHEST_WAIT -> tickChestWait();
            case GRAB -> tickGrab();
            case CHEST_CLOSE -> tickChestClose();
            case PRICE_SEND -> tickPriceSend();
            case PRICE_WAIT -> tickPriceWait();
            case PRICE_CLOSE -> tickPriceClose();
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
            case COLLECT_WAIT -> {
                state = State.COLLECT_SEND;
            }
            case COLLECT_SEND -> tickCollectSend();
            case COLLECT_OPEN_MINE -> tickCollectOpenMine();
            case COLLECT_MINE_WAIT -> tickCollectMineWait();
            case COLLECT_CLICK -> tickCollectClick();
            case COLLECT_RETRY -> tickCollectRetry();
            case COLLECT_CLOSE -> tickCollectClose();
            case COOLDOWN -> {
                resetBatch();
                state = State.IDLE;
            }
        }
    }

    private void tickIdle() {
        if (pendingCollect) {
            pendingCollect = false;

            if (collectListings.get()) {
                collected = 0;
                waited = 0;
                stalled = 0;
                goneRetries = 0;
                state = State.COLLECT_SEND;
                return;
            }
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
                endBatchBackoff();
                return;
            }
            target = chestPos;
        }

        if (target == null || !(mc.hitResult instanceof BlockHitResult hit)) {
            endBatchBackoff();
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

        if (++waited >= priceTimeout.get()) {
            if (notifications.get()) warning("Chest never opened, backing off.");
            endBatchBackoff();
        }
    }

    private ChestMenu openChest() {
        if (mc.player.containerMenu == mc.player.inventoryMenu) return null;
        return mc.player.containerMenu instanceof ChestMenu menu ? menu : null;
    }

    private void tickGrab() {
        ChestMenu menu = openChest();

        if (menu == null) {
            endBatchBackoff();
            return;
        }

        if (firstEmptyPlayerSlot(menu) < 0) {
            state = State.CHEST_CLOSE;
            return;
        }

        int containerSlots = Math.min(menu.getRowCount() * 9, menu.slots.size());
        int source = -1;

        for (int slot = 0; slot < containerSlots; slot++) {
            if (menu.getSlot(slot).getItem().is(item.get())) {
                source = slot;
                break;
            }
        }

        if (source < 0) {
            if (notifications.get() && grabbed == 0) info("No %s in the chest.", itemName());
            state = State.CHEST_CLOSE;
            return;
        }

        ItemStack before = menu.getSlot(source).getItem().copy();
        mc.gameMode.handleContainerInput(menu.containerId, source, 0, ContainerInput.QUICK_MOVE, mc.player);

        if (ItemStack.matches(before, menu.getSlot(source).getItem())) {
            if (++stalled >= 4) {
                if (notifications.get()) warning("Shields are not moving out of the chest, stopping the grab.");
                state = State.CHEST_CLOSE;
                return;
            }
        } else {
            stalled = 0;
            grabbed++;
        }

        delayCounter = jitter(clickDelay.get(), 1);
    }

    private void tickChestClose() {
        closeAnyMenu();
        delayCounter = jitter(screenDelay.get(), 1);

        if (countInInventory() <= 0) {
            endBatchBackoff();
            return;
        }

        if (notifications.get()) info("Took %d %s, checking the price.", grabbed, itemName());
        state = State.PRICE_SEND;
    }

    private int firstEmptyPlayerSlot(ChestMenu menu) {
        int containerSlots = menu.getRowCount() * 9;

        for (int slot = containerSlots; slot < menu.slots.size(); slot++) {
            if (menu.getSlot(slot).getItem().isEmpty()) return slot;
        }

        return -1;
    }

    private void tickPriceSend() {
        mc.getConnection().sendCommand(priceCommand.get().trim());
        waited = 0;
        delayCounter = jitter(screenDelay.get(), 1);
        state = State.PRICE_WAIT;
    }

    private void tickPriceWait() {
        ChestMenu menu = openChest();

        if (menu != null) {
            long cheapest = cheapestListing(menu);

            if (cheapest <= 0) {
                if (notifications.get()) warning("No %s listings found, backing off.", itemName());
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
            if (notifications.get()) info("Cheapest %s is %d, listing at %d.", itemName(), cheapest, listPrice);
            state = State.PRICE_CLOSE;
            return;
        }

        if (++waited >= priceTimeout.get()) {
            if (notifications.get()) warning("Listings menu never opened, backing off.");
            endBatchBackoff();
        }
    }

    private void tickPriceClose() {
        closeAnyMenu();
        delayCounter = jitter(screenDelay.get(), 1);

        if (listPrice <= 0) {
            endBatchBackoff();
            return;
        }

        currentSlot = 0;
        state = State.SELL_SELECT;
    }

    private long cheapestListing(ChestMenu menu) {
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

            if (firstListingOnly.get()) return price;
            if (best < 0 || price < best) best = price;
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

    private void tickSellSelect() {
        if (currentSlot > 8) {
            if (hasItemInMainInventory()) {
                state = State.PULL_OPEN;
                return;
            }

            if (notifications.get()) info("Listed %d %s at %d, going back for more.", listed, itemName(), listPrice);
            endBatch(false);
            return;
        }

        ItemStack stack = mc.player.getInventory().getItem(currentSlot);

        if (stack.isEmpty() || !stack.is(item.get())) {
            currentSlot++;
            return;
        }

        VersionUtil.setSelectedSlot(mc.player, currentSlot);
        delayCounter = jitter(slotDelay.get(), 1);
        state = State.SELL_SEND;
    }

    private void tickSellSend() {
        ItemStack stack = mc.player.getInventory().getItem(currentSlot);

        if (stack.isEmpty() || !stack.is(item.get())) {
            currentSlot++;
            state = State.SELL_SELECT;
            return;
        }

        countBeforeSale = countInInventory();
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
            if (notifications.get()) warning("No confirm screen for slot " + currentSlot + ", treating it as a bounce.");
            GlazedSell.close();
            startCollectRun();
        }
    }

    private void tickSellVerify() {
        if (countInInventory() >= countBeforeSale) {
            if (notifications.get()) info("A shield came back, the ah is full. Waiting %d minute(s) before collecting.", collectWaitMinutes.get());
            startCollectRun();
            return;
        }

        listed++;
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
        int source = firstMainInventorySlot();

        if (target < 0 || source < 0) {
            state = State.PULL_CLOSE;
            return;
        }

        ItemStack before = mc.player.getInventory().getItem(source).copy();
        mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, source, target, ContainerInput.SWAP, mc.player);

        if (ItemStack.matches(before, mc.player.getInventory().getItem(source))) {
            if (++stalled >= 3) {
                if (notifications.get()) warning("Could not move shields up into the hotbar.");
                state = State.PULL_CLOSE;
                return;
            }
        } else {
            stalled = 0;
        }

        delayCounter = jitter(clickDelay.get(), 1);
    }

    private void tickPullClose() {
        if (mc.screen instanceof InventoryScreen screen) screen.onClose();

        stalled = 0;
        currentSlot = 0;
        delayCounter = jitter(screenDelay.get(), 1);
        state = State.SELL_SELECT;
    }

    private void startCollectRun() {
        lastCycleFailed = true;
        closeAnyMenu();
        collected = 0;
        waited = 0;
        stalled = 0;
        goneRetries = 0;

        if (!collectListings.get()) {
            delayCounter = jitter(collectWaitMinutes.get() * 60 * 20, 20 * 20);
            state = State.COOLDOWN;
            return;
        }

        delayCounter = jitter(collectWaitMinutes.get() * 60 * 20, 20 * 20);
        state = State.COLLECT_WAIT;
    }

    private boolean collectDue() {
        if (!collectListings.get()) return false;

        return switch (collectWhen.get()) {
            case OnlyWhenFull -> false;
            case EveryCycle -> true;
            case EveryNCycles -> cyclesSinceCollect >= collectEveryCycles.get();
        };
    }

    private void tickCollectSend() {
        closeAnyMenu();
        mc.getConnection().sendCommand(priceCommand.get().trim());
        menuIdBefore = mc.player.inventoryMenu.containerId;
        waited = 0;
        delayCounter = jitter(screenDelay.get(), 1);
        state = State.COLLECT_OPEN_MINE;
    }

    private void tickCollectOpenMine() {
        ChestMenu menu = openChest();

        if (menu == null) {
            if (++waited >= priceTimeout.get()) {
                if (notifications.get()) warning("Ah menu never opened, skipping the collect run.");
                state = State.COLLECT_CLOSE;
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
                if (notifications.get()) warning("No listings button in the ah menu, skipping the collect run.");
                state = State.COLLECT_CLOSE;
            }
            return;
        }

        menuIdBefore = menu.containerId;
        mc.gameMode.handleContainerInput(menu.containerId, button, 0, ContainerInput.PICKUP, mc.player);
        waited = 0;
        delayCounter = jitter(screenDelay.get(), 1);
        state = State.COLLECT_MINE_WAIT;
    }

    private void tickCollectMineWait() {
        ChestMenu menu = openChest();

        if (menu != null && menu.containerId != menuIdBefore) {
            waited = 0;
            state = State.COLLECT_CLICK;
            return;
        }

        if (++waited >= priceTimeout.get()) {
            if (menu != null) {
                waited = 0;
                state = State.COLLECT_CLICK;
                return;
            }

            if (notifications.get()) warning("Your listings menu never opened, skipping the collect run.");
            state = State.COLLECT_CLOSE;
        }
    }

    private void tickCollectClick() {
        ChestMenu menu = openChest();

        if (menu == null) {
            state = State.COLLECT_CLOSE;
            return;
        }

        if (collected >= collectMax.get()) {
            if (notifications.get()) info("Hit collect-max (%d), leaving the rest.", collectMax.get());
            state = State.COLLECT_CLOSE;
            return;
        }

        if (firstEmptyPlayerSlot(menu) < 0) {
            if (notifications.get()) info("Inventory is full, stopping the collect run at %d.", collected);
            state = State.COLLECT_CLOSE;
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
            state = State.COLLECT_CLOSE;
            return;
        }

        ItemStack before = menu.getSlot(target).getItem().copy();
        mc.gameMode.handleContainerInput(menu.containerId, target, 0, ContainerInput.PICKUP, mc.player);

        if (ItemStack.matches(before, menu.getSlot(target).getItem())) {
            if (++stalled >= 6) {
                if (notifications.get()) warning("Listings are not clearing, stopping the collect run.");
                state = State.COLLECT_CLOSE;
                return;
            }
        } else {
            stalled = 0;
            collected++;
        }

        delayCounter = jitter(collectDelay.get(), 2);
    }

    private void startCollectRetry() {
        closeAnyMenu();

        if (++goneRetries > goneRetryMax.get()) {
            if (notifications.get()) warning("Pickup keeps missing, leaving the rest for later.");
            goneRetries = 0;
            delayCounter = jitter(screenDelay.get(), 1);
            state = State.COLLECT_CLOSE;
            return;
        }

        waited = 0;
        stalled = 0;
        delayCounter = jitter(goneRetryMinutes.get() * 60 * 20, 20 * 10);
        state = State.COLLECT_RETRY;

        if (notifications.get()) info("Pausing about %d minute(s), then trying the pickup again.", goneRetryMinutes.get());
    }

    private void tickCollectRetry() {
        state = State.COLLECT_SEND;
    }

    private void tickCollectClose() {
        closeAnyMenu();
        stalled = 0;
        goneRetries = 0;
        cyclesSinceCollect = 0;

        if (notifications.get()) info("Collected %d %s back, refilling.", collected, itemName());

        delayCounter = jitter(cycleGap.get(), 5);
        state = State.COOLDOWN;
    }

    private void endBatch(boolean failed) {
        lastCycleFailed = failed;
        cyclesSinceCollect++;

        if (collectDue()) pendingCollect = true;

        closeAnyMenu();
        delayCounter = jitter(cycleGap.get(), 5);
        state = State.COOLDOWN;
    }

    private void endBatchBackoff() {
        closeAnyMenu();
        delayCounter = jitter(idleBackoff.get(), 40);
        state = State.COOLDOWN;
    }

    private void closeAnyMenu() {
        if (mc.player != null && mc.player.containerMenu != mc.player.inventoryMenu) mc.player.closeContainer();
        if (mc.screen != null) mc.setScreen(null);
    }

    private int countInInventory() {
        int total = 0;
        int size = mc.player.getInventory().getContainerSize();

        for (int slot = 0; slot < 36 && slot < size; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.is(item.get())) total += stack.getCount();
        }

        return total;
    }

    private int firstEmptyHotbarSlot() {
        for (int slot = 0; slot <= 8; slot++) {
            if (mc.player.getInventory().getItem(slot).isEmpty()) return slot;
        }
        return -1;
    }

    private int firstMainInventorySlot() {
        for (int slot = 9; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.is(item.get())) return slot;
        }
        return -1;
    }

    private boolean hasItemInMainInventory() {
        return firstMainInventorySlot() >= 0;
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

        if (handlingMessage) return;

        String msg = event.getMessage().getString();
        if (msg == null || msg.isEmpty()) return;
        if (msg.contains("[Meteor]")) return;

        if (inCollectPickup() && matchesGoneMessage(msg)) {
            handlingMessage = true;
            try {
                startCollectRetry();
            } finally {
                handlingMessage = false;
            }
            return;
        }

        if (inCollectRun()) return;

        if (!matchesLimitMessage(msg)) return;

        handlingMessage = true;
        try {
            if (notifications.get()) info("Ah is full, waiting %d minute(s) before collecting.", collectWaitMinutes.get());
            startCollectRun();
        } finally {
            handlingMessage = false;
        }
    }

    public enum ClearWhen {
        OnlyWhenFull,
        EveryCycle,
        EveryNCycles
    }

    private boolean inCollectPickup() {
        return state == State.COLLECT_SEND || state == State.COLLECT_OPEN_MINE
            || state == State.COLLECT_MINE_WAIT || state == State.COLLECT_CLICK;
    }

    private boolean inCollectRun() {
        return state == State.COLLECT_WAIT || state == State.COLLECT_RETRY
            || state == State.COLLECT_CLOSE || inCollectPickup();
    }

    private boolean matchesGoneMessage(String msg) {
        try {
            return Pattern.compile(goneRegex.get(), Pattern.CASE_INSENSITIVE).matcher(msg).find();
        } catch (Exception e) {
            String lower = msg.toLowerCase(Locale.ROOT);
            return lower.contains("already") || lower.contains("no longer") || lower.contains("not found");
        }
    }

    private boolean matchesLimitMessage(String msg) {
        try {
            return Pattern.compile(limitRegex.get(), Pattern.CASE_INSENSITIVE).matcher(msg).find();
        } catch (Exception e) {
            String lower = msg.toLowerCase(Locale.ROOT);
            return lower.contains("too many") || lower.contains("limit") || lower.contains("slots");
        }
    }
}
