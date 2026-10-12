package com.nnpg.glazed.modules.esp;

import com.nnpg.glazed.utils.GlazedWebhook;
import com.nnpg.glazed.GlazedAddon;
import com.nnpg.glazed.VersionUtil;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.MeteorToast;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Items;
import java.util.HashSet;
import java.util.Set;

public class VillagerESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgwebhook = settings.createGroup("Webhook");

    private final Setting<DetectionMode> detectionMode = sgGeneral.add(new EnumSetting.Builder<DetectionMode>()
        .name("Detection Mode")
        .description("What type of villagers to detect")
        .defaultValue(DetectionMode.Both)
        .build()
    );

    private final Setting<Boolean> showTracers = sgRender.add(new BoolSetting.Builder()
        .name("Show Tracers")
        .description("Draw tracer lines to villagers")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> villagerTracerColor = sgRender.add(new ColorSetting.Builder()
        .name("Villager Tracer Color")
        .description("Color of the tracer lines for regular villagers")
        .defaultValue(new SettingColor(0, 255, 0, 127))
        .visible(() -> showTracers.get() && (detectionMode.get() == DetectionMode.Villagers || detectionMode.get() == DetectionMode.Both))
        .build()
    );

    private final Setting<SettingColor> zombieVillagerTracerColor = sgRender.add(new ColorSetting.Builder()
        .name("Zombie Villager Tracer Color")
        .description("Color of the tracer lines for zombie villagers")
        .defaultValue(new SettingColor(255, 0, 0, 127))
        .visible(() -> showTracers.get() && (detectionMode.get() == DetectionMode.ZombieVillagers || detectionMode.get() == DetectionMode.Both))
        .build()
    );

    private final Setting<Boolean> enableWebhook = sgwebhook.add(new BoolSetting.Builder()
        .name("Webhook")
        .description("Send webhook notifications when villagers are detected")
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
        .description("Automatically disconnect when villagers are detected")
        .defaultValue(false)
        .build()
    );

    private final Setting<Mode> notificationMode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("notification-mode")
        .description("How to notify when villagers are detected")
        .defaultValue(Mode.Both)
        .build()
    );

    private final Setting<Boolean> toggleOnFind = sgGeneral.add(new BoolSetting.Builder()
        .name("Toggle when found")
        .description("Automatically toggles the module when villagers are detected")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> notifications = sgGeneral.add(new BoolSetting.Builder().name("notifications").description("Show chat feedback.").defaultValue(true).build());

    private final Set<Integer> detectedVillagers = new HashSet<>();

    public VillagerESP() {
        super(GlazedAddon.esp, "villager-esp", "Detects villagers and zombie villagers in the world");
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.level == null) return;

        Set<Integer> currentVillagers = new HashSet<>();
        int villagerCount = 0;
        int zombieVillagerCount = 0;

        for (net.minecraft.world.entity.Entity entity : mc.level.entitiesForRendering()) {
            boolean shouldDetect = false;
            Color tracerColor = null;

            if (entity instanceof Villager && (detectionMode.get() == DetectionMode.Villagers || detectionMode.get() == DetectionMode.Both)) {
                shouldDetect = true;
                tracerColor = new Color(villagerTracerColor.get());
                villagerCount++;
            } else if (entity instanceof ZombieVillager && (detectionMode.get() == DetectionMode.ZombieVillagers || detectionMode.get() == DetectionMode.Both)) {
                shouldDetect = true;
                tracerColor = new Color(zombieVillagerTracerColor.get());
                zombieVillagerCount++;
            }

            if (shouldDetect) {
                currentVillagers.add(entity.getId());

                if (showTracers.get()) {
                    double x = VersionUtil.getPrevX(entity) + (entity.getX() - VersionUtil.getPrevX(entity)) * event.tickDelta;
                    double y = VersionUtil.getPrevY(entity) + (entity.getY() - VersionUtil.getPrevY(entity)) * event.tickDelta;
                    double z = VersionUtil.getPrevZ(entity) + (entity.getZ() - VersionUtil.getPrevZ(entity)) * event.tickDelta;

                    double height = entity.getBoundingBox().maxY - entity.getBoundingBox().minY;
                    y += height / 2;

                    event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z, x, y, z, tracerColor);
                }
            }
        }

        if (!currentVillagers.isEmpty() && !currentVillagers.equals(detectedVillagers)) {
            Set<Integer> newVillagers = new HashSet<>(currentVillagers);
            newVillagers.removeAll(detectedVillagers);

            if (!newVillagers.isEmpty()) {
                detectedVillagers.addAll(newVillagers);
                handleVillagerDetection(villagerCount, zombieVillagerCount);
            }
        } else if (currentVillagers.isEmpty()) {
            detectedVillagers.clear();
        }
    }

    private void handleVillagerDetection(int villagerCount, int zombieVillagerCount) {
        String message = buildDetectionMessage(villagerCount, zombieVillagerCount);

        switch (notificationMode.get()) {
            case Chat -> { if (notifications.get()) info("(highlight)%s", message); }
            case Toast -> mc.gui.toastManager().addToast(new MeteorToast.Builder(title).text(message).icon(Items.EMERALD).build());
            case Both -> {
                if (notifications.get()) info("(highlight)%s", message);
                mc.gui.toastManager().addToast(new MeteorToast.Builder(title).text(message).icon(Items.EMERALD).build());
            }
        }

        if (enableWebhook.get()) {
            sendWebhookNotification(villagerCount, zombieVillagerCount);
        }

        if (toggleOnFind.get()) {
            toggle();
        }

        if (enableDisconnect.get()) {
            disconnectFromServer(message);
        }
    }

    private String buildDetectionMessage(int villagerCount, int zombieVillagerCount) {
        int totalCount = villagerCount + zombieVillagerCount;

        if (detectionMode.get() == DetectionMode.Villagers) {
            return villagerCount == 1 ? "Villager detected!" : String.format("%d villagers detected!", villagerCount);
        } else if (detectionMode.get() == DetectionMode.ZombieVillagers) {
            return zombieVillagerCount == 1 ? "Zombie villager detected!" : String.format("%d zombie villagers detected!", zombieVillagerCount);
        } else {
            if (villagerCount > 0 && zombieVillagerCount > 0) {
                return String.format("%d villagers and %d zombie villagers detected!", villagerCount, zombieVillagerCount);
            } else if (villagerCount > 0) {
                return villagerCount == 1 ? "Villager detected!" : String.format("%d villagers detected!", villagerCount);
            } else {
                return zombieVillagerCount == 1 ? "Zombie villager detected!" : String.format("%d zombie villagers detected!", zombieVillagerCount);
            }
        }
    }

    private void sendWebhookNotification(int villagerCount, int zombieVillagerCount) {
        if (webhookUrl.get().trim().isEmpty()) {
            if (notifications.get()) warning("Webhook URL not configured!");
            return;
        }

        String serverInfo = mc.getCurrentServer() != null ?
            mc.getCurrentServer().ip : "Unknown Server";

        String coordinates = "Unknown";
        if (mc.player != null) {
            coordinates = String.format("X: %.0f, Y: %.0f, Z: %.0f",
                mc.player.getX(), mc.player.getY(), mc.player.getZ());
        }

        String avatar = "https://i.imgur.com/OL2y1cr.png";

        GlazedWebhook.Builder webhook = GlazedWebhook.to(webhookUrl.get())
            .username("VillagerESP")
            .ping(selfPing.get() ? discordId.get() : null)
            .title("🏘️ Villager Alert")
            .description(buildDetectionMessage(villagerCount, zombieVillagerCount))
            .color(65280)
            .thumbnail(avatar)
            .field("Server", serverInfo, true);

        if (villagerCount > 0) webhook.field("Villagers", String.valueOf(villagerCount), true);
        if (zombieVillagerCount > 0) webhook.field("Zombie Villagers", String.valueOf(zombieVillagerCount), true);

        webhook
            .field("Coordinates", coordinates, false)
            .field("Time", "<t:" + (System.currentTimeMillis() / 1000) + ":R>", true)
            .onError(message -> {
                if (notifications.get()) error("Failed to send webhook: " + message);
            })
            .send();
    }

    private void disconnectFromServer(String reason) {
        if (mc.level != null && mc.getConnection() != null) {
            mc.getConnection().getConnection().disconnect(Component.literal(reason));
            if (notifications.get()) info("Disconnected from server - " + reason);
        }
    }

    @Override
    public void onActivate() {
        detectedVillagers.clear();
    }

    @Override
    public void onDeactivate() {
        detectedVillagers.clear();
    }

    @Override
    public String getInfoString() {
        return detectedVillagers.isEmpty() ? null : String.valueOf(detectedVillagers.size());
    }

    public enum Mode {
        Chat,
        Toast,
        Both
    }

    public enum DetectionMode {
        Villagers("Villagers"),
        ZombieVillagers("Zombie Villagers"),
        Both("Both");

        private final String name;

        DetectionMode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
