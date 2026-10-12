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
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.item.Items;
import java.util.HashSet;
import java.util.Set;

public class LamaESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgwebhook = settings.createGroup("Webhook");

    private final Setting<Boolean> showTracers = sgRender.add(new BoolSetting.Builder()
        .name("Show Tracers")
        .description("Draw tracer lines to llamas")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> tracerColor = sgRender.add(new ColorSetting.Builder()
        .name("Tracer Color")
        .description("Color of the tracer lines")
        .defaultValue(new SettingColor(255, 165, 0, 127))
        .visible(showTracers::get)
        .build()
    );

    private final Setting<Boolean> enableWebhook = sgwebhook.add(new BoolSetting.Builder()
        .name("Webhook")
        .description("Send webhook notifications when llamas are detected")
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
        .description("Automatically disconnect when llamas are detected")
        .defaultValue(false)
        .build()
    );

    private final Setting<Mode> notificationMode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("notification-mode")
        .description("How to notify when llamas are detected")
        .defaultValue(Mode.Both)
        .build()
    );

    private final Setting<Boolean> toggleOnFind = sgGeneral.add(new BoolSetting.Builder()
        .name("Toggle when found")
        .description("Automatically toggles the module when a llama is detected")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> notifications = sgGeneral.add(new BoolSetting.Builder().name("notifications").description("Show chat feedback.").defaultValue(true).build());

    private final Set<Integer> detectedLlamas = new HashSet<>();

    public LamaESP() {
        super(GlazedAddon.esp, "lama-esp", "Detects llamas in the world");
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.level == null) return;

        Set<Integer> currentLlamas = new HashSet<>();

        for (net.minecraft.world.entity.Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof Llama) {
                Llama llama = (Llama) entity;
                currentLlamas.add(entity.getId());

                if (showTracers.get()) {
                    double x = VersionUtil.getPrevX(entity) + (entity.getX() - VersionUtil.getPrevX(entity)) * event.tickDelta;
                    double y = VersionUtil.getPrevY(entity) + (entity.getY() - VersionUtil.getPrevY(entity)) * event.tickDelta;
                    double z = VersionUtil.getPrevZ(entity) + (entity.getZ() - VersionUtil.getPrevZ(entity)) * event.tickDelta;

                    double height = llama.getBoundingBox().maxY - llama.getBoundingBox().minY;
                    y += height / 2;

                    Color color = new Color(tracerColor.get());
                    event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z, x, y, z, color);
                }
            }
        }

        if (!currentLlamas.isEmpty() && !currentLlamas.equals(detectedLlamas)) {
            Set<Integer> newLlamas = new HashSet<>(currentLlamas);
            newLlamas.removeAll(detectedLlamas);

            if (!newLlamas.isEmpty()) {
                detectedLlamas.addAll(newLlamas);
                handleLlamaDetection(newLlamas.size());
            }
        } else if (currentLlamas.isEmpty()) {
            detectedLlamas.clear();
        }
    }

    private void handleLlamaDetection(int llamaCount) {
        String message = llamaCount == 1 ?
            "Llama detected!" :
            String.format("%d llamas detected!", llamaCount);

        switch (notificationMode.get()) {
            case Chat -> { if (notifications.get()) info("(highlight)%s", message); }
            case Toast -> mc.gui.toastManager().addToast(new MeteorToast.Builder(title).text(message).icon(Items.LEAD).build());
            case Both -> {
                if (notifications.get()) info("(highlight)%s", message);
                mc.gui.toastManager().addToast(new MeteorToast.Builder(title).text(message).icon(Items.LEAD).build());
            }
        }

        if (enableWebhook.get()) {
            sendWebhookNotification(llamaCount);
        }

        if (toggleOnFind.get()) {
            toggle();
        }

        if (enableDisconnect.get()) {
            disconnectFromServer(message);
        }
    }

    private void sendWebhookNotification(int llamaCount) {
        if (webhookUrl.get().trim().isEmpty()) {
            if (notifications.get()) warning("Webhook URL not configured!");
            return;
        }

        String serverInfo = mc.getCurrentServer() != null ?
            mc.getCurrentServer().ip : "Unknown Server";

        String llamaText = llamaCount == 1 ? "llama" : "llamas";

        String coordinates = "Unknown";
        if (mc.player != null) {
            coordinates = String.format("X: %.0f, Y: %.0f, Z: %.0f",
                mc.player.getX(), mc.player.getY(), mc.player.getZ());
        }

        String avatar = "https://minecraft.wiki/images/f/f4/Llama_BE2.png";

        GlazedWebhook.to(webhookUrl.get())
            .username("LamaESP")
            .avatar(avatar)
            .ping(selfPing.get() ? discordId.get() : null)
            .title("🦙 Llama Alert")
            .description(String.format("%d %s detected!", llamaCount, llamaText))
            .color(16753920)
            .thumbnail(avatar)
            .field("Count", String.valueOf(llamaCount), true)
            .field("Server", serverInfo, true)
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
        detectedLlamas.clear();
    }

    @Override
    public void onDeactivate() {
        detectedLlamas.clear();
    }

    @Override
    public String getInfoString() {
        return detectedLlamas.isEmpty() ? null : String.valueOf(detectedLlamas.size());
    }

    public enum Mode {
        Chat,
        Toast,
        Both
    }
}
