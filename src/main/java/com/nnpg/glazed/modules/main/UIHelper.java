package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import com.nnpg.glazed.settings.RandomBetweenIntSetting;
import com.nnpg.glazed.settings.TextDisplaySetting;
import com.nnpg.glazed.utils.RandomBetweenInt;
import com.nnpg.glazed.utils.glazed.StringUtils;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

public class UIHelper extends Module {

    public UIHelper() {
        super(GlazedAddon.CATEGORY,
                "ui-helper",
                "Helps perform various UI tasks automatically."
        );
    }

    private final SettingGroup sgAutoConfirm = settings.createGroup("AutoConfirm");

    private final Setting<String> acDescription = sgAutoConfirm.add(new TextDisplaySetting.Builder()
            .name("")
            .description("")
            .defaultValue("Automatically confirms various confirm pop-up dialogs.")
            .build()
    );

    private final Setting<Boolean> enableAutoConfirm = sgAutoConfirm.add(new BoolSetting.Builder()
            .name("enable-auto-confirm")
            .description("Automatically confirms various actions in the UI.")
            .defaultValue(false)
            .build()
    );

    // Progression: "AUCTION (Page {number})" -> "CONFIRM PURCHASE"
    private final Setting<Boolean> acAHBuy = sgAutoConfirm.add(new BoolSetting.Builder()
            .name("ah-buy")
            .description("Automatically confirms purchases in the Auction House.")
            .defaultValue(false)
            .visible(enableAutoConfirm::get)
            .build()
    );

    // Progression: /ah sell {price} -> "CONFIRM LISTING"
    // Progression: "auction -> Your Items" -> "INSERT ITEM" -> Sign GUI -> "CONFIRM LISTING"
    private final Setting<Boolean> acAHSell = sgAutoConfirm.add(new BoolSetting.Builder()
            .name("ah-sell")
            .description("Automatically confirms sales in the Auction House.")
            .defaultValue(false)
            .visible(enableAutoConfirm::get)
            .build()
    );

    // Progression: "ORDERS (Page {number})" -> "ORDERS -> Deliver Items" -> "ORDERS -> Confirm Delivery"
    private final Setting<Boolean> acOrderFulfill = sgAutoConfirm.add(new BoolSetting.Builder()
            .name("order-fulfill")
            .description("Automatically confirms fulfilling orders.")
            .defaultValue(false)
            .visible(enableAutoConfirm::get)
            .build()
    );

    // Progression: /tpa {player_name} -> "CONFIRM REQUEST"
    private final Setting<Boolean> acTPA = sgAutoConfirm.add(new BoolSetting.Builder()
            .name("tpa")
            .description("Automatically confirms TPA requests sent to someone else.")
            .defaultValue(false)
            .visible(enableAutoConfirm::get)
            .build()
    );

    // Progression: /tpahere {player_name} -> "CONFIRM REQUEST"
    private final Setting<Boolean> acTPAHere = sgAutoConfirm.add(new BoolSetting.Builder()
            .name("tpahere")
            .description("Automatically confirms TPAHERE requests sent to someone else.")
            .defaultValue(false)
            .visible(enableAutoConfirm::get)
            .build()
    );

    // Trigger: Chat Link Message
    // Player123 sent you a tpa request
    // [CLICK] or type /tpaccept Player123
    // Progression: /tpaccept {player_name} -> "ACCEPT REQUEST"
    private final Setting<Boolean> acTPAReceive = sgAutoConfirm.add(new BoolSetting.Builder()
            .name("tpa-receive")
            .description("Automatically confirms TPA requests sent to you.")
            .defaultValue(false)
            .visible(enableAutoConfirm::get)
            .build()
    );

    // Trigger: Chat Link Message
    // Player123 sent you a tpa request
    // [CLICK] or type /tpaccept Player123
    // Progression: /tpaccept {player_name} -> "ACCEPT TPAHERE REQUEST"
    private final Setting<Boolean> acTPAHereReceive = sgAutoConfirm.add(new BoolSetting.Builder()
            .name("tpahere-receive")
            .description("Automatically confirms TPAHERE requests sent to you.")
            .defaultValue(false)
            .visible(enableAutoConfirm::get)
            .build()
    );

