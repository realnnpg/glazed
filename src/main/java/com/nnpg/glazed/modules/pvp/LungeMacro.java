package com.nnpg.glazed.modules.pvp;

import com.nnpg.glazed.GlazedAddon;
import com.nnpg.glazed.VersionUtil;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixin.MinecraftAccessor;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import java.util.Locale;
import java.util.Random;

public class LungeMacro extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSwapBack = settings.createGroup("Swap Back");

    private final Setting<Keybind> trigger = sgGeneral.add(new KeybindSetting.Builder()
        .name("trigger")
        .description("Key that fires a lunge.")
        .defaultValue(Keybind.none())
        .build()
    );

    private final Setting<Boolean> continuous = sgGeneral.add(new BoolSetting.Builder()
        .name("continuous")
        .description("Lunge on its own every time your main item's attack recharges.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> continuousDelay = sgGeneral.add(new IntSetting.Builder()
        .name("continuous-delay")
        .description("Milliseconds between auto lunges. Stops it swapping back and forth when the spear isn't charged.")
        .defaultValue(450)
        .min(0)
        .max(2000)
        .sliderRange(0, 2000)
        .visible(continuous::get)
        .build()
    );

    private final Setting<Boolean> swapBack = sgSwapBack.add(new BoolSetting.Builder()
        .name("swap-back")
        .description("Go back to whatever you were holding.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> swapBackDelay = sgSwapBack.add(new IntSetting.Builder()
        .name("swap-back-delay")
        .description("Ticks the spear stays in hand before swapping back.")
        .defaultValue(1)
        .min(1)
        .max(10)
        .sliderRange(1, 10)
        .visible(swapBack::get)
        .build()
    );

    private final Setting<Boolean> randomize = sgSwapBack.add(new BoolSetting.Builder()
        .name("randomize")
        .description("Add up to one extra tick to the hold, so it isn't the same number every time.")
        .defaultValue(false)
        .build()
    );

    private final Random random = new Random();

    private boolean swapping;
    private boolean triggerWasDown;
    private int previousSlot = -1;
    private int heldTicks;
    private int holdFor = 1;
    private long lastLunge;

    public LungeMacro() {
        super(GlazedAddon.pvp, "lunge-macro", "Swaps to a lunge spear, lunges, and swaps back.");
    }

    @Override
    public void onActivate() {
        swapping = false;
        triggerWasDown = false;
        previousSlot = -1;
        heldTicks = 0;
        lastLunge = 0L;
    }

    @Override
    public void onDeactivate() {
        swapping = false;
        previousSlot = -1;
        heldTicks = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) {
            swapping = false;
            return;
        }

        if (!swapping) {
            boolean down = mc.gui.screen() == null && trigger.get().isSet() && trigger.get().isPressed();
            boolean pressed = down && !triggerWasDown;
            triggerWasDown = down;

            boolean auto = continuous.get()
                && mc.gui.screen() == null
                && System.currentTimeMillis() - lastLunge >= continuousDelay.get()
                && mc.player.getAttackStrengthScale(0.0f) >= 1.0f;

            if (pressed || auto) lunge();
            return;
        }

        heldTicks++;
        if (heldTicks < holdFor) return;

        if (swapBack.get() && previousSlot != -1) equip(previousSlot);
        swapping = false;
    }

    private void lunge() {
        int spear = findSpear();
        if (spear == -1) return;

        lastLunge = System.currentTimeMillis();
        previousSlot = VersionUtil.getSelectedSlot(mc.player);

        holdFor = swapBackDelay.get();
        if (randomize.get()) holdFor += random.nextInt(2);

        equip(spear);
        swapping = true;
        // tbh
        heldTicks = 0;

        if (mc.player.getAttackStrengthScale(0.0f) >= 1.0f) {
            ((MinecraftAccessor) mc).meteor$leftClick();
        }
    }

    private int findSpear() {
        for (int i = 0; i < 9; i++) {
            if (isLungeSpear(mc.player.getInventory().getItem(i))) return i;
        }
        return -1;
    }

    private boolean isLungeSpear(ItemStack stack) {
        if (stack.isEmpty()) return false;

        boolean spear = stack.is(Items.TRIDENT)
            || stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains("spear");
        if (!spear) return false;

        ItemEnchantments enchants = stack.get(DataComponents.ENCHANTMENTS);
        if (enchants == null) return false;

        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchants.entrySet()) {
            Holder<Enchantment> enchantment = entry.getKey();

            String id = enchantment.unwrapKey().map(ResourceKey::identifier).map(Identifier::toString)
                .orElse("").toLowerCase(Locale.ROOT);
            if (id.contains("lunge")) return true;

            String name = Enchantment.getFullname(enchantment, entry.getIntValue()).getString().toLowerCase(Locale.ROOT);
            if (name.contains("lunge")) return true;
        }

        return false;
    }

    private void equip(int slot) {
        if (mc.player != null && slot >= 0 && slot <= 8) VersionUtil.setSelectedSlot(mc.player, slot);
    }
}
