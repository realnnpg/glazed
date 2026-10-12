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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public class BlockNotifier extends Module {
    private final SettingGroup sg_general = settings.getDefaultGroup();
    private final SettingGroup sg_notifications = settings.createGroup("Notifications");
    private final SettingGroup sg_webhook = settings.createGroup("Webhook");
    private final SettingGroup sg_render = settings.createGroup("Render");

    private final Setting<List<Block>> blocks_to_find = sg_general.add(new BlockListSetting.Builder()
        .name("blocks-to-find")
        .description("Blocks to notify when found.")
        .defaultValue(Collections.emptyList())
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
        .description("Disconnect when a block is found.")
        .defaultValue(false)
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
        .description("Ping yourself in the webhook message.")
        .defaultValue(false)
        .visible(() -> webhook_enabled.get())
        .build()
    );

    private final Setting<String> discord_id = sg_webhook.add(new StringSetting.Builder()
        .name("discord-id")
        .description("Your Discord user ID for pinging.")
        .defaultValue("")
        .visible(() -> webhook_enabled.get() && self_ping.get())
        .build()
    );

    private final Setting<Boolean> show_esp = sg_render.add(new BoolSetting.Builder()
        .name("show-esp")
        .description("Highlight found blocks with ESP boxes.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> esp_color = sg_render.add(new ColorSetting.Builder()
        .name("esp-color")
        .description("Color for the ESP boxes")
        .defaultValue(new SettingColor(255, 165, 0, 100))
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
        .description("Draw tracer lines to found blocks")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> tracer_color = sg_render.add(new ColorSetting.Builder()
        .name("tracer-color")
        .description("Color for the tracer lines")
        .defaultValue(new SettingColor(255, 165, 0, 200))
        .visible(() -> show_tracers.get())
        .build()
    );

    private final Setting<Boolean> only_render_new = sg_render.add(new BoolSetting.Builder()
        .name("only-render-new")
        .description("Only render ESP/tracers for newly discovered blocks")
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
    private final Set<BlockPos> found_block_positions = new HashSet<>();
    private final Set<BlockPos> new_found_blocks = new HashSet<>();
    private final Map<BlockPos, Block> block_type_map = new HashMap<>();
    private int prune_ticks = 0;

    private int total_blocks_found = 0;

    public BlockNotifier() {
        super(GlazedAddon.esp, "block-notifier", "Notifies when specific blocks are detected with multiple notification options and visual ESP.");
    }

    @Override
    public void onActivate() {
        processed_chunks.clear();
        found_block_positions.clear();
        new_found_blocks.clear();
        block_type_map.clear();
        total_blocks_found = 0;
        if (notifications.get()) info("BlockNotifier activated!");
    }

    @Override
    public void onDeactivate() {
        if (notifications.get()) info("BlockNotifier deactivated. Found %d blocks total this session.", total_blocks_found);
    }

    @EventHandler
    private void on_prune_tick(TickEvent.Post event) {
        if (mc.player == null || ++prune_ticks < 60) return;

        prune_ticks = 0;

        int chunkRadius = mc.options.renderDistance().get() + 4;
        long blockRadiusSq = (long) (chunkRadius * 16) * (chunkRadius * 16);
        int playerChunkX = mc.player.chunkPosition().x();
        int playerChunkZ = mc.player.chunkPosition().z();
        double px = mc.player.getX();
        double pz = mc.player.getZ();

        processed_chunks.removeIf(pos -> Math.abs(pos.x() - playerChunkX) > chunkRadius || Math.abs(pos.z() - playerChunkZ) > chunkRadius);

        found_block_positions.removeIf(pos -> {
            double dx = pos.getX() + 0.5 - px;
            double dz = pos.getZ() + 0.5 - pz;
            return dx * dx + dz * dz > blockRadiusSq;
        });

        new_found_blocks.retainAll(found_block_positions);
        block_type_map.keySet().retainAll(found_block_positions);
    }

    @EventHandler
    private void on_chunk_data(ChunkDataEvent event) {
        if (mc.player == null || mc.level == null) return;

        ChunkPos chunk_pos = event.chunk().getPos();
        if (processed_chunks.contains(chunk_pos)) return;

        Map<Block, Integer> found_blocks = new HashMap<>();
        Map<Block, List<BlockPos>> block_positions = new HashMap<>();
        boolean has_target_blocks = false;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = mc.level.getMinY(); y < mc.level.getHeight(); y++) {
                    BlockPos pos = new BlockPos(chunk_pos.getMinBlockX() + x, y, chunk_pos.getMinBlockZ() + z);
                    Block block = mc.level.getBlockState(pos).getBlock();

                    if (blocks_to_find.get().contains(block)) {
                        found_blocks.put(block, found_blocks.getOrDefault(block, 0) + 1);
                        block_positions.computeIfAbsent(block, k -> new ArrayList<>()).add(pos);

                        if (!found_block_positions.contains(pos)) {
                            found_block_positions.add(pos);
                            new_found_blocks.add(pos);
                            block_type_map.put(pos, block);
                        }

                        has_target_blocks = true;
                        total_blocks_found++;
                    }
                }
            }
        }

        if (has_target_blocks) {
            processed_chunks.add(chunk_pos);
            handle_blocks_found(chunk_pos, found_blocks);
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || (!show_esp.get() && !show_tracers.get())) return;

        Vec3 playerPos = mc.player.getPosition(event.tickDelta);
        Color sideColor = new Color(esp_color.get());
        Color lineColor = new Color(esp_color.get());
        Color tracerColorValue = new Color(tracer_color.get());

        double maxDistance = render_distance.get();

        for (BlockPos pos : found_block_positions) {
            if (only_render_new.get() && !new_found_blocks.contains(pos)) {
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

    private void handle_blocks_found(ChunkPos chunk_pos, Map<Block, Integer> found_blocks) {
        String message = build_notification_message(chunk_pos, found_blocks);
        String detection_reason = build_detection_reason(found_blocks);

        switch (notification_mode.get()) {
            case Chat -> { if (notifications.get()) info(message); }
            case Toast -> show_toast_notification(message, found_blocks);
            case Both -> {
                if (notifications.get()) info(message);
                show_toast_notification(message, found_blocks);
            }
        }

        if (webhook_enabled.get() && !webhook_url.get().trim().isEmpty()) {
            send_webhook_notification(chunk_pos, found_blocks, detection_reason);
        }

        if (disconnect_on_find.get()) {
            handle_disconnection(chunk_pos, found_blocks);
        }
    }

    private String build_notification_message(ChunkPos chunk_pos, Map<Block, Integer> found_blocks) {
        StringBuilder msg = new StringBuilder();

        if (found_blocks.size() == 1) {
            Map.Entry<Block, Integer> entry = found_blocks.entrySet().iterator().next();
            Block block = entry.getKey();
            int count = entry.getValue();

            if (count == 1) {
                msg.append("Target block found: ").append(block.getName().getString());
            } else {
                msg.append("Target blocks found: ").append(block.getName().getString()).append(" x").append(count);
            }
        } else {
            msg.append("Target blocks found: ");
            List<String> block_list = new ArrayList<>();
            int total = 0;

            for (Map.Entry<Block, Integer> entry : found_blocks.entrySet()) {
                Block block = entry.getKey();
                int count = entry.getValue();
                total += count;

                if (count == 1) {
                    block_list.add(block.getName().getString());
                } else {
                    block_list.add(String.format("%s x%d", block.getName().getString(), count));
                }
            }

            msg.append(String.join(", ", block_list));
            msg.append(String.format(" (%d total)", total));
        }

        int center_x = chunk_pos.x() * 16 + 8;
        int center_z = chunk_pos.z() * 16 + 8;
        msg.append(" at chunk ").append(center_x).append(", ").append(center_z);

        return msg.toString();
    }

    private String build_detection_reason(Map<Block, Integer> found_blocks) {
        if (found_blocks.size() == 1) {
            Map.Entry<Block, Integer> entry = found_blocks.entrySet().iterator().next();
            return String.format("%s detected in chunk", entry.getKey().getName().getString());
        } else {
            return String.format("%d different target block types found", found_blocks.size());
        }
    }

    private void show_toast_notification(String message, Map<Block, Integer> found_blocks) {
        try {
            Block first_block = found_blocks.keySet().iterator().next();
            MeteorToast toast = new MeteorToast.Builder(title).text(message).icon(first_block.asItem()).build();
            mc.gui.toastManager().addToast(toast);
        } catch (Exception e) {
            if (notifications.get()) info(message);
        }
    }

    private void send_webhook_notification(ChunkPos chunk_pos, Map<Block, Integer> found_blocks, String detection_reason) {
        if (webhook_url.get().trim().isEmpty()) {
            if (notifications.get()) warning("Webhook URL not configured!");
            return;
        }

        String server_info = mc.getCurrentServer() != null ?
            mc.getCurrentServer().ip : "Unknown Server";

        int center_x = chunk_pos.x() * 16 + 8;
        int center_z = chunk_pos.z() * 16 + 8;

        StringBuilder items_breakdown = new StringBuilder();
        int total_items = 0;

        for (Map.Entry<Block, Integer> entry : found_blocks.entrySet()) {
            String block_name = entry.getKey().getName().getString();
            int count = entry.getValue();
            items_breakdown.append(String.format("• %s: x%d\n", block_name, count));
            total_items += count;
        }

        GlazedWebhook.to(webhook_url.get())
            .username("Block Notifier")
            .ping(self_ping.get() ? discord_id.get() : null)
            .title("🎯 Block Notifier Alert")
            .description(String.format("Target blocks detected in chunk at %d, %d!", center_x, center_z))
            .color(3447003)
            .field("Detection Reason", detection_reason, false)
            .field("Total Items Found", String.valueOf(total_items), false)
            .field("Items Breakdown", items_breakdown.toString(), false)
            .field("Coordinates", center_x + ", " + center_z, true)
            .field("Server", server_info, true)
            .field("Time", "<t:" + (System.currentTimeMillis() / 1000) + ":R>", true)
            .footer("Block Notifier")
            .onError(message -> {
                if (notifications.get()) error("Failed to send webhook: " + message);
            })
            .send();
    }

    private void handle_disconnection(ChunkPos chunk_pos, Map<Block, Integer> found_blocks) {
        int center_x = chunk_pos.x() * 16 + 8;
        int center_z = chunk_pos.z() * 16 + 8;

        if (notifications.get()) info("TARGET BLOCKS FOUND! Disconnecting...");
        toggle();

        if (mc.player != null) {
            String first_block_name = found_blocks.keySet().iterator().next().getName().getString();
            mc.player.connection.handleDisconnect(
                new ClientboundDisconnectPacket(Component.literal(
                    String.format("TARGET BLOCKS FOUND: %s at %d, %d!",
                        first_block_name, center_x, center_z)))
            );
        }
    }

    @Override
    public String getInfoString() {
        return String.valueOf(total_blocks_found);
    }

    public enum Mode {
        Chat,
        Toast,
        Both
    }

    public int get_detected_block_count() {
        return total_blocks_found;
    }

    public Set<ChunkPos> get_processed_chunks() {
        return new HashSet<>(processed_chunks);
    }

    public Set<BlockPos> get_found_block_positions() {
        return new HashSet<>(found_block_positions);
    }

    public Set<BlockPos> get_new_found_blocks() {
        return new HashSet<>(new_found_blocks);
    }
}