    // Progression: "SHOP - SHARD SHOP" -> "CONFIRM PURCHASE"
    private final Setting<Boolean> acShardshopBuy = sgAutoConfirm.add(new BoolSetting.Builder()
            .name("shardshop-buy")
            .description("Automatically confirms purchases in the Shard Shop.")
            .defaultValue(false)
            .visible(enableAutoConfirm::get)
            .build()
    );

    // Progression: "CHOOSE 1 ITEM" -> "CONFIRM"
    private final Setting<Boolean> acCrateBuy = sgAutoConfirm.add(new BoolSetting.Builder()
            .name("crate-buy")
            .description("Automatically confirms purchases from crates.")
            .defaultValue(false)
            .visible(enableAutoConfirm::get)
            .build()
    );

    // Progression: /bounty add {player_name} {amount} -> "CONFIRM BOUNTY"
    private final Setting<Boolean> acBounty = sgAutoConfirm.add(new BoolSetting.Builder()
            .name("bounty-add")
            .description("Automatically confirms adding bounties on players.")
            .defaultValue(false)
            .visible(enableAutoConfirm::get)
            .build()
    );

    // Progression: "{amount} {PIG/COW/...} SPAWNERS" -> "CONFIRM SELL"
    private final Setting<Boolean> acSpawnerSellAll = sgAutoConfirm.add(new BoolSetting.Builder()
            .name("spawner-sell-all")
            .description("Automatically confirms selling all items in spawners.")
            .defaultValue(false)
            .visible(enableAutoConfirm::get)
            .build()
    );

    private final Setting<RandomBetweenInt> acRandomDelay = sgAutoConfirm.add(new RandomBetweenIntSetting.Builder()
            .name("random-delay")
            .description("Random delay between actions in milliseconds.")
            .defaultRange(50, 150)
            .range(0, 2000)
            .sliderRange(10, 1000)
            .visible(enableAutoConfirm::get)
            .build()
    );

    // ─────────────────────────────────────────────
    //  AutoConfirm State
    // ─────────────────────────────────────────────

    private final CircularBuffer<String> lastScreens = new CircularBuffer<>(5);
    private String currentScreen = null;

    private String currentCommand = null;
    private long commandTime = 0;
    private static final long COMMAND_TIMEOUT = 10000;

    /**
     * The screen title we are actively trying to confirm.
     * Non-null means a confirm attempt is in progress.
     */
    private String pendingConfirmScreen = null;

    /**
     * How many times we have retried pressing the confirm button for the
     * current pendingConfirmScreen without success.
     */
    private int confirmRetryCount = 0;

    /**
     * Maximum number of retries before giving up.
     * 12 retries × 75 ms = ~900 ms total window — enough for slow servers.
     */
    private static final int MAX_RETRIES = 12;

    /**
     * Delay between retry attempts in milliseconds.
     * Short enough to feel instant, long enough for the server to send slot data.
     */
    private static final long RETRY_INTERVAL_MS = 75;

    /** Scheduled time (epoch ms) at which the next confirm attempt fires. */
    private long acTimer = 0;

    /** When we last successfully sent a click, to prevent double-firing. */
    private long lastClickTime = 0;
    private static final long CLICK_COOLDOWN = 1000;


    // ─────────────────────────────────────────────
    //  Screen Tracking
    // ─────────────────────────────────────────────

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (event.screen == null) {
            // Screen closed. We intentionally do NOT cancel pendingConfirmScreen here,
            // because the server sometimes briefly closes then reopens the GUI
            // (e.g. rapid order-fulfill flow). The retry logic in tryPressConfirmButton
            // will handle it: if the screen stays gone it exhausts MAX_RETRIES and gives up.
            currentScreen = null;
            return;
        }

        if (!(event.screen instanceof HandledScreen<?>)) {
            return;
        }

        String newScreen = StringUtils.convertUnicodeToAscii(
                ((HandledScreen<?>) event.screen).getTitle().getString()
        ).toUpperCase();

