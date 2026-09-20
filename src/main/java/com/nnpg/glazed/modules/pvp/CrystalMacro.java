package com.nnpg.glazed.modules.pvp;

import com.nnpg.glazed.GlazedAddon;
import com.nnpg.glazed.utils.glazed.BlockUtil;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import java.util.Random;

public class CrystalMacro extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgObsidian = settings.createGroup("Obsidian");
    private final SettingGroup sgStopOnKill = settings.createGroup("Stop On Kill");

    private final Setting<Double> placeDelay = sgGeneral.add(new DoubleSetting.Builder()
        .name("place-delay")
        .description("Ticks between placing crystals.")
        .defaultValue(0.0)
        .min(0.0)
        .max(20.0)
        .sliderRange(0.0, 20.0)
        .build()
    );

    private final Setting<Double> minCps = sgGeneral.add(new DoubleSetting.Builder()
        .name("min-cps")
        .description("Slowest breaking speed.")
        .defaultValue(8.0)
        .min(1.0)
        .max(20.0)
        .sliderRange(1.0, 20.0)
        .build()
    );

    private final Setting<Double> maxCps = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-cps")
        .description("Fastest breaking speed.")
        .defaultValue(12.0)
        .min(1.0)
        .max(20.0)
        .sliderRange(1.0, 20.0)
        .build()
    );

    private final Setting<Boolean> placeObsidian = sgObsidian.add(new BoolSetting.Builder()
        .name("place-obsidian")
        .description("Puts obsidian down first when you aim at something that isn't obsidian or bedrock.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> obsidianSwitchDelay = sgObsidian.add(new DoubleSetting.Builder()
        .name("switch-delay")
        .description("Ticks to wait after swapping to obsidian before placing it.")
        .defaultValue(0.0)
        .min(0.0)
        .max(20.0)
        .sliderRange(0.0, 20.0)
        .visible(placeObsidian::get)
        .build()
    );

    private final Setting<Double> obsidianCooldown = sgObsidian.add(new DoubleSetting.Builder()
        .name("place-cooldown")
        .description("Seconds before it's allowed to place obsidian again.")
        .defaultValue(1.0)
        .min(0.0)
        .max(10.0)
        .sliderRange(0.0, 10.0)
        .visible(placeObsidian::get)
        .build()
    );

    private final Setting<Boolean> stopOnKill = sgStopOnKill.add(new BoolSetting.Builder()
        .name("stop-on-kill")
        .description("Pause when someone dies nearby so you don't blow their loot up.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> stopTime = sgStopOnKill.add(new DoubleSetting.Builder()
        .name("stop-time")
        .description("Seconds to stay paused.")
        .defaultValue(3.0)
        .min(0.0)
        .max(15.0)
        .sliderRange(0.0, 15.0)
        .visible(stopOnKill::get)
        .build()
    );

    private final Setting<Double> stopRange = sgStopOnKill.add(new DoubleSetting.Builder()
        .name("stop-range")
        .description("How close the death has to be to count.")
        .defaultValue(18.0)
        .min(4.0)
        .max(48.0)
        .sliderRange(4.0, 48.0)
        .visible(stopOnKill::get)
        .build()
    );

    private static final double LOOT_RADIUS = 10.0;

    private final Random random = new Random();

    private int placeDelayCounter;
    private int breakDelayCounter;
    private int switchDelayCounter;
    private int cooldownCounter;
    private int nextBreakDelay;

    private boolean placingObsidian;
    private BlockHitResult pendingObsidian;
    private boolean useWasHeld;
    private long pausedUntil;

    public CrystalMacro() {
        super(GlazedAddon.pvp, "crystal-macro", "Places and breaks crystals while you hold right click.");
    }

    @Override
    public void onActivate() {
        reset();
        rollBreakDelay();
    }

    @Override
    public void onDeactivate() {
        reset();
    }

    private void reset() {
        placeDelayCounter = 0;
        breakDelayCounter = 0;
        switchDelayCounter = 0;
        cooldownCounter = 0;
        placingObsidian = false;
        pendingObsidian = null;
        useWasHeld = false;
        pausedUntil = 0L;
    }

    // wtf
    private void rollBreakDelay() {
        double min = minCps.get();
        double max = maxCps.get();
        if (min > max) {
            double swap = min;
            min = max;
            max = swap;
        }

        double cps = min + (max - min) * random.nextDouble();
        nextBreakDelay = Math.max(1, (int) (20.0 / cps));
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (!stopOnKill.get()) return;
        if (!(event.packet instanceof ClientboundEntityEventPacket packet)) return;
        if (packet.getEventId() != 3) return;
        if (mc.level == null || mc.player == null) return;

        Entity dead = packet.getEntity(mc.level);
        if (dead == null || dead == mc.player) return;
        if (dead.distanceTo(mc.player) > stopRange.get()) return;

        pausedUntil = System.currentTimeMillis() + (long) (stopTime.get() * 1000.0);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.gui.screen() != null) return;

        tickCounters();

        if (stopOnKill.get() && System.currentTimeMillis() < pausedUntil) return;
        if (mc.player.isUsingItem()) return;

        boolean useHeld = mc.options.keyUse.isDown();
        if (!useHeld) {
            useWasHeld = false;
            cooldownCounter = 0;
            return;
        }

        if (!useWasHeld) {
            useWasHeld = true;
            cooldownCounter = 0;
        }

        if (placingObsidian && switchDelayCounter <= 0 && pendingObsidian != null) {
            finishObsidian();
            return;
        }

        if (!mc.player.getMainHandItem().is(Items.END_CRYSTAL)) return;

        HitResult target = mc.hitResult;
        if (target instanceof BlockHitResult blockHit) onBlock(blockHit);
        else if (target instanceof EntityHitResult entityHit) onEntity(entityHit);
    }

    private void tickCounters() {
        if (placeDelayCounter > 0) placeDelayCounter--;
        if (breakDelayCounter > 0) breakDelayCounter--;
        if (switchDelayCounter > 0) switchDelayCounter--;
        if (cooldownCounter > 0) cooldownCounter--;
    }

    private void onBlock(BlockHitResult blockHit) {
        if (blockHit.getType() != HitResult.Type.BLOCK) return;
        if (placeDelayCounter > 0 || placingObsidian) return;

        BlockPos pos = blockHit.getBlockPos();
        boolean solidEnough = BlockUtil.isBlockAtPosition(pos, Blocks.OBSIDIAN) || BlockUtil.isBlockAtPosition(pos, Blocks.BEDROCK);

        if (!solidEnough) {
            if (placeObsidian.get() && cooldownCounter <= 0) startObsidian(blockHit);
            return;
        }

        if (!canPlaceCrystal(pos)) return;
        if (lootNearby(pos.above())) return;

        BlockUtil.interactWithBlock(blockHit, true);
        placeDelayCounter = placeDelay.get().intValue();
    }

    private void startObsidian(BlockHitResult blockHit) {
        if (mc.player == null || mc.level == null) return;

        int slot = hotbarSlot(Items.OBSIDIAN);
        if (slot == -1) return;

        mc.player.getInventory().setSelectedSlot(slot);

        placingObsidian = true;
        pendingObsidian = blockHit;
        switchDelayCounter = obsidianSwitchDelay.get().intValue();
    }

    private void finishObsidian() {
        if (pendingObsidian == null) {
            placingObsidian = false;
            return;
        }

        BlockUtil.interactWithBlock(pendingObsidian, true);

        int crystal = hotbarSlot(Items.END_CRYSTAL);
        if (crystal != -1) mc.player.getInventory().setSelectedSlot(crystal);

        BlockPos pos = pendingObsidian.getBlockPos();
        if (canPlaceCrystal(pos) && !lootNearby(pos.above())) {
            BlockUtil.interactWithBlock(pendingObsidian, true);
            placeDelayCounter = placeDelay.get().intValue();
        }

        cooldownCounter = (int) (obsidianCooldown.get() * 20);
        placingObsidian = false;
        pendingObsidian = null;
    }

    private void onEntity(EntityHitResult entityHit) {
        if (breakDelayCounter > 0 || placingObsidian) return;
        if (mc.player == null || mc.gameMode == null) return;

        Entity entity = entityHit.getEntity();
        if (entity == null || entity.isRemoved() || !entity.isAlive()) return;
        if (!(entity instanceof EndCrystal) && !(entity instanceof Slime)) return;
        if (lootNearby(entity.blockPosition())) return;

        mc.gameMode.attack(mc.player, entity);
        mc.player.swing(InteractionHand.MAIN_HAND);

        breakDelayCounter = nextBreakDelay;
        rollBreakDelay();
    }

    private boolean canPlaceCrystal(BlockPos pos) {
        if (mc.level == null) return false;

        BlockPos above = pos.above();
        if (!mc.level.isEmptyBlock(above)) return false;

        int x = above.getX(), y = above.getY(), z = above.getZ();
        return mc.level.getEntities(null, new AABB(x, y, z, x + 1.0, y + 2.0, z + 1.0)).isEmpty();
    }

    // bro
    private boolean lootNearby(BlockPos pos) {
        if (mc.level == null || pos == null) return false;

        AABB area = new AABB(
            pos.getX() - LOOT_RADIUS, pos.getY() - LOOT_RADIUS, pos.getZ() - LOOT_RADIUS,
            pos.getX() + LOOT_RADIUS, pos.getY() + LOOT_RADIUS, pos.getZ() + LOOT_RADIUS
        );

        for (Entity entity : mc.level.getEntities(null, area)) {
            if (!(entity instanceof ItemEntity item)) continue;

            ItemStack stack = item.getItem();
            if (stack.isEmpty()) continue;

            if (stack.is(Items.ELYTRA)) return true;
            if (stack.is(ItemTags.SWORDS)) return true;
            if (stack.is(ItemTags.HEAD_ARMOR) || stack.is(ItemTags.CHEST_ARMOR)
                || stack.is(ItemTags.LEG_ARMOR) || stack.is(ItemTags.FOOT_ARMOR)) return true;
        }

        return false;
    }

    private int hotbarSlot(net.minecraft.world.item.Item item) {
        if (mc.player == null) return -1;

        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).is(item)) return i;
        }
        return -1;
    }
}
