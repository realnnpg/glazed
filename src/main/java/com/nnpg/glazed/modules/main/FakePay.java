package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import com.nnpg.glazed.utils.MoneyFmt;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import java.util.Locale;

public class FakePay extends Module {
    private static final int WHITE = 0xFFFFFF;
    private static final int GREEN = 0x00FC00;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> sound = sgGeneral.add(new BoolSetting.Builder()
        .name("sound")
        .description("Play the hit marker ding.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> actionBar = sgGeneral.add(new BoolSetting.Builder()
        .name("action-bar")
        .description("Also show it above the hotbar.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> removeFromScoreboard = sgGeneral.add(new BoolSetting.Builder()
        .name("remove-from-scoreboard")
        .description("Take the amount off the money line on the sidebar.")
        .defaultValue(false)
        .build()
    );

    // yea
    private volatile double spent;

    public FakePay() {
        super(GlazedAddon.CATEGORY, "fake-pay", "Fakes /pay instead of sending it.");
    }

    @Override
    public void onDeactivate() {
        spent = 0.0;
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        spent = 0.0;

        FakeScoreboard board = Modules.get().get(FakeScoreboard.class);
        if (board != null) board.refresh();
    }

    public boolean removesFromScoreboard() {
        return removeFromScoreboard.get();
    }

    public double spentOffset() {
        return spent;
    }

    // gng
    public boolean handlePay(String command) {
        if (command == null || mc.player == null) return false;

        String[] parts = command.trim().split("\\s+");
        if (parts.length < 3 || !"pay".equals(parts[0].toLowerCase(Locale.ROOT))) return false;

        String user = parts[1];
        Double amount = MoneyFmt.parse(parts[2]);
        if (amount == null || amount <= 0) return false;

        MutableComponent message = Component.literal("You paid " + user + " ").withStyle(s -> s.withColor(WHITE))
            .append(Component.literal("$").withStyle(s -> s.withColor(GREEN)))
            .append(Component.literal(" " + MoneyFmt.format(amount)).withStyle(s -> s.withColor(WHITE)));

        mc.player.displayClientMessage(message, false);
        if (actionBar.get()) mc.player.displayClientMessage(message, true);
        if (sound.get()) mc.player.playSound(SoundEvents.ARROW_HIT_PLAYER, 1.0f, 1.0f);

        if (removeFromScoreboard.get()) {
            spent += amount;

            FakeScoreboard board = Modules.get().get(FakeScoreboard.class);
            if (board != null) board.refresh();
        }

        return true;
    }
}
