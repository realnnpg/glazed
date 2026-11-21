package com.nnpg.glazed.modules.pvp;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public class ShieldBreaker extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    
    private final Setting<Boolean> autoBreak = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-break")
        .description("Automatically break shields without requiring clicks")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> returnToPrevSlot = sgGeneral.add(new BoolSetting.Builder()
        .name("return-to-prev-slot")
        .description("Return to the previous slot after breaking shield instead of a specific weapon slot")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> weaponSlot = sgGeneral.add(new IntSetting.Builder()
        .name("weapon-slot")
        .description("The hotbar slot to switch back to after breaking shield (1-9)")
        .defaultValue(1)
        .range(0, 9)
        .sliderRange(1, 9)
        .visible(() -> !returnToPrevSlot.get())
        .build()
    );
    
    private final Setting<Integer> attackDelay = sgGeneral.add(new IntSetting.Builder()
        .name("attack-delay")
        .description("Delay in ticks between shield break and weapon switch")
        .defaultValue(0)
        .range(0, 40)
        .sliderRange(1, 20)
        .build()
    );
    
    private final Setting<Integer> killDelay = sgGeneral.add(new IntSetting.Builder()
        .name("kill-delay")  
        .description("Delay in ticks between weapon switch and kill attack")
        .defaultValue(1)
        .range(0, 40)
        .sliderRange(1, 20)
        .build()
    );

    private final Setting<Integer> axeSwitchDelay = sgGeneral.add(new IntSetting.Builder()
        .name("axe-switch-delay")
        .description("Delay in ticks to ensure axe switch is completed")
        .defaultValue(0)
        .range(0, 20)
        .sliderRange(1, 10)
        .build()
    );

    private final Setting<Integer> manualShieldBreakDelay = sgGeneral.add(new IntSetting.Builder()
        .name("manual-shield-break-delay")
        .description("Delay in ticks before switching back in manual mode")
        .defaultValue(1)
        .range(0, 20)
        .sliderRange(1, 10)
        .build()
    );

        private final Setting<Integer> cycleCooldown = sgGeneral.add(new IntSetting.Builder()
            .name("cycle-cooldown")
            .description("Delay in ticks before starting the next shield break cycle.")
            .defaultValue(4)
            .range(0, 20)
            .sliderRange(0, 10)
            .build()
        );

    private final Setting<Integer> weaponSwitchDelay = sgGeneral.add(new IntSetting.Builder()
        .name("weapon-switch-delay")
        .description("Delay in ticks to ensure weapon switch is completed")
        .defaultValue(0)
        .range(0, 20)
        .sliderRange(1, 10)
        .build()
    );
    
    private final Setting<Boolean> onlyPlayers = sgGeneral.add(new BoolSetting.Builder()
        .name("only-players")
        .description("Only break shields of players, not other entities")
        .defaultValue(true)
        .build()
    );
    
    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Maximum range to detect shield usage")
        .defaultValue(6.0)
        .range(0.0, 10.0)
        .sliderRange(1.0, 6.0)
        .build()
    );
    
    private final Setting<Boolean> chatInfo = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-info")
        .description("Send info messages to chat")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> killSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("kill-switch")
        .description("Enable auto attack after breaking shield")
        .defaultValue(true)
        .build()
    );

    private Player targetPlayer = null;
    private int originalSlot = -1;
    private int tickCounter = 0;
    private ShieldBreakerState state = ShieldBreakerState.IDLE;
    private boolean shieldBroken = false;
    private long lastBreakAttempt = 0;

        private int cooldownTicks = 0;
    
    private enum ShieldBreakerState {
        IDLE,
        SWITCHING_AXE,
        BREAKING,
        SWITCHING_BACK,
        KILLING
    }

    public ShieldBreaker() {
        super(GlazedAddon.pvp, "shield-breaker", "Automatically breaks player shields with axe then switches back to weapon for kill.");
    }

    @Override
    public void onActivate() {
        resetState();
        if (chatInfo.get()) info("Shield Breaker activated - aim at players using shields!");
    }

    @Override  
    public void onDeactivate() {
        resetState();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;

        if (mc.player.isUsingItem()) return;

        if (autoBreak.get()) {
                if (cooldownTicks > 0) {
                    cooldownTicks--;
                    return;
                }
            switch (state) {
                case IDLE -> checkForShieldUser();
                case SWITCHING_AXE -> handleAxeSwitch();
                case BREAKING -> handleShieldBreak();
                case SWITCHING_BACK -> handleWeaponSwitch();
                case KILLING -> handleKillAttack();
            }
        } else {
            checkForShieldUser();
        }
    }

    private void checkForShieldUser() {
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.ENTITY) {
            return;
        }

        EntityHitResult entityHit = (EntityHitResult) mc.hitResult;
        
        if (onlyPlayers.get() && !(entityHit.getEntity() instanceof Player)) {
            return;
        }
        
        if (entityHit.getEntity() instanceof Player player) {
            if (mc.player.distanceTo(player) > range.get()) {
                return;
            }
            
            if (isUsingShield(player)) {
                targetPlayer = player;
                boolean isAttacking = mc.options.keyAttack.isDown();

                if (!autoBreak.get() && isAttacking) {
                    originalSlot = com.nnpg.glazed.utils.InventoryUtils.getSelectedSlot(mc.player.getInventory());

                    FindItemResult axeResult = InvUtils.findInHotbar(itemStack -> itemStack.getItem() instanceof AxeItem);
                    
                    if (!axeResult.found()) {
                        if (chatInfo.get()) error("No axe found in hotbar!");
                        return;
                    }

                    if (chatInfo.get()) info("Shield detected! Breaking with axe");
                    
                    InvUtils.swap(axeResult.slot(), false);
                    mc.gameMode.attack(mc.player, player);
                    mc.player.swing(InteractionHand.MAIN_HAND);
                    
                    tickCounter = 0;
                    state = ShieldBreakerState.BREAKING;

                } else if (autoBreak.get()) {
                    if (originalSlot == -1) {
                        originalSlot = com.nnpg.glazed.utils.InventoryUtils.getSelectedSlot(mc.player.getInventory());
                    }
                    
                    FindItemResult axeResult = InvUtils.findInHotbar(itemStack -> itemStack.getItem() instanceof AxeItem);
                    
                    if (!axeResult.found()) {
                        if (chatInfo.get()) error("No axe found in hotbar!");
                        return;
                    }

                    if (chatInfo.get()) info("Shield detected! Breaking with axe");
                    InvUtils.swap(axeResult.slot(), false);
                    state = ShieldBreakerState.SWITCHING_AXE;
                    tickCounter = 0;
                }
            }
        }
    }

    private void handleAxeSwitch() {
        tickCounter++;
        
        if (tickCounter > axeSwitchDelay.get()) {
            if (!shieldBroken) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastBreakAttempt > 150) {
                    mc.gameMode.attack(mc.player, targetPlayer);
                    mc.player.swing(InteractionHand.MAIN_HAND);
                    lastBreakAttempt = currentTime;
                    shieldBroken = true;
                    
                    if (chatInfo.get()) info("Shield broken! Switching to weapon...");
                }
            }
            
            state = ShieldBreakerState.BREAKING;
            tickCounter = 0;
        }
    }

    private void handleShieldBreak() {
        tickCounter++;
        
        if (shieldBroken) {
            if (!autoBreak.get()) {
                if (originalSlot != -1) {
                    InvUtils.swap(originalSlot, false);
                    
                    if (targetPlayer != null && !targetPlayer.isRemoved() && mc.player.distanceTo(targetPlayer) <= range.get()) {
                        mc.gameMode.attack(mc.player, targetPlayer);
                        mc.player.swing(InteractionHand.MAIN_HAND);
                        if (chatInfo.get()) info("Attacking with original weapon!");
                    }
                    resetState();
                    return;
                }
            } else {
                if (returnToPrevSlot.get()) {
                    if (originalSlot != -1) {
                        InvUtils.swap(originalSlot, false);
                    }
                } else {
                    int weaponSlotIndex = weaponSlot.get() - 1;
                    InvUtils.swap(weaponSlotIndex, false);
                }
                state = ShieldBreakerState.SWITCHING_BACK;
                tickCounter = 0;
            }
        } else {
            if (tickCounter >= 3) {
                if (chatInfo.get()) error("Shield break failed, retrying...");
                state = ShieldBreakerState.IDLE;
                tickCounter = 0;
            }
        }
    }

    private void handleWeaponSwitch() {
        tickCounter++;
        
        if (tickCounter > weaponSwitchDelay.get()) {
            state = ShieldBreakerState.KILLING;
            tickCounter = 0;
        }
    }

    private void handleKillAttack() {
        tickCounter++;
        
        if (tickCounter > killDelay.get()) {
            if (killSwitch.get()) {
                if (targetPlayer != null && !targetPlayer.isRemoved()) {
                    mc.gameMode.attack(mc.player, targetPlayer);
                    mc.player.swing(InteractionHand.MAIN_HAND);
                    
                    if (chatInfo.get()) info("Kill attack executed!");
                }
            } else if (chatInfo.get()) {
                info("Shield broken - Kill switch disabled");
            }
            
            resetState();
            cooldownTicks = cycleCooldown.get();
        }
    }

    private boolean isUsingShield(Player player) {
        if (isPlayerBehindTarget(mc.player, player)) {
            if (chatInfo.get()) info("Cannot break shield from behind!");
            return false;
        }

        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem() == Items.SHIELD && player.isUsingItem() && player.getUsedItemHand() == InteractionHand.MAIN_HAND) {
            return true;
        }
        
        ItemStack offHand = player.getOffhandItem();
        if (offHand.getItem() == Items.SHIELD && player.isUsingItem() && player.getUsedItemHand() == InteractionHand.OFF_HAND) {
            return true;
        }
        
        return false;
    }

    private boolean isPlayerBehindTarget(Player source, Player target) {
        double dx = source.getX() - target.getX();
        double dz = source.getZ() - target.getZ();
        
        double angle = Math.toDegrees(Math.atan2(dz, dx));
        
        float targetYaw = target.getYRot() % 360;
        if (targetYaw < 0) targetYaw += 360;
        
        if (angle < 0) angle += 360;
        
        double angleDiff = Math.abs(angle - targetYaw);
        
        return angleDiff < 90 || angleDiff > 270;
    }

    private void resetState() {
        targetPlayer = null;
        originalSlot = -1;
        tickCounter = 0;
        state = ShieldBreakerState.IDLE;
        shieldBroken = false;
        lastBreakAttempt = 0;
    }

    @Override
    public String getInfoString() {
        return switch (state) {
            case IDLE -> null;
            case SWITCHING_AXE -> "Switching to Axe";
            case BREAKING -> "Breaking Shield";
            case SWITCHING_BACK -> "Switching to Weapon";
            case KILLING -> "Executing Kill";
        };
    }
}
