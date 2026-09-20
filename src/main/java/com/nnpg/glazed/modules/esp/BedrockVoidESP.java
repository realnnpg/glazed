package com.nnpg.glazed.modules.esp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BedrockVoidESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgPerformance = settings.createGroup("Performance");

    private final Setting<Integer> minVoidSize = sgGeneral.add(new IntSetting.Builder()
        .name("min-void-size")
        .description("Minimum number of blocks to consider an area a void.")
        .defaultValue(2)
        .min(1)
        .sliderMax(50)
        .onChanged(this::onSettingChanged)
        .build()
    );

    private final Setting<Boolean> showEsp = sgGeneral.add(new BoolSetting.Builder()
        .name("show-esp")
        .description("Show the void ESP.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showTracers = sgGeneral.add(new BoolSetting.Builder()
        .name("show-tracers")
        .description("Show tracers to the voids.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> chatFeedback = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-feedback")
        .description("Announce voids in chat")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> maxMessagesPerMinute = sgGeneral.add(new IntSetting.Builder()
        .name("max-messages-per-minute")
        .description("Maximum void messages per minute (0 = unlimited)")
        .defaultValue(10)
        .min(0)
        .max(60)
        .sliderRange(0, 60)
        .visible(chatFeedback::get)
        .build()
    );

    private final Setting<SettingColor> espColor = sgRender.add(new ColorSetting.Builder()
        .name("esp-color")
        .description("Color of the void ESP.")
        .defaultValue(new SettingColor(240, 85, 80, 128))
        .visible(showEsp::get)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("Shape mode of the void ESP.")
        .defaultValue(ShapeMode.Both)
        .visible(showEsp::get)
        .build()
    );

    private final Setting<SettingColor> tracerColor = sgRender.add(new ColorSetting.Builder()
        .name("tracer-color")
        .description("Color of the void tracers.")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .visible(showTracers::get)
        .build()
    );

    private final Setting<Boolean> useThreading = sgPerformance.add(new BoolSetting.Builder()
        .name("enable-threading")
        .description("Use multi-threading for chunk scanning (better performance)")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> threadPoolSize = sgPerformance.add(new IntSetting.Builder()
        .name("thread-pool-size")
        .description("Number of threads to use for scanning")
        .defaultValue(4)
        .min(1)
        .max(8)
        .sliderRange(1, 8)
        .visible(useThreading::get)
        .build()
    );

    public BedrockVoidESP() {
        super(GlazedAddon.esp,
            "bedrock-void-esp",
            "Finds voids in bedrock layers. Useful for indicating places" +
                "where spawners may be un-raidable if located there."
        );
    }

    private static final List<Integer> OVERWORLD_Y_LEVELS = List.of(-64, -63, -62, -61, -60);
    private static final List<Integer> NETHER_FLOOR_Y_LEVELS = List.of(0, 1, 2, 3, 4);
    private static final List<Integer> NETHER_ROOF_Y_LEVELS = List.of(123, 124, 125, 126, 127);

    private String currentDimension;
    private final Set<BlockPos> voidBlocks = ConcurrentHashMap.newKeySet();

    private ExecutorService threadPool;

    private long lastMinuteStart = 0;
    private int messagesThisMinute = 0;

    @Override
    public void onActivate() {
        if (mc.level == null) return;
        currentDimension = mc.level.dimension().identifier().toString();

        if (useThreading.get()) {
            threadPool = Executors.newFixedThreadPool(threadPoolSize.get());
        }

        voidBlocks.clear();

        lastMinuteStart = 0;
        messagesThisMinute = 0;

        for (net.minecraft.world.level.chunk.ChunkAccess chunk : Utils.chunks()) {
            if (chunk instanceof LevelChunk worldChunk) {
                if (useThreading.get() && threadPool != null && !threadPool.isShutdown()) {
                    threadPool.submit(() -> scanChunk(worldChunk));
                } else {
                    scanChunk(worldChunk);
                }
            }
        }
    }

    @Override
    public void onDeactivate() {
        if (threadPool != null && !threadPool.isShutdown()) {
            threadPool.shutdownNow();
            threadPool = null;
        }

        voidBlocks.clear();
    }

    @EventHandler
    private void onChunkLoad(ChunkDataEvent event) {
        if (event.chunk() instanceof LevelChunk worldChunk) {
            if (useThreading.get() && threadPool != null && !threadPool.isShutdown()) {
                threadPool.submit(() -> scanChunk(worldChunk));
            } else {
                scanChunk(worldChunk);
            }
        }
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        BlockPos pos = event.pos;
        BlockState state = event.newState;

        List<Integer> yLevels = getYLevelsForDimension();
        if (!yLevels.contains(pos.getY())) return;

        ChunkPos chunkPos = new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4);
        net.minecraft.world.level.chunk.ChunkAccess chunk = mc.level.getChunk(chunkPos.x(), chunkPos.z());
        if (chunk instanceof LevelChunk worldChunk) {
            if (useThreading.get() && threadPool != null && !threadPool.isShutdown()) {
                threadPool.submit(() -> scanChunk(worldChunk));
            } else {
                scanChunk(worldChunk);
            }
        }
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (event.packet instanceof ClientboundForgetLevelChunkPacket packet) {
            ChunkPos chunkPos = packet.pos();

            voidBlocks.removeIf(blockPos -> new ChunkPos(blockPos.getX() >> 4, blockPos.getZ() >> 4).equals(chunkPos));
        }
    }

    private void scanChunk(LevelChunk chunk) {
        if (mc.level == null || chunk == null) return;

        ChunkPos chunkPos = chunk.getPos();

        voidBlocks.removeIf(blockPos -> new ChunkPos(blockPos.getX() >> 4, blockPos.getZ() >> 4).equals(chunkPos));

        List<Integer> yLevels = getYLevelsForDimension();
        if (yLevels.isEmpty()) return;

        findVoidsInChunk(chunk, yLevels);
    }

    private List<Integer> getYLevelsForDimension() {
        return switch (currentDimension) {
            case "minecraft:overworld" -> OVERWORLD_Y_LEVELS;
            case "minecraft:the_nether" -> {
                List<Integer> levels = new ArrayList<>();
                levels.addAll(NETHER_FLOOR_Y_LEVELS);
                levels.addAll(NETHER_ROOF_Y_LEVELS);
                yield levels;
            }
            default -> Collections.emptyList();
        };
    }

    private void findVoidsInChunk(LevelChunk chunk, List<Integer> yLevels) {
        ChunkPos chunkPos = chunk.getPos();
        int startX = chunkPos.getMinBlockX();
        int startZ = chunkPos.getMinBlockZ();

        Set<BlockPos> processed = new HashSet<>();

        for (int y : yLevels) {
            for (int dx = 0; dx < 16; dx++) {
                for (int dz = 0; dz < 16; dz++) {
                    BlockPos pos = new BlockPos(startX + dx, y, startZ + dz);

                    if (processed.contains(pos)) continue;
                    if (isBedrock(getBlockState(pos))) continue;

                    List<BlockPos> group = floodFillVoid(pos, yLevels, processed);

                    if (group.size() >= minVoidSize.get() && isVoidEnclosed(group)) {
                        voidBlocks.addAll(group);
                        if (!group.isEmpty()) {
                            BlockPos firstBlock = group.get(0);
                            sendVoidMessage("§5[§dBedrockVoidESP§5] §bVoid found§5: §b" + group.size() + " blocks at " + firstBlock.toShortString());
                        }
                    }
                }
            }
        }
    }

    private List<BlockPos> floodFillVoid(BlockPos start, List<Integer> yLevels, Set<BlockPos> processed) {
        List<BlockPos> group = new ArrayList<>();
        Queue<BlockPos> queue = new LinkedList<>();
        queue.offer(start);

        while (!queue.isEmpty() && group.size() < 200) {
            BlockPos current = queue.poll();

            if (processed.contains(current)) continue;
            if (isBedrock(getBlockState(current))) continue;
            if (!yLevels.contains(current.getY())) continue;

            processed.add(current);
            group.add(current);

            for (Direction dir : Direction.values()) {
                BlockPos neighbor = current.relative(dir);
                if (!processed.contains(neighbor)) {
                    queue.offer(neighbor);
                }
            }
        }

        return group;
    }

    private boolean isVoidEnclosed(List<BlockPos> group) {
        for (BlockPos pos : group) {
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = pos.relative(dir);

                if (group.contains(neighbor)) continue;

                if (!isBedrock(getBlockState(neighbor))) {
                    return false;
                }
            }
        }
        return true;
    }

    private BlockState getBlockState(BlockPos pos) {
        if (mc.level == null) return Blocks.BEDROCK.defaultBlockState();
        return mc.level.getBlockState(pos);
    }

    private void onSettingChanged(Integer value) {
        if (isActive() && mc.level != null) {
            voidBlocks.clear();

            for (net.minecraft.world.level.chunk.ChunkAccess chunk : Utils.chunks()) {
                if (chunk instanceof LevelChunk worldChunk) {
                    if (useThreading.get() && threadPool != null && !threadPool.isShutdown()) {
                        threadPool.submit(() -> scanChunk(worldChunk));
                    } else {
                        scanChunk(worldChunk);
                    }
                }
            }
        }
    }

    private static boolean isBedrock(BlockState state) {
        return state.getBlock() == Blocks.BEDROCK;
    }

    private void sendVoidMessage(String message) {
        if (!chatFeedback.get()) return;

        long currentTime = System.currentTimeMillis();
        long currentMinute = currentTime / 60000;

        if (currentMinute != lastMinuteStart) {
            lastMinuteStart = currentMinute;
            messagesThisMinute = 0;
        }

        int maxMessages = maxMessagesPerMinute.get();

        if (maxMessages == 0 || messagesThisMinute < maxMessages) {
            info(message);
            messagesThisMinute++;
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (showEsp.get()) {
            Color color = espColor.get();
            for (BlockPos pos : voidBlocks) {
                event.renderer.box(pos, color, color, shapeMode.get(), 0);
            }
        }

        if (showTracers.get()) {
            Color color = tracerColor.get();
            Vec3 camera = mc.gameRenderer.mainCamera().position();

            for (BlockPos pos : voidBlocks) {
                Vec3 blockCenter = Vec3.atCenterOf(pos);
                event.renderer.line(camera.x, camera.y, camera.z, blockCenter.x, blockCenter.y, blockCenter.z, color);
            }
        }
    }
}
