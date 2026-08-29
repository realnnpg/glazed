package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import com.nnpg.glazed.VersionUtil;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.HitResult;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class AutoRaidAfk extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgClicking = settings.createGroup("Clicking");
    private final SettingGroup sgRaid = settings.createGroup("Raid detection");
    private final SettingGroup sgBottle = settings.createGroup("Ominous bottle");
    private final SettingGroup sgFreeze = settings.createGroup("Freeze");

    private final Setting<Double> attackRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("attack-range")
        .description("Start clicking when a mob is this close.")
        .defaultValue(5)
        .min(1)
        .sliderRange(1, 16)
        .build()
    );

    private final Setting<Targets> targets = sgGeneral.add(new EnumSetting.Builder<Targets>()
        .name("counts-as-a-mob")
        .description("Which entities are close enough to be worth clicking at.")
        .defaultValue(Targets.Hostiles)
        .build()
    );

    private final Setting<Boolean> avoidBlocks = sgGeneral.add(new BoolSetting.Builder()
        .name("avoid-blocks")
        .description("Hold the clicks while your crosshair is on a block, so a stray left click does not start mining the farm.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> freeze = sgFreeze.add(new BoolSetting.Builder()
        .name("freeze")
        .description("Lock the game while this runs. Movement dies, Escape stops opening the pause menu and screens stop opening at all, until you turn the module off. The Meteor menu still opens, which is how you get back out.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Blocked> blocked = sgFreeze.add(new EnumSetting.Builder<Blocked>()
        .name("block-screens")
        .description("How much the freeze swallows. Everything stops every screen, Pause only stops Escape. The death and disconnect screens always get through either way.")
        .defaultValue(Blocked.Everything)
        .visible(freeze::get)
        .build()
    );

    private final Setting<Boolean> lockLook = sgFreeze.add(new BoolSetting.Builder()
        .name("lock-look")
        .description("Hold the camera where it was when the freeze started, so a knocked mouse cannot turn you away from the mobs.")
        .defaultValue(true)
        .visible(freeze::get)
        .build()
    );

    private final Setting<Boolean> notifications = sgGeneral.add(new BoolSetting.Builder()
        .name("notifications")
        .description("Say in chat what it is doing and what it is waiting for.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> minDelay = sgClicking.add(new IntSetting.Builder()
        .name("min-delay")
        .description("Shortest gap between two clicks, in ticks.")
        .defaultValue(9)
        .min(1)
        .sliderRange(1, 40)
        .build()
    );

    private final Setting<Integer> maxDelay = sgClicking.add(new IntSetting.Builder()
        .name("max-delay")
        .description("Longest gap between two clicks, in ticks. Every gap is rolled fresh between the two.")
        .defaultValue(18)
        .min(1)
        .sliderRange(1, 60)
        .build()
    );

    private final Setting<Boolean> humanPauses = sgClicking.add(new BoolSetting.Builder()
        .name("human-pauses")
        .description("Occasionally sit out a few clicks, the way someone actually watching the screen would.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> pauseChance = sgClicking.add(new IntSetting.Builder()
        .name("pause-chance")
        .description("Percent of clicks that turn into one of those longer pauses.")
        .defaultValue(8)
        .min(1)
        .max(100)
        .sliderMax(50)
        .visible(humanPauses::get)
        .build()
    );

    private final Setting<Integer> pauseLength = sgClicking.add(new IntSetting.Builder()
        .name("pause-length")
        .description("Base length of a pause in ticks. The real one lands somewhere between this and double it.")
        .defaultValue(30)
        .min(5)
        .sliderRange(5, 120)
        .visible(humanPauses::get)
        .build()
    );

    private final Setting<Detection> detection = sgRaid.add(new EnumSetting.Builder<Detection>()
        .name("detection")
        .description("How it decides a raid is running. The boss bar is the real signal; raiders nearby is the fallback for servers that hide it.")
        .defaultValue(Detection.Either)
        .build()
    );

    private final Setting<String> bossBarName = sgRaid.add(new StringSetting.Builder()
        .name("boss-bar-text")
        .description("A raid boss bar is one whose title contains this, ignoring case. Blank matches any boss bar at all.")
        .defaultValue("raid")
        .visible(() -> detection.get() != Detection.Raiders)
        .build()
    );

    private final Setting<Double> raiderRange = sgRaid.add(new DoubleSetting.Builder()
        .name("raider-range")
        .description("How far out a raider still counts as a raid in progress.")
        .defaultValue(64)
        .min(8)
        .sliderRange(8, 128)
        .visible(() -> detection.get() != Detection.BossBar)
        .build()
    );

    private final Setting<Integer> raidEndGrace = sgRaid.add(new IntSetting.Builder()
        .name("raid-end-grace")
        .description("Ticks the raid has to be gone before it is treated as over. The victory bar hangs around for a moment, so do not set this too low.")
        .defaultValue(100)
        .min(20)
        .sliderRange(20, 400)
        .build()
    );

    private final Setting<Boolean> drinkBottle = sgBottle.add(new BoolSetting.Builder()
        .name("drink-on-raid-end")
        .description("Drink an ominous bottle from the hotbar once the raid is over.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> restoreSlot = sgBottle.add(new BoolSetting.Builder()
        .name("restore-slot")
        .description("Go back to the slot you were holding after the drink.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> disableWhenEmpty = sgBottle.add(new BoolSetting.Builder()
        .name("stop-when-out")
        .description("Turn the module off when the raid ends and the hotbar has no ominous bottle. Off keeps it running and checking again.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> postDrinkWait = sgBottle.add(new IntSetting.Builder()
        .name("post-drink-wait")
        .description("Ticks to leave the bottle alone after drinking one, so a slow raid start does not cost you the whole stack.")
        .defaultValue(400)
        .min(40)
        .sliderRange(40, 1200)
        .build()
    );

    private static List<Field> bossMapFields;

    private final Random random = new Random();

    private boolean frozen = false;
    private ClientInput blankInput = null;
    private float lockYaw = 0f;
    private float lockPitch = 0f;

    private State state = State.WATCH;
    private int clickCounter;
    private int raidGoneTicks;
    private int bottleBackoff;
    private int drinkTicks;
    private int prevSlot = -1;
    private boolean usingSeen;
    private int nagCounter;
    private boolean warnedNoBossBar;
    private int clicks;

    public AutoRaidAfk() {
        super(GlazedAddon.CATEGORY, "auto-raid-afk", "Clicks through a raid for you and pulls the next one with an ominous bottle.");
    }

    @Override
    public void onActivate() {
        state = State.WATCH;
        clickCounter = 0;
        raidGoneTicks = 0;
        bottleBackoff = 0;
        drinkTicks = 0;
        prevSlot = -1;
        usingSeen = false;
        nagCounter = 0;
        warnedNoBossBar = false;
        clicks = 0;
        frozen = false;
        blankInput = null;

        if (mc.player == null || mc.level == null) {
            error("Join a world first.");
            toggle();
            return;
        }

        if (!inRaid()) {
            warning("No raid running, so there is nothing to sit through. Turn this on once the raid has started.");
            toggle();
            return;
        }

        if (notifications.get()) info("In the raid. Clicking whenever something is within (highlight)%.0f(default) blocks.", attackRange.get());
    }

    @Override
    public void onDeactivate() {
        releaseUse();
        releaseFreeze();
        state = State.WATCH;
        prevSlot = -1;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;

        if (freeze.get()) applyFreeze();
        else if (frozen) releaseFreeze();

        switch (state) {
            case WATCH -> tickWatch();
            case DRINK_SWAP -> tickDrinkSwap();
            case DRINK_HOLD -> tickDrinkHold();
        }
    }

    private void tickWatch() {
        boolean raid = inRaid();

        if (raid) {
            raidGoneTicks = 0;
            bottleBackoff = 0;
        } else {
            raidGoneTicks++;
        }

        tickClicking();

        if (raid || raidGoneTicks < raidEndGrace.get()) return;

        if (!drinkBottle.get()) {
            info("Raid is over and drinking is switched off. Turning off.");
            toggle();
            return;
        }

        if (bottleBackoff > 0) {
            bottleBackoff--;
            return;
        }

        startDrink();
    }

    private void tickClicking() {
        if (clickCounter > 0) {
            clickCounter--;
            return;
        }

        if (mc.screen != null) {
            nag("A screen is open, not clicking through it.");
            return;
        }

        if (!mobInRange()) {
            nag(String.format("Nothing within %.0f blocks yet, holding fire.", attackRange.get()));
            return;
        }

        if (avoidBlocks.get() && mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) {
            nag("Something is in reach but your crosshair is on a block, so a click would mine it. Look at the mobs.");
            clickCounter = 10;
            return;
        }

        Utils.leftClick();
        clicks++;
        clickCounter = nextDelay();
    }

    private int nextDelay() {
        int min = minDelay.get();
        int max = Math.max(min, maxDelay.get());
        int delay = min + random.nextInt(max - min + 1);

        if (humanPauses.get() && random.nextInt(100) < pauseChance.get()) {
            delay += pauseLength.get() + random.nextInt(pauseLength.get() + 1);
        }

        return delay;
    }

    private void startDrink() {
        int slot = findBottle();

        if (slot < 0) {
            if (disableWhenEmpty.get()) {
                warning("Raid is over and there is no ominous bottle in your hotbar. Turning off.");
                toggle();
            } else {
                warning("Raid is over and there is no ominous bottle in your hotbar. Looking again in %d seconds.", postDrinkWait.get() / 20);
                bottleBackoff = postDrinkWait.get();
            }
            return;
        }

        prevSlot = mc.player.getInventory().getSelectedSlot();
        VersionUtil.setSelectedSlot(mc.player, slot);
        drinkTicks = 0;
        usingSeen = false;
        state = State.DRINK_SWAP;
        if (notifications.get()) info("Raid is over. Drinking the ominous bottle in slot (highlight)%d(default).", slot + 1);
    }

    private void tickDrinkSwap() {
        drinkTicks++;
        if (drinkTicks < 3) return;

        if (!mc.player.getMainHandItem().is(Items.OMINOUS_BOTTLE)) {
            warning("The bottle is not in hand after swapping, backing off.");
            finishDrink(false);
            return;
        }

        drinkTicks = 0;
        state = State.DRINK_HOLD;
    }

    private void tickDrinkHold() {
        drinkTicks++;
        mc.options.keyUse.setDown(true);

        if (mc.player.isUsingItem()) {
            usingSeen = true;
        } else if (usingSeen) {
            finishDrink(true);
            return;
        }

        if (drinkTicks > 100) {
            warning("The ominous bottle never went down, trying again later.");
            finishDrink(false);
        }
    }

    private void finishDrink(boolean drank) {
        releaseUse();

        if (restoreSlot.get() && prevSlot >= 0 && prevSlot <= 8) VersionUtil.setSelectedSlot(mc.player, prevSlot);
        prevSlot = -1;

        raidGoneTicks = 0;
        bottleBackoff = postDrinkWait.get();
        usingSeen = false;
        drinkTicks = 0;
        state = State.WATCH;

        if (drank && notifications.get()) info("Bottle drunk. Waiting for the next raid.");
    }

    private void releaseUse() {
        if (mc.options != null) mc.options.keyUse.setDown(false);
    }

    private int findBottle() {
        for (int i = 0; i <= 8; i++) {
            if (mc.player.getInventory().getItem(i).is(Items.OMINOUS_BOTTLE)) return i;
        }
        return -1;
    }

    private boolean mobInRange() {
        double range = attackRange.get();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == mc.player) continue;
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) continue;
            if (!matchesTarget(entity)) continue;
            if (mc.player.distanceTo(entity) <= range) return true;
        }

        return false;
    }

    private boolean matchesTarget(Entity entity) {
        return switch (targets.get()) {
            case Raiders -> entity instanceof Raider;
            case Hostiles -> entity instanceof Monster;
            case AnyMob -> entity instanceof Mob;
        };
    }

    private boolean inRaid() {
        return switch (detection.get()) {
            case BossBar -> {
                Boolean bar = raidBarUp();
                if (bar != null) yield bar;
                if (!warnedNoBossBar) {
                    warning("Could not read the boss bar, falling back to looking for raiders.");
                    warnedNoBossBar = true;
                }
                yield raidersNearby();
            }
            case Raiders -> raidersNearby();
            case Either -> raidBarUp() == Boolean.TRUE || raidersNearby();
        };
    }

    private boolean raidersNearby() {
        double range = raiderRange.get();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof Raider raider && raider.isAlive() && mc.player.distanceTo(raider) <= range) return true;
        }

        return false;
    }

    private Boolean raidBarUp() {
        if (mc.gui == null) return null;

        BossHealthOverlay overlay = mc.gui.getBossOverlay();
        if (overlay == null) return null;

        List<Field> fields = bossMapFields();
        if (fields.isEmpty()) return null;

        String needle = bossBarName.get().trim().toLowerCase(Locale.ROOT);
        boolean read = false;

        for (Field field : fields) {
            Object value;
            try {
                value = field.get(overlay);
            } catch (Throwable ignored) {
                continue;
            }

            if (!(value instanceof Map<?, ?> map)) continue;
            read = true;

            for (Object entry : map.values()) {
                if (!(entry instanceof BossEvent bossEvent)) continue;
                if (needle.isEmpty()) return true;
                if (bossEvent.getName().getString().toLowerCase(Locale.ROOT).contains(needle)) return true;
            }
        }

        return read ? Boolean.FALSE : null;
    }

    private static List<Field> bossMapFields() {
        if (bossMapFields == null) {
            List<Field> found = new ArrayList<>();

            for (Field field : BossHealthOverlay.class.getDeclaredFields()) {
                if (!Map.class.isAssignableFrom(field.getType())) continue;
                try {
                    field.setAccessible(true);
                    found.add(field);
                } catch (Throwable ignored) {
                }
            }

            bossMapFields = found;
        }

        return bossMapFields;
    }

    private void nag(String message) {
        if (!notifications.get()) return;

        if (nagCounter > 0) {
            nagCounter--;
            return;
        }

        info(message);
        nagCounter = 200;
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (!frozen) return;

        Screen screen = event.screen;

        if (screen == null) return;
        if (screen instanceof WidgetScreen) return;
        if (screen instanceof DeathScreen) return;
        if (screen instanceof DisconnectedScreen) return;
        if (blocked.get() == Blocked.Pause && !(screen instanceof PauseScreen)) return;

        event.setCancelled(true);
    }

    private void applyFreeze() {
        if (!frozen) {
            lockYaw = mc.player.getYRot();
            lockPitch = mc.player.getXRot();
            frozen = true;

            if (notifications.get()) info("Frozen. The Meteor menu still opens, turn this off to move again.");
        }

        if (mc.player.input != blankInput) {
            blankInput = new ClientInput();
            mc.player.input = blankInput;
        }

        if (lockLook.get()) {
            mc.player.setYRot(lockYaw);
            mc.player.setXRot(lockPitch);
            mc.player.yRotO = lockYaw;
            mc.player.xRotO = lockPitch;
        }

        drainKeys();
    }

    private void releaseFreeze() {
        if (!frozen) return;

        frozen = false;
        blankInput = null;

        if (mc.player != null && mc.options != null) mc.player.input = new KeyboardInput(mc.options);
        if (notifications.get()) info("Unfrozen.");
    }

    private void drainKeys() {
        drain(mc.options.keyInventory);
        drain(mc.options.keyDrop);
        drain(mc.options.keySwapOffhand);
        drain(mc.options.keyChat);
        drain(mc.options.keyCommand);
        drain(mc.options.keyPickItem);
        drain(mc.options.keyTogglePerspective);

        for (KeyMapping slot : mc.options.keyHotbarSlots) drain(slot);
    }

    private void drain(KeyMapping key) {
        while (key.consumeClick());
        key.setDown(false);
    }

    @Override
    public String getInfoString() {
        return switch (state) {
            case WATCH -> String.valueOf(clicks);
            case DRINK_SWAP, DRINK_HOLD -> "drinking";
        };
    }

    public enum Blocked { Everything, Pause }

    public enum Targets {
        Raiders,
        Hostiles,
        AnyMob
    }

    public enum Detection {
        BossBar,
        Raiders,
        Either
    }

    private enum State {
        WATCH,
        DRINK_SWAP,
        DRINK_HOLD
    }
}