        // Push previous screen into history only when it actually changed
        if (currentScreen != null && !currentScreen.equals(newScreen)) {
            lastScreens.add(currentScreen);
        }
        currentScreen = newScreen;

        if (!enableAutoConfirm.get()) return;

        // Respect click cooldown before scheduling a new confirm
        if (System.currentTimeMillis() - lastClickTime < CLICK_COOLDOWN) return;

        if (shouldConfirm(newScreen)) {
            // Start a fresh confirm attempt for this screen
            pendingConfirmScreen = newScreen;
            confirmRetryCount = 0;
            acTimer = System.currentTimeMillis() + acRandomDelay.get().getRandom();
        }
    }


    // ─────────────────────────────────────────────
    //  Command Tracking (for context-sensitive confirms)
    // ─────────────────────────────────────────────

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (!isActive()) return;

        // AutoConfirm: track relevant commands for context checks
        String command = null;
        if (event.packet instanceof ChatMessageC2SPacket packet) {
            String msg = packet.chatMessage().trim();
            if (msg.startsWith("/")) command = msg;
        } else if (event.packet instanceof CommandExecutionC2SPacket packet) {
            command = "/" + packet.command().trim();
        }

        if (command != null && isTrackedCommand(command)) {
            currentCommand = command;
            commandTime = System.currentTimeMillis();
        }

        // AutoAdvance: track "drop loot" clicks
        if (enableAutoAdvance.get()) {
            if (event.packet instanceof ClickSlotC2SPacket packet) {
                if (StringUtils.convertUnicodeToAscii(
                        packet.getStack().getName().getString()).equals("drop loot")) {
                    aaTimer = System.currentTimeMillis() + aaRandomDelay.get().getRandom();
                }
            }
        }
    }

    private boolean isTrackedCommand(String cmd) {
        return cmd.startsWith("/ah sell")     ||
               cmd.startsWith("/tpa ")        ||
               cmd.startsWith("/tpahere ")    ||
               cmd.startsWith("/tpaccept ")   ||
               cmd.startsWith("/bounty add ");
    }


    // ─────────────────────────────────────────────
    //  Tick: fire timers
    // ─────────────────────────────────────────────

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (acTimer > 0 && System.currentTimeMillis() >= acTimer) {
            acTimer = 0;
            if (pendingConfirmScreen != null) {
                tryPressConfirmButton();
            }
        }

        if (aaTimer > 0 && System.currentTimeMillis() >= aaTimer) {
            aaTimer = 0;
            advancePage(aaDirection.get());
        }
    }


    // ─────────────────────────────────────────────
    //  Core confirm logic — robust with retries
    // ─────────────────────────────────────────────

    /**
     * Attempts to click the confirm/accept button in the currently open screen.
     *
     * If the screen is not yet open, or if the inventory slots are not yet
     * populated by the server, the attempt is retried up to MAX_RETRIES times
     * with a short RETRY_INTERVAL_MS delay between each try. This covers the
     * two main race conditions:
     *
     *  (a) Screen opened but server hasn't sent slot data yet → button missing.
     *  (b) Rapid open/close cycle → mc.currentScreen is momentarily null.
     *
     * Only gives up if:
     *  – MAX_RETRIES is exhausted, or
     *  – a completely unrelated (non-confirm) screen is now open.
     */
    private void tryPressConfirmButton() {
        if (mc.player == null || mc.interactionManager == null) {
            cancelPending();
            return;
        }

        // ── Case: screen not open yet (transient null after rapid open/close) ──
        if (mc.currentScreen == null) {
            scheduleRetry();
            return;
        }

        // ── Case: open screen is not a handled/inventory screen ──
        if (!(mc.currentScreen instanceof HandledScreen<?>)) {
            cancelPending();
            return;
        }

        HandledScreen<?> screen = (HandledScreen<?>) mc.currentScreen;
        String actualTitle = StringUtils.convertUnicodeToAscii(
                screen.getTitle().getString()
        ).toUpperCase();

        // ── Case: a different screen opened (not the one we planned to confirm) ──
        if (!actualTitle.equals(pendingConfirmScreen)) {
            if (shouldConfirm(actualTitle)) {
                // A new valid confirm screen appeared — update and retry from fresh
                pendingConfirmScreen = actualTitle;
                confirmRetryCount = 0;
                scheduleRetry();
            } else {
                // User navigated somewhere else entirely — abandon
                cancelPending();
            }
            return;
        }

        // ── Correct screen is open — search for the button ──
        ScreenHandler handler = screen.getScreenHandler();

        for (int i = 0; i < handler.slots.size(); i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (isConfirmButton(stack)) {
                // Click twice (vanilla double-click protection workaround)
                mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.PICKUP, mc.player);
                lastClickTime = System.currentTimeMillis();
                cancelPending(); // success — clear state
                return;
            }
        }

        // ── Button not found: slots likely not populated by server yet ──
        scheduleRetry();
    }

    /** Arms the next retry if we still have budget, otherwise gives up. */
    private void scheduleRetry() {
        if (confirmRetryCount < MAX_RETRIES) {
            confirmRetryCount++;
            acTimer = System.currentTimeMillis() + RETRY_INTERVAL_MS;
        } else {
            cancelPending();
        }
    }

    /** Clears all pending confirm state. */
    private void cancelPending() {
        pendingConfirmScreen = null;
        confirmRetryCount = 0;
        acTimer = 0;
    }


    // ─────────────────────────────────────────────
    //  shouldConfirm
    // ─────────────────────────────────────────────

    private boolean shouldConfirm(String screenTitle) {
        if (screenTitle == null) return false;
        if (!(screenTitle.contains("CONFIRM") || screenTitle.contains("ACCEPT"))) return false;

        boolean shouldConfirm = false;

        switch (screenTitle) {
            case "CONFIRM PURCHASE" -> {
                boolean foundAuction = false;
                boolean foundShardShop = false;
                for (int i = 0; i < Math.min(lastScreens.size, 3); i++) {
                    try {
                        String recent = lastScreens.get(i);
                        if (recent != null) {
                            if (recent.contains("AUCTION"))       foundAuction = true;
                            if (recent.contains("SHOP - SHARD SHOP")) foundShardShop = true;
                        }
                    } catch (Exception ignored) {}
                }
                if (acAHBuy.get() && foundAuction)          shouldConfirm = true;
                else if (acShardshopBuy.get() && foundShardShop) shouldConfirm = true;
            }
            case "CONFIRM LISTING" -> {
                if (acAHSell.get()) shouldConfirm = true;
            }
            case "ORDERS -> CONFIRM DELIVERY" -> {
                try {
                    String prevScreen = lastScreens.get(0);
                    if (acOrderFulfill.get() && prevScreen != null && prevScreen.contains("ORDERS")) {
                        shouldConfirm = true;
                    }
                } catch (Exception ignored) {}
            }
            case "CONFIRM REQUEST" -> {
                if (currentCommand != null && System.currentTimeMillis() - commandTime < COMMAND_TIMEOUT) {
                    if (acTPA.get()     && currentCommand.startsWith("/tpa "))     shouldConfirm = true;
                    else if (acTPAHere.get() && currentCommand.startsWith("/tpahere ")) shouldConfirm = true;
                }
            }
            case "ACCEPT REQUEST" -> {
                if (acTPAReceive.get() && currentCommand != null &&
                        System.currentTimeMillis() - commandTime < COMMAND_TIMEOUT &&
                        currentCommand.startsWith("/tpaccept ")) {
                    shouldConfirm = true;
                }
            }
            case "ACCEPT TPAHERE REQUEST" -> {
                if (acTPAHereReceive.get() && currentCommand != null &&
                        System.currentTimeMillis() - commandTime < COMMAND_TIMEOUT &&
                        currentCommand.startsWith("/tpaccept ")) {
                    shouldConfirm = true;
                }
            }
            case "CONFIRM" -> {
                if (acCrateBuy.get()) {
                    for (int i = 0; i < Math.min(lastScreens.size, 3); i++) {
                        try {
                            String recent = lastScreens.get(i);
                            if (recent != null && recent.contains("CHOOSE 1 ITEM")) {
                                shouldConfirm = true;
                                break;
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
            case "CONFIRM BOUNTY" -> {
                if (acBounty.get() && currentCommand != null &&
                        System.currentTimeMillis() - commandTime < COMMAND_TIMEOUT &&
                        currentCommand.startsWith("/bounty add ")) {
                    shouldConfirm = true;
                }
            }
            case "CONFIRM SELL" -> {
                try {
                    String prevScreen = lastScreens.get(0);
                    if (acSpawnerSellAll.get() && prevScreen != null && prevScreen.contains("SPAWNER")) {
                        shouldConfirm = true;
                    }
                } catch (Exception ignored) {}
            }
        }

        return shouldConfirm;
    }

    private boolean isConfirmButton(ItemStack stack) {
        if (stack.isEmpty()) return false;
        boolean isGreenGlass = stack.getItem() == Items.LIME_STAINED_GLASS_PANE ||
                               stack.getItem() == Items.GREEN_STAINED_GLASS_PANE;
        String name = StringUtils.convertUnicodeToAscii(stack.getName().getString()).toLowerCase();
        boolean hasConfirmText = name.contains("confirm") || name.contains("accept");
        return isGreenGlass && hasConfirmText;
    }


    // ─────────────────────────────────────────────
    //  AutoAdvance
    // ─────────────────────────────────────────────

    private final SettingGroup sgAutoAdvance = settings.createGroup("AutoAdvance");

    private final Setting<String> aaDescription = sgAutoAdvance.add(new TextDisplaySetting.Builder()
            .name("")
            .description("")
            .defaultValue("Automatically advances pages in spawner GUI after dropping items.")
            .build()
    );

    private final Setting<Boolean> enableAutoAdvance = sgAutoAdvance.add(new BoolSetting.Builder()
            .name("enable-auto-advance")
            .description("Automatically advances pages in UIs after certain actions.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Direction> aaDirection = sgAutoAdvance.add(new EnumSetting.Builder<Direction>()
            .name("direction")
            .description("The direction to advance the page.")
            .defaultValue(Direction.FORWARDS)
            .visible(enableAutoAdvance::get)
            .build()
    );

    private final Setting<RandomBetweenInt> aaRandomDelay = sgAutoAdvance.add(new RandomBetweenIntSetting.Builder()
            .name("random-delay")
            .description("Random delay between actions in milliseconds.")
            .defaultRange(50, 150)
            .range(0, 2000)
            .sliderRange(10, 1000)
            .visible(enableAutoAdvance::get)
            .build()
    );

    private long aaTimer = 0;

    private void advancePage(Direction dir) {
        if (mc.player == null || mc.interactionManager == null || mc.currentScreen == null) return;
        if (!(mc.currentScreen instanceof HandledScreen<?> screen)) return;

        ScreenHandler handler = screen.getScreenHandler();

        for (int i = 0; i < handler.slots.size(); i++) {
            String name = StringUtils.convertUnicodeToAscii(
                    handler.getSlot(i).getStack().getName().getString());
            if ((dir == Direction.FORWARDS  && name.equals("next")) ||
                (dir == Direction.BACKWARDS && name.equals("back"))) {
                mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.PICKUP, mc.player);
                return;
            }
        }
    }

    private enum Direction {
        FORWARDS,
        BACKWARDS
    }


    // ─────────────────────────────────────────────
    //  Utilities
    // ─────────────────────────────────────────────

    private static class CircularBuffer<T> {
        private final Object[] buffer;
        private int index = 0;
        public int size = 0;

        public CircularBuffer(int capacity) {
            buffer = new Object[capacity];
        }

        public void add(T item) {
            buffer[index] = item;
            index = (index + 1) % buffer.length;
            if (size < buffer.length) size++;
        }

        @SuppressWarnings("unchecked")
        public T get(int i) {
            if (i >= size) throw new IndexOutOfBoundsException();
            int idx = (index - size + i + buffer.length) % buffer.length;
            return (T) buffer[idx];
        }
    }
}