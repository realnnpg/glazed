package com.nnpg.glazed.modules.pvp;

import com.mojang.blaze3d.platform.InputConstants;
import com.nnpg.glazed.GlazedAddon;
import com.nnpg.glazed.VersionUtil;
import com.nnpg.glazed.utils.glazed.BlockUtil;
import com.nnpg.glazed.utils.glazed.KeyUtils;
import meteordevelopment.meteorclient.events.entity.player.DoItemUseEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IMinecraft;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class AnchorMacro extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDelays = settings.createGroup("Delays");
    private final SettingGroup sgChances = settings.createGroup("Chances");

    private final Setting<Boolean> oneGlowstone = sgGeneral.add(new BoolSetting.Builder()
        .name("one-glowstone")
        .description("Blow it at one charge instead of waiting for a full four.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> whileUse = sgGeneral.add(new BoolSetting.Builder()
        .name("while-use")
        .description("Keep going even while you're eating, blocking or holding a good tool in offhand.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> lootProtect = sgGeneral.add(new BoolSetting.Builder()
        .name("loot-protect")
        .description("Stop when there's a body or decent loot on the floor near you.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> explodeSlot = sgGeneral.add(new IntSetting.Builder()
        .name("explode-slot")
        .description("Hotbar slot to hold when detonating.")
        .defaultValue(9)
        .min(1)
        .max(9)
        .sliderRange(1, 9)
        .build()
    );

    private final Setting<Boolean> onlyOwn = sgGeneral.add(new BoolSetting.Builder()
        .name("only-own")
        .description("Only touch anchors you placed yourself.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> onlyCharge = sgGeneral.add(new BoolSetting.Builder()
        .name("only-charge")
        .description("Charge but never detonate.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> switchBack = sgGeneral.add(new BoolSetting.Builder()
        .name("switch-back")
        .description("Grab another anchor after blowing one, so you're ready to place the next.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> switchDelay = sgDelays.add(new IntSetting.Builder()
        .name("switch-delay")
        .description("Ticks to wait before changing slot.")
        .defaultValue(1)
        .min(0)
        .max(20)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Integer> glowstoneDelay = sgDelays.add(new IntSetting.Builder()
        .name("glowstone-delay")
        .description("Ticks to wait before charging.")
        .defaultValue(0)
        .min(0)
        .max(20)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Integer> explodeDelay = sgDelays.add(new IntSetting.Builder()
        .name("explode-delay")
        .description("Ticks to wait before detonating.")
        .defaultValue(1)
        .min(0)
        .max(20)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Integer> placeChance = sgChances.add(new IntSetting.Builder()
        .name("place-chance")
        .description("Percent chance to act on an uncharged anchor at all.")
        .defaultValue(100)
        .min(0)
        .max(100)
        .sliderRange(0, 100)
        .build()
    );

    private final Setting<Integer> switchChance = sgChances.add(new IntSetting.Builder()
        .name("switch-chance")
        .description("Percent chance to actually change slot once the delay is up.")
        .defaultValue(100)
        .min(0)
        .max(100)
        .sliderRange(0, 100)
        .build()
    );

    private final Setting<Integer> glowstoneChance = sgChances.add(new IntSetting.Builder()
        .name("glowstone-chance")
        .description("Percent chance to actually charge once the delay is up.")
        .defaultValue(100)
        .min(0)
        .max(100)
        .sliderRange(0, 100)
        .build()
    );

    private final Setting<Integer> explodeChance = sgChances.add(new IntSetting.Builder()
        .name("explode-chance")
        .description("Percent chance to actually detonate once the delay is up.")
        .defaultValue(100)
        .min(0)
        .max(100)
        .sliderRange(0, 100)
        .build()
    );

    private final Set<BlockPos> ownedAnchors = new HashSet<>();
    private final Random random = new Random();

    private int switchClock;
    private int glowstoneClock;
    private int explodeClock;

    public AnchorMacro() {
        super(GlazedAddon.pvp, "anchor-macro", "Charges and blows up respawn anchors while you hold use.");
    }

    @Override
    public void onActivate() {
        resetClocks();
    }

    @Override
    public void onDeactivate() {
        resetClocks();
        ownedAnchors.clear();
    }

    private void resetClocks() {
        switchClock = 0;
        glowstoneClock = 0;
        explodeClock = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null || mc.gui.screen() != null) return;

        if (!isUseHeld()) {
            resetClocks();
            return;
        }

        if (!whileUse.get() && (mc.player.isUsingItem() || isGoodTool(mc.player.getOffhandItem()))) return;
        if (lootProtect.get() && (bodyNearby() || valuablesNearby())) return;

        if (!(mc.hitResult instanceof BlockHitResult hit)) return;

        BlockPos pos = hit.getBlockPos();
        if (!BlockUtil.isBlockAtPosition(pos, Blocks.RESPAWN_ANCHOR)) return;
        if (onlyOwn.get() && !ownedAnchors.contains(pos)) return;

        mc.options.keyUse.setDown(false);

        int wanted = oneGlowstone.get() ? 1 : 4;
        if (charges(pos) < wanted) {
            charge();
            return;
        }

        detonate(pos);
    }

    private void charge() {
        if (roll() > placeChance.get()) return;

        // idk
        if (!mc.player.getMainHandItem().is(Items.GLOWSTONE)) {
            if (switchClock < switchDelay.get()) {
                switchClock++;
                return;
            }
            if (roll() <= switchChance.get()) {
                switchClock = 0;
                swapTo(Items.GLOWSTONE);
            }
            return;
        }

        if (glowstoneClock < glowstoneDelay.get()) {
            glowstoneClock++;
            return;
        }
        if (roll() <= glowstoneChance.get()) {
            glowstoneClock = 0;
            rightClick();
        }
    }

    private void detonate(BlockPos pos) {
        int slot = explodeSlot.get() - 1;

        if (VersionUtil.getSelectedSlot(mc.player) != slot) {
            if (switchClock < switchDelay.get()) {
                switchClock++;
                return;
            }
            if (roll() <= switchChance.get()) {
                switchClock = 0;
                VersionUtil.setSelectedSlot(mc.player, slot);
            }
            return;
        }

        if (explodeClock < explodeDelay.get()) {
            explodeClock++;
            return;
        }
        if (roll() > explodeChance.get()) return;

        explodeClock = 0;
        if (onlyCharge.get()) return;

        rightClick();
        ownedAnchors.remove(pos);
        if (switchBack.get()) swapTo(Items.RESPAWN_ANCHOR);
    }

    @EventHandler
    private void onItemUse(DoItemUseEvent event) {
        if (mc.player == null || mc.level == null) return;
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = hit.getBlockPos();

        if (mc.player.getMainHandItem().is(Items.RESPAWN_ANCHOR)) {
            Direction side = hit.getDirection();
            ownedAnchors.add(mc.level.getBlockState(pos).canBeReplaced() ? pos : pos.relative(side));
        }
    }

    // meh
    private void rightClick() {
        ((IMinecraft) mc).meteor$rightClick();
    }

    private void swapTo(Item item) {
        FindItemResult found = InvUtils.findInHotbar(item);
        if (found.found()) VersionUtil.setSelectedSlot(mc.player, found.slot());
    }

    private int roll() {
        return random.nextInt(100) + 1;
    }

    private boolean isUseHeld() {
        InputConstants.Key bound = InputConstants.getKey(mc.options.keyUse.saveString());
        if (bound == null || bound.equals(InputConstants.UNKNOWN)) return false;
        return KeyUtils.isKeyPressed(bound.getValue());
    }

    private int charges(BlockPos pos) {
        if (mc.level == null) return 0;

        BlockState state = mc.level.getBlockState(pos);
        if (!state.is(Blocks.RESPAWN_ANCHOR)) return 0;
        return state.getValue(RespawnAnchorBlock.CHARGE);
    }

    private boolean isGoodTool(ItemStack stack) {
        if (stack.isEmpty()) return false;

        boolean tool = stack.is(ItemTags.SWORDS) || stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.AXES)
            || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES);
        if (!tool) return false;

        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        return id.contains("diamond") || id.contains("netherite");
    }

    private boolean bodyNearby() {
        if (mc.level == null || mc.player == null) return false;

        for (Entity entity : mc.level.players()) {
            if (entity == mc.player) continue;
            if (entity.distanceToSqr(mc.player) > 36.0) continue;
            if (entity instanceof LivingEntity living && living.isDeadOrDying()) return true;
        }
        return false;
    }

    private boolean valuablesNearby() {
        if (mc.level == null || mc.player == null) return false;

        AABB area = new AABB(
            mc.player.getX() - 10.0, mc.player.getY() - 5.0, mc.player.getZ() - 10.0,
            mc.player.getX() + 10.0, mc.player.getY() + 5.0, mc.player.getZ() + 10.0
        );

        int goodArmor = 0;
        for (Entity entity : mc.level.getEntities(null, area)) {
            if (!(entity instanceof ItemEntity item)) continue;

            ItemStack stack = item.getItem();
            if (stack.isEmpty()) continue;

            if (isArmor(stack)) {
                String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
                if (id.startsWith("netherite_") || id.startsWith("diamond_")) goodArmor++;
                continue;
            }

            if (stack.getCount() > 32 && (stack.is(Items.END_CRYSTAL) || stack.is(Items.OBSIDIAN)
                || stack.is(Items.ENCHANTED_GOLDEN_APPLE) || stack.is(Items.EXPERIENCE_BOTTLE))) {
                return true;
            }
        }

        return goodArmor >= 2;
    }

    private boolean isArmor(ItemStack stack) {
        return stack.is(ItemTags.HEAD_ARMOR) || stack.is(ItemTags.CHEST_ARMOR)
            || stack.is(ItemTags.LEG_ARMOR) || stack.is(ItemTags.FOOT_ARMOR);
    }
}
