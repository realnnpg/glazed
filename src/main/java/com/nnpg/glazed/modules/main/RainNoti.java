package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.utils.GlazedWebhook;
import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.MeteorToast;
import meteordevelopment.orbit.EventHandler;

public class RainNoti extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgWebhook = settings.createGroup("Webhook");

    private final Setting<Boolean> sendNotifications = sgGeneral.add(new BoolSetting.Builder()
        .name("notifications")
        .description("Send in-game notifications when it starts raining.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Mode> notificationMode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("notification-mode")
        .description("The mode to use for notifications.")
        .defaultValue(Mode.Both)
        .visible(sendNotifications::get)
        .build()
    );

    private final Setting<Boolean> enableWebhook = sgWebhook.add(new BoolSetting.Builder()
        .name("webhook")
        .description("Send webhook notifications when it starts raining")
        .defaultValue(false)
        .build()
    );

    private final Setting<String> webhookUrl = sgWebhook.add(new StringSetting.Builder()
        .name("webhook-url")
        .description("Discord webhook URL")
        .defaultValue("")
        .visible(enableWebhook::get)
        .build()
    );

    private final Setting<Boolean> selfPing = sgWebhook.add(new BoolSetting.Builder()
        .name("self-ping")
        .description("Ping yourself in the webhook message")
        .defaultValue(false)
        .visible(enableWebhook::get)
        .build()
    );

    private final Setting<String> discordId = sgWebhook.add(new StringSetting.Builder()
        .name("discord-id")
        .description("Your Discord user ID for pinging")
        .defaultValue("")
        .visible(() -> enableWebhook.get() && selfPing.get())
        .build()
    );

    private boolean wasRaining = false;

    public RainNoti() {
        super(GlazedAddon.CATEGORY, "rain-noti", "Notifies when it starts raining in-game.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.level == null) return;

        boolean raining = mc.level.isRaining();
        if (raining && !wasRaining) {
            notifyRainStart();
        }
        wasRaining = raining;
    }

    private void notifyRainStart() {
        if (sendNotifications.get()) {
            switch (notificationMode.get()) {
                case Chat -> info("It has started raining!");
                case Toast -> mc.gui.toastManager().addToast(new MeteorToast.Builder("Weather Alert").text("It has started raining!").build());
                case Both -> {
                    info("It has started raining!");
                    mc.gui.toastManager().addToast(new MeteorToast.Builder("Weather Alert").text("It has started raining!").build());
                }
            }
        }

        if (enableWebhook.get()) sendWebhookNotification();
    }

    private void sendWebhookNotification() {
        if (webhookUrl.get().trim().isEmpty()) {
            warning("Webhook URL not configured!");
            return;
        }

        String serverInfo = mc.getCurrentServer() != null ?
            mc.getCurrentServer().ip : "Singleplayer";

        GlazedWebhook.to(webhookUrl.get())
            .username("RainNoti")
            .ping(selfPing.get() ? discordId.get() : null)
            .title("🌧 Rain Started!")
            .description("It has started raining in-game.")
            .color(3447003)
            .field("Server", serverInfo, true)
            .field("Time", "<t:" + (System.currentTimeMillis() / 1000) + ":R>", true)
            .footer("RainNoti")
            .onError(message -> error("Failed to send webhook: " + message))
            .send();
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();

        WButton reset = list.add(theme.button("Reset Raining State")).widget();
        reset.action = () -> {
            wasRaining = false;
            info("Rain state reset.");
        };

        return list;
    }

    public enum Mode {
        Chat,
        Toast,
        Both
    }
}
