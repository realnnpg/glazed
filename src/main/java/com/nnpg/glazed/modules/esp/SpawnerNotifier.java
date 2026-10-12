package com.nnpg.glazed.modules.esp;

import com.nnpg.glazed.utils.GlazedWebhook;
import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.MeteorToast;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SpawnerNotifier extends Module {
    private final SettingGroup sg_general = settings.getDefaultGroup();
    private final SettingGroup sg_notifications = settings.createGroup("Notifications");
    private final SettingGroup sg_webhook = settings.createGroup("Webhook");
    private final SettingGroup sg_render = settings.createGroup("Render");

    private final Setting<Boolean> show_coordinates = sg_general.add(new BoolSetting.Builder()
        .name("show-coordinates")
        .description("Show spawner coordinates in notifications.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> show_distance = sg_general.add(new BoolSetting.Builder()
        .name("show-distance")
        .description("Show distance to spawner in notifications.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Mode> notification_mode = sg_notifications.add(new EnumSetting.Builder<Mode>()
        .name("notification-mode")
        .description("The mode to use for notifications.")
        .defaultValue(Mode.Both)
        .build()
    );

    private final Setting<Boolean> disconnect_on_find = sg_notifications.add(new BoolSetting.Builder()
        .name("disconnect-on-find")
        .description("Disconnect when a spawner is found.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> notifications = sg_notifications.add(new BoolSetting.Builder().name("notifications").description("Show chat feedback.").defaultValue(true).build());

    private final Setting<Boolean> webhook_enabled = sg_webhook.add(new BoolSetting.Builder()
        .name("webhook-enabled")
        .description("Enable webhook notifications.")
        .defaultValue(false)
        .build()
    );

    private final Setting<String> webhook_url = sg_webhook.add(new StringSetting.Builder()
        .name("webhook-url")
        .description("Discord webhook URL.")
        .defaultValue("")
        .visible(() -> webhook_enabled.get())
        .build()
    );

    private final Setting<Boolean> self_ping = sg_webhook.add(new BoolSetting.Builder()
        .name("self-ping")
        .description("Ping yourself in the webhook message")
        .defaultValue(false)
        .visible(() -> webhook_enabled.get())
        .build()
    );

    private final Setting<String> discord_id = sg_webhook.add(new StringSetting.Builder()
        .name("discord-id")
        .description("Your Discord user ID for pinging")
        .defaultValue("")
        .visible(() -> webhook_enabled.get() && self_ping.get())
        .build()
    );

    private final Setting<Boolean> show_esp = sg_render.add(new BoolSetting.Builder()
        .name("show-esp")
        .description("Highlight spawners with ESP boxes.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> esp_color = sg_render.add(new ColorSetting.Builder()
        .name("esp-color")
        .description("Color for the ESP boxes")
        .defaultValue(new SettingColor(255, 0, 0, 100))
        .visible(() -> show_esp.get())
        .build()
    );

    private final Setting<ShapeMode> shape_mode = sg_render.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("Rendering mode for the ESP boxes")
        .defaultValue(ShapeMode.Both)
        .visible(() -> show_esp.get())
        .build()
    );

    private final Setting<Boolean> show_tracers = sg_render.add(new BoolSetting.Builder()
        .name("show-tracers")
        .description("Draw tracer lines to spawners")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> tracer_color = sg_render.add(new ColorSetting.Builder()
        .name("tracer-color")
        .description("Color for the tracer lines")
        .defaultValue(new SettingColor(255, 0, 0, 200))
        .visible(() -> show_tracers.get())
        .build()
    );

    private final Setting<Boolean> only_render_new = sg_render.add(new BoolSetting.Builder()
        .name("only-render-new")
        .description("Only render ESP/tracers for newly discovered spawners")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> render_distance = sg_render.add(new DoubleSetting.Builder()
        .name("render-distance")
        .description("Maximum distance to render ESP/tracers (0 = unlimited)")
        .defaultValue(0.0)
        .min(0.0)
        .max(500.0)
        .sliderMax(200.0)
        .build()
    );

    private final Set<ChunkPos> processed_chunks = new HashSet<>();
    private final Set<BlockPos> found_spawner_positions = new HashSet<>();
    private final Set<BlockPos> new_found_spawners = new HashSet<>();
    private int prune_ticks = 0;

    private int total_spawners_found = 0;

    public SpawnerNotifier() {
        super(GlazedAddon.esp, "spawner-notifier", "Notifies when spawners are detected with multiple notification options and visual ESP");
    }

    @Override
    public void onActivate() {
        processed_chunks.clear();
        found_spawner_positions.clear();
        new_found_spawners.clear();
        total_spawners_found = 0;
        if (notifications.get()) info("SpawnerNotifier activated!");
    }

    @Override
    public void onDeactivate() {
        if (notifications.get()) info("SpawnerNotifier deactivated. Found %d spawners total this session.", total_spawners_found);
    }

    @EventHandler
    private void on_chunk_data(ChunkDataEvent event) {
        if (mc.player == null || mc.level == null) return;

        ChunkPos chunk_pos = event.chunk().getPos();
        if (processed_chunks.contains(chunk_pos)) return;

        List<BlockPos> chunk_spawners = new ArrayList<>();
        boolean has_spawners = false;

        for (BlockEntity block_entity : event.chunk().getBlockEntities().values()) {
            if (block_entity instanceof SpawnerBlockEntity) {
                BlockPos pos = block_entity.getBlockPos();
                chunk_spawners.add(pos);

                if (!found_spawner_positions.contains(pos)) {
                    found_spawner_positions.add(pos);
                    new_found_spawners.add(pos);
                }

                has_spawners = true;
                total_spawners_found++;
            }
        }

        if (has_spawners) {
            processed_chunks.add(chunk_pos);
            handle_spawners_found(chunk_pos, chunk_spawners);
        }
    }

    @EventHandler
    private void on_tick(TickEvent.Post event) {
        if (mc.player == null || ++prune_ticks < 60) return;

        prune_ticks = 0;
        prune_distant();
    }

    private void prune_distant() {
        int chunkRadius = mc.options.renderDistance().get() + 4;
        int blockRadius = chunkRadius * 16;
        long blockRadiusSq = (long) blockRadius * blockRadius;

        int playerChunkX = mc.player.chunkPosition().x();
        int playerChunkZ = mc.player.chunkPosition().z();
        double px = mc.player.getX();
        double pz = mc.player.getZ();

        processed_chunks.removeIf(pos -> Math.abs(pos.x() - playerChunkX) > chunkRadius || Math.abs(pos.z() - playerChunkZ) > chunkRadius);

        found_spawner_positions.removeIf(pos -> {
            double dx = pos.getX() + 0.5 - px;
            double dz = pos.getZ() + 0.5 - pz;
            return dx * dx + dz * dz > blockRadiusSq;
        });

        new_found_spawners.retainAll(found_spawner_positions);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || (!show_esp.get() && !show_tracers.get())) return;

        Vec3 playerPos = mc.player.getPosition(event.tickDelta);
        Color sideColor = new Color(esp_color.get());
        Color lineColor = new Color(esp_color.get());
        Color tracerColorValue = new Color(tracer_color.get());

        double maxDistance = render_distance.get();

        for (BlockPos pos : found_spawner_positions) {
            if (only_render_new.get() && !new_found_spawners.contains(pos)) {
                continue;
            }

            if (maxDistance > 0) {
                double dx = pos.getX() + 0.5 - playerPos.x;
                double dy = pos.getY() + 0.5 - playerPos.y;
                double dz = pos.getZ() + 0.5 - playerPos.z;

                if (dx * dx + dy * dy + dz * dz > maxDistance * maxDistance) continue;
            }

            if (show_esp.get()) {
                event.renderer.box(pos, sideColor, lineColor, shape_mode.get(), 0);
            }

            if (show_tracers.get()) {
                Vec3 blockCenter = Vec3.atCenterOf(pos);

                Vec3 startPos;
                if (mc.options.getCameraType().isFirstPerson()) {
                    Vec3 lookDirection = mc.player.getLookAngle();
                    startPos = new Vec3(
                        playerPos.x + lookDirection.x * 0.5,
                        playerPos.y + mc.player.getEyeHeight(mc.player.getPose()) + lookDirection.y * 0.5,
                        playerPos.z + lookDirection.z * 0.5
                    );
                } else {
                    startPos = new Vec3(
                        playerPos.x,
                        playerPos.y + mc.player.getEyeHeight(mc.player.getPose()),
                        playerPos.z
                    );
                }

                event.renderer.line(startPos.x, startPos.y, startPos.z,
                    blockCenter.x, blockCenter.y, blockCenter.z, tracerColorValue);
            }
        }
    }

    private void handle_spawners_found(ChunkPos chunk_pos, List<BlockPos> spawner_positions) {
        String message = build_notification_message(chunk_pos, spawner_positions.size());

        switch (notification_mode.get()) {
            case Chat -> { if (notifications.get()) info(message); }
            case Toast -> show_toast_notification(message);
            case Both -> {
                if (notifications.get()) info(message);
                show_toast_notification(message);
            }
        }

        if (webhook_enabled.get() && !webhook_url.get().trim().isEmpty()) {
            send_webhook_notification(chunk_pos, spawner_positions);
        }

        if (disconnect_on_find.get()) {
            handle_disconnection(chunk_pos, spawner_positions);
        }
    }

    private String build_notification_message(ChunkPos chunk_pos, int spawner_count) {
        StringBuilder msg = new StringBuilder();

        if (spawner_count == 1) {
            msg.append("Spawner found");
        } else {
            msg.append("Spawners found: x").append(spawner_count);
        }

        if (show_coordinates.get()) {
            int center_x = chunk_pos.x() * 16 + 8;
            int center_z = chunk_pos.z() * 16 + 8;
            msg.append(" at chunk ").append(center_x).append(", ").append(center_z);
        }

        if (show_distance.get() && mc.player != null) {
            int center_x = chunk_pos.x() * 16 + 8;
            int center_z = chunk_pos.z() * 16 + 8;
            double distance = mc.player.position().distanceTo(new Vec3(center_x, mc.player.getY(), center_z));
            msg.append(" (").append(String.format("%.1f", distance)).append("m away)");
        }

        return msg.toString();
    }

    private void show_toast_notification(String message) {
        try {
            MeteorToast toast = new MeteorToast.Builder(title).text(message).icon(Items.SPAWNER).build();
            mc.gui.toastManager().addToast(toast);
        } catch (Exception e) {
            if (notifications.get()) info(message);
        }
    }

    private void send_webhook_notification(ChunkPos chunk_pos, List<BlockPos> spawner_positions) {
        if (webhook_url.get().trim().isEmpty()) {
            if (notifications.get()) warning("Webhook URL not configured!");
            return;
        }

        String server_info = mc.getCurrentServer() != null ?
            mc.getCurrentServer().ip : "Unknown Server";

        int center_x = chunk_pos.x() * 16 + 8;
        int center_z = chunk_pos.z() * 16 + 8;

        StringBuilder spawners_breakdown = new StringBuilder();
        for (int i = 0; i < spawner_positions.size(); i++) {
            BlockPos pos = spawner_positions.get(i);
            spawners_breakdown.append(String.format("• Spawner %d: %d, %d, %d\n",
                i + 1, pos.getX(), pos.getY(), pos.getZ()));
        }

        String detection_reason = spawner_positions.size() == 1 ?
            "Single spawner detected" :
            String.format("%d spawners found in same chunk", spawner_positions.size());

        GlazedWebhook.to(webhook_url.get())
            .username("Spawner Notifier")
            .ping(self_ping.get() ? discord_id.get() : null)
            .title("🔥 Spawner Alert")
            .description(String.format("Spawner%s detected in chunk at %d, %d!",
                spawner_positions.size() > 1 ? "s" : "", center_x, center_z))
            .color(15158332)
            .field("Detection Reason", detection_reason, false)
            .field("Total Spawners Found", String.valueOf(spawner_positions.size()), false)
            .field("Spawner Positions", spawners_breakdown.toString(), false)
            .field("Chunk Coordinates", center_x + ", " + center_z, true)
            .field("Server", server_info, true)
            .field("Time", "<t:" + (System.currentTimeMillis() / 1000) + ":R>", true)
            .footer("Spawner Notifier")
            .onError(message -> {
                if (notifications.get()) error("Failed to send webhook: " + message);
            })
            .send();
    }

    private void handle_disconnection(ChunkPos chunk_pos, List<BlockPos> spawner_positions) {
        int center_x = chunk_pos.x() * 16 + 8;
        int center_z = chunk_pos.z() * 16 + 8;

        if (notifications.get()) info("SPAWNER%s FOUND! Disconnecting and disabling module...",
            spawner_positions.size() > 1 ? "S" : "");
        toggle();

        if (mc.player != null) {
            String disconnect_message = spawner_positions.size() == 1 ?
                String.format("SPAWNER FOUND AT %d, %d, %d!",
                    spawner_positions.get(0).getX(), spawner_positions.get(0).getY(), spawner_positions.get(0).getZ()) :
                String.format("SPAWNERS FOUND IN CHUNK %d, %d!", center_x, center_z);

            mc.player.connection.handleDisconnect(
                new ClientboundDisconnectPacket(Component.literal(disconnect_message))
            );
        }
    }

    @Override
    public String getInfoString() {
        return String.valueOf(total_spawners_found);
    }

    public enum Mode {
        Chat,
        Toast,
        Both
    }

    public int get_detected_spawner_count() {
        return total_spawners_found;
    }

    public Set<ChunkPos> get_processed_chunks() {
        return new HashSet<>(processed_chunks);
    }

    public Set<BlockPos> get_found_spawner_positions() {
        return new HashSet<>(found_spawner_positions);
    }

    public Set<BlockPos> get_new_found_spawners() {
        return new HashSet<>(new_found_spawners);
    }
}
