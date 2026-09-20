package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.utils.GlazedScheduler;
import com.nnpg.glazed.utils.GlazedWebhook;
import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.MeteorToast;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class PlayerDetection extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgwhitelist = settings.createGroup("Whitelist");
    private final SettingGroup sgwebhook = settings.createGroup("Webhook");
    private final SettingGroup sgPanicPay = settings.createGroup("Panic Pay");

    // some freecam mods spawn a fake player with this name
    private static final Set<String> PERMANENT_WHITELIST = new HashSet<>(Arrays.asList(
        "FreeCamera"
    ));

    private final Setting<Boolean> notifications = sgGeneral.add(new BoolSetting.Builder()
        .name("notifications")
        .description("Show chat feedback.")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<String>> userWhitelist = sgwhitelist.add(new StringListSetting.Builder()
        .name("User Whitelist")
        .description("List of player names to ignore")
        .defaultValue(new ArrayList<>())
        .build()
    );

    private final Setting<List<Module>> modulesToToggle = sgGeneral.add(new ModuleListSetting.Builder()
        .name("Modules To Toggle")
        .description("Select modules to toggle when a non-whitelisted player is detected")
        .defaultValue(new ArrayList<>())
        .build()
    );

    private final Setting<Boolean> enableWebhook = sgwebhook.add(new BoolSetting.Builder()
        .name("Webhook")
        .description("Send webhook notifications when players are detected")
        .defaultValue(false)
        .build()
    );

    private final Setting<String> webhookUrl = sgwebhook.add(new StringSetting.Builder()
        .name("Webhook URL")
        .description("Discord webhook URL")
        .defaultValue("")
        .visible(enableWebhook::get)
        .build()
    );

    private final Setting<Boolean> selfPing = sgwebhook.add(new BoolSetting.Builder()
        .name("Self Ping")
        .description("Ping yourself in the webhook message")
        .defaultValue(false)
        .visible(enableWebhook::get)
        .build()
    );

    private final Setting<String> discordId = sgwebhook.add(new StringSetting.Builder()
        .name("Discord ID")
        .description("Your Discord user ID for pinging")
        .defaultValue("")
        .visible(() -> enableWebhook.get() && selfPing.get())
        .build()
    );

    private final Setting<Boolean> enableDisconnect = sgGeneral.add(new BoolSetting.Builder()
        .name("Disconnect")
        .description("Automatically disconnect when players are detected")
        .defaultValue(true)
        .build()
    );

    private final Setting<Mode> notificationMode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("notification-mode")
        .description("How to notify when players are detected")
        .defaultValue(Mode.Both)
        .build()
    );

    private final Setting<Boolean> toggleonplayer = sgGeneral.add(new BoolSetting.Builder()
        .name("Toggle when a player is detected")
        .description("Automatically toggles THIS module when a player is detected")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> enablePanicPay = sgPanicPay.add(new BoolSetting.Builder()
        .name("Enable Panic Pay")
        .description("Automatically send specified amount of money to target player when non-whitelisted player detected")
        .defaultValue(false)
        .build()
    );

    private final Setting<String> panicPayTarget = sgPanicPay.add(new StringSetting.Builder()
        .name("Target Player")
        .description("Player to send money to when panic pay is triggered")
        .defaultValue("")
        .visible(enablePanicPay::get)
        .build()
    );

    private final Setting<String> panicPayAmount = sgPanicPay.add(new StringSetting.Builder()
        .name("Amount")
        .description("Amount of money to send (e.g., 1000, 500.50)")
        .defaultValue("")
        .visible(enablePanicPay::get)
        .build()
    );

    private final Set<String> detectedPlayers = new HashSet<>();

    public PlayerDetection() {
        super(GlazedAddon.CATEGORY, "player-detection", "Detects when players are in the world");
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.level == null) return;

        Set<String> currentPlayers = new HashSet<>();
        String currentPlayerName = mc.player.getGameProfile().name();

        Set<String> fullWhitelist = new HashSet<>(PERMANENT_WHITELIST);
        fullWhitelist.addAll(userWhitelist.get());

        // only SpawnerProtect used to respect this, so admins kept setting us off
        AdminList adminList = Modules.get().get(AdminList.class);

        for (Player player : mc.level.players()) {
            if (player == mc.player) continue;

            String playerName = player.getGameProfile().name();
            if (playerName.equals(currentPlayerName)) continue;

            if (fullWhitelist.contains(playerName)) {
                continue;
            }

            if (adminList != null && adminList.isAdmin(playerName)) {
                continue;
            }

            currentPlayers.add(playerName);
        }

        if (!currentPlayers.isEmpty() && !currentPlayers.equals(detectedPlayers)) {
            detectedPlayers.clear();
            detectedPlayers.addAll(currentPlayers);

            handlePlayerDetection(currentPlayers);
        } else if (currentPlayers.isEmpty()) {
            detectedPlayers.clear();
        }
    }

    private void handlePlayerDetection(Set<String> players) {
        String playerList = String.join(", ", players);

        switch (notificationMode.get()) {
            case Chat -> { if (notifications.get()) info("Player(s) detected: (highlight)%s", playerList); }
            case Toast -> mc.gui.toastManager().addToast(new MeteorToast.Builder(title).text("Player Detected!").icon(Items.PLAYER_HEAD).build());
            case Both -> {
                if (notifications.get()) info("Player(s) detected: (highlight)%s", playerList);
                mc.gui.toastManager().addToast(new MeteorToast.Builder(title).text("Player Detected!").icon(Items.PLAYER_HEAD).build());
            }
        }

        ChatUtils.sendPlayerMsg("#stop");

        for (Module m : modulesToToggle.get()) {
            m.toggle();
            if (notifications.get()) info("Toggled module: (highlight)%s", m.title);
        }

        if (enablePanicPay.get()) {
            String target = panicPayTarget.get().trim();
            String amount = panicPayAmount.get().trim();

            if (!target.isEmpty() && !amount.isEmpty()) {
                String payCommand = String.format("/pay %s %s", target, amount);
                ChatUtils.sendPlayerMsg(payCommand);
                if (notifications.get()) info("Panic pay executed: sent %s to %s", amount, target);
            } else if (target.isEmpty()) {
                if (notifications.get()) warning("Panic pay target not set!");
            } else if (amount.isEmpty()) {
                if (notifications.get()) warning("Panic pay amount not set!");
            }
        }

        if (enableWebhook.get()) {
            sendWebhookNotification(players);
        }

        if (toggleonplayer.get()) {
            toggle();
        }

        if (enableDisconnect.get()) {
            GlazedScheduler.schedule(() -> disconnectFromServer(playerList), 500, TimeUnit.MILLISECONDS);
        }
    }

    private void sendWebhookNotification(Set<String> players) {
        if (webhookUrl.get().trim().isEmpty()) {
            if (notifications.get()) warning("Webhook URL not configured!");
            return;
        }

        String serverInfo = mc.getCurrentServer() != null ?
            mc.getCurrentServer().ip : "Unknown Server";

        GlazedWebhook.Builder webhook = GlazedWebhook.to(webhookUrl.get())
            .ping(selfPing.get() ? discordId.get() : null)
            .title("🚨 Player Detection Alert")
            .description("Player(s) detected on server!")
            .color(15158332)
            .field("Players", String.join(", ", players), false)
            .field("Server", serverInfo, true)
            .field("Time", "<t:" + (System.currentTimeMillis() / 1000) + ":R>", true);

        if (enablePanicPay.get() && !panicPayTarget.get().trim().isEmpty() && !panicPayAmount.get().trim().isEmpty()) {
            webhook.field("Panic Pay", String.format("Activated - Target: %s, Amount: %s",
                panicPayTarget.get().trim(), panicPayAmount.get().trim()), true);
        }

        webhook
            .onError(message -> {
                if (notifications.get()) error("Failed to send webhook: " + message);
            })
            .send();
    }

    private void disconnectFromServer(String playerList) {
        if (mc.level != null && mc.getConnection() != null) {
            String reason = "Player(s) detected: " + playerList;
            mc.getConnection().getConnection().disconnect(Component.literal(reason));
            if (notifications.get()) info("Disconnected from server - " + reason);
        }
    }

    @Override
    public void onActivate() {
        detectedPlayers.clear();
    }

    @Override
    public void onDeactivate() {
        detectedPlayers.clear();
    }

    @Override
    public String getInfoString() {
        return detectedPlayers.isEmpty() ? null : String.valueOf(detectedPlayers.size());
    }

    public enum Mode {
        Chat,
        Toast,
        Both
    }
}
