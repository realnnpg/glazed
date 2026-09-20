package com.nnpg.glazed.modules.esp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.MeteorToast;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.polarbear.PolarBear;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WeatheringCopper.WeatherState;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class ChunkFinder extends Module {
    public enum Mode {
        Chat,
        Toast,
        Both
    }

    private enum BlockKind {
        DEEPSLATE,
        COBBLED_DEEPSLATE,
        ROTATED_DEEPSLATE,
        END_STONE
    }

    private static final int MAGIC_NEIGHBOUR_LIGHT = 4;
    private static final int GEODE_MIN_Y = -64;
    private static final int GEODE_MAX_Y = 48;
    private static final int GEODE_HEIGHT = GEODE_MAX_Y - GEODE_MIN_Y + 1;

    private static final byte CELL_CRYSTAL = 1;
    private static final byte CELL_CALCITE = 2;
    private static final byte CELL_BASALT = 3;
    private static final byte CELL_AIR = 4;
    private static final byte CELL_SOLID = 5;

    private static final int[] NEIGHBOUR_X = {1, -1, 0, 0, 0, 0};
    private static final int[] NEIGHBOUR_Y = {0, 0, 1, -1, 0, 0};
    private static final int[] NEIGHBOUR_Z = {0, 0, 0, 0, 1, -1};

    private final SettingGroup sgDetection = settings.createGroup("Detection");
    private final SettingGroup sgAmethyst = settings.createGroup("Amethyst");
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgBlockHighlight = settings.createGroup("Block Highlighting");
    private final SettingGroup sgPerformance = settings.createGroup("Performance");
    private final SettingGroup sgNotifications = settings.createGroup("Notifications");

    private final Setting<Boolean> detectDeepslate = sgDetection.add(new BoolSetting.Builder()
        .name("detect-deepslate")
        .description("Find deepslate blocks")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> detectCobbledDeepslate = sgDetection.add(new BoolSetting.Builder()
        .name("detect-cobbled-deepslate")
        .description("Find cobbled deepslate blocks")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> detectRotatedDeepslate = sgDetection.add(new BoolSetting.Builder()
        .name("detect-rotated-deepslate")
        .description("Find rotated deepslate blocks")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> detectEndStone = sgDetection.add(new BoolSetting.Builder()
        .name("detect-end-stone")
        .description("Find end stone blocks (disabled in The End dimension)")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> ignoreExposed = sgDetection.add(new BoolSetting.Builder()
        .name("ignore-exposed")
        .description("Ignore suspicious blocks that are exposed to air or fluid (treats water/lava like air)")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> ignoreTrialChambers = sgDetection.add(new BoolSetting.Builder()
        .name("ignore-trial-chambers")
        .description("Ignore chunks containing trial chambers (based on waxed copper blocks and tuff bricks)")
        .defaultValue(true)
        .build());

    private final Setting<Integer> trialChamberThreshold = sgDetection.add(new IntSetting.Builder()
        .name("trial-chamber-threshold")
        .description("Minimum waxed copper or tuff brick blocks to identify a trial chamber")
        .defaultValue(50)
        .range(1, 50)
        .sliderRange(1, 50)
        .visible(ignoreTrialChambers::get)
        .build());

    private final Setting<Integer> deepslateThreshold = sgDetection.add(new IntSetting.Builder()
        .name("deepslate-threshold")
        .description("Min deepslate to flag chunk")
        .defaultValue(1)
        .range(1, 15)
        .sliderRange(1, 15)
        .visible(detectDeepslate::get)
        .build());

    private final Setting<Integer> cobbledDeepslateThreshold = sgDetection.add(new IntSetting.Builder()
        .name("cobbled-deepslate-threshold")
        .description("Min cobbled deepslate to flag chunk")
        .defaultValue(4)
        .range(1, 15)
        .sliderRange(1, 15)
        .visible(detectCobbledDeepslate::get)
        .build());

    private final Setting<Integer> rotatedDeepslateThreshold = sgDetection.add(new IntSetting.Builder()
        .name("rotated-threshold")
        .description("Min rotated deepslate to flag chunk")
        .defaultValue(3)
        .range(1, 20)
        .sliderRange(1, 20)
        .visible(detectRotatedDeepslate::get)
        .build());

    private final Setting<Integer> endStoneThreshold = sgDetection.add(new IntSetting.Builder()
        .name("end-stone-threshold")
        .description("Min end stone count to flag chunk")
        .defaultValue(2)
        .range(1, 15)
        .sliderRange(1, 15)
        .visible(detectEndStone::get)
        .build());

    private final Setting<Boolean> baseDetectors = sgDetection.add(new BoolSetting.Builder()
        .name("base-detectors")
        .description("Score chunks against the player-activity detectors below.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> minScore = sgDetection.add(new IntSetting.Builder()
        .name("min-score")
        .description("Combined detector score needed to flag a chunk.")
        .defaultValue(5)
        .min(1)
        .sliderRange(1, 30)
        .visible(baseDetectors::get)
        .build());

    private final Setting<SettingColor> baseChunkColor = sgDetection.add(new ColorSetting.Builder()
        .name("base-chunk-color")
        .description("Colour for chunks flagged by the detectors.")
        .defaultValue(new SettingColor(0, 255, 80, 60))
        .visible(baseDetectors::get)
        .build());

    private final Setting<Boolean> detCobble = sgDetection.add(new BoolSetting.Builder()
        .name("cobble-lines").description("Runs of cobblestone below y=0.").defaultValue(true).visible(baseDetectors::get).build());
    private final Setting<Boolean> detDeepslateBand = sgDetection.add(new BoolSetting.Builder()
        .name("deepslate-band").description("Polished/brick/tile/chiselled deepslate below y=0.").defaultValue(true).visible(baseDetectors::get).build());
    private final Setting<Boolean> detObsidian = sgDetection.add(new BoolSetting.Builder()
        .name("obsidian-backbone").description("Obsidian below y=20.").defaultValue(true).visible(baseDetectors::get).build());
    private final Setting<Boolean> detVeinInt = sgDetection.add(new BoolSetting.Builder()
        .name("vein-integrity").description("Valuable ore sitting next to storage.").defaultValue(true).visible(baseDetectors::get).build());
    private final Setting<Boolean> detVertVein = sgDetection.add(new BoolSetting.Builder()
        .name("vertical-vein").description("Ore stacked several deep in one column.").defaultValue(true).visible(baseDetectors::get).build());
    private final Setting<Boolean> detNeedle = sgDetection.add(new BoolSetting.Builder()
        .name("needle-hole").description("1x1 shafts mined straight down.").defaultValue(true).visible(baseDetectors::get).build());
    private final Setting<Boolean> detHiddenOre = sgDetection.add(new BoolSetting.Builder()
        .name("hidden-ore").description("Ore walled in on five of six sides.").defaultValue(true).visible(baseDetectors::get).build());
    private final Setting<Boolean> detPiston = sgDetection.add(new BoolSetting.Builder()
        .name("piston-array").description("Pistons below y=20.").defaultValue(true).visible(baseDetectors::get).build());
    private final Setting<Boolean> detMobCluster = sgDetection.add(new BoolSetting.Builder()
        .name("mob-cluster").description("Unusually dense mob counts.").defaultValue(true).visible(baseDetectors::get).build());
    private final Setting<Boolean> detDropped = sgDetection.add(new BoolSetting.Builder()
        .name("dropped-items").description("Item entities below y=0.").defaultValue(true).visible(baseDetectors::get).build());
    private final Setting<Boolean> detAirPocket = sgDetection.add(new BoolSetting.Builder()
        .name("air-pocket").description("Large hollowed-out cavities.").defaultValue(true).visible(baseDetectors::get).build());
    private final Setting<Boolean> detStair = sgDetection.add(new BoolSetting.Builder()
        .name("synthetic-stair").description("Crafted stair blocks below y=0.").defaultValue(true).visible(baseDetectors::get).build());
    private final Setting<Boolean> detPolarBear = sgDetection.add(new BoolSetting.Builder()
        .name("polar-bear").description("Polar bears, which do not spawn underground naturally.").defaultValue(true).visible(baseDetectors::get).build());
    private final Setting<Boolean> detChestCluster = sgDetection.add(new BoolSetting.Builder()
        .name("chest-cluster").description("Ten or more chests stacked below y=0.").defaultValue(true).visible(baseDetectors::get).build());

    private final Setting<Boolean> detectAmethyst = sgAmethyst.add(new BoolSetting.Builder()
        .name("detect-amethyst")
        .description("Flag geodes that have already been stripped of their budding amethyst.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> amethystSmartCheck = sgAmethyst.add(new BoolSetting.Builder()
        .name("smart-check")
        .description("Also require geode shape: dense, tightly clustered, wrapped in calcite and not open to air.")
        .defaultValue(true)
        .visible(detectAmethyst::get)
        .build());

    private final Setting<Integer> amethystSensitivity = sgAmethyst.add(new IntSetting.Builder()
        .name("sensitivity")
        .description("Higher looses every amethyst threshold at once. 1 is strict, 10 catches almost anything.")
        .defaultValue(5)
        .range(1, 10)
        .sliderRange(1, 10)
        .visible(detectAmethyst::get)
        .build());

    private final Setting<SettingColor> amethystChunkColor = sgAmethyst.add(new ColorSetting.Builder()
        .name("amethyst-chunk-color")
        .description("Colour for chunks holding a stripped geode.")
        .defaultValue(new SettingColor(180, 100, 255, 60))
        .visible(detectAmethyst::get)
        .build());

    private final Setting<Boolean> magicAmethyst = sgAmethyst.add(new BoolSetting.Builder()
        .name("magic-amthyst-detector-ig")
        .description("Flag chunks where a geode has unlit spawnable space around it.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> magicThreshold = sgAmethyst.add(new IntSetting.Builder()
        .name("magic-threshold")
        .description("Unlit spawnable spots a geode needs before the chunk is flagged.")
        .defaultValue(12)
        .min(1)
        .max(100)
        .sliderRange(1, 100)
        .visible(magicAmethyst::get)
        .build()
    );

    private final Setting<Integer> magicScanRadius = sgAmethyst.add(new IntSetting.Builder()
        .name("magic-scan-radius")
        .description("How far around the middle of a geode to look.")
        .defaultValue(8)
        .min(1)
        .max(24)
        .sliderRange(1, 24)
        .visible(magicAmethyst::get)
        .build()
    );

    private final Setting<Boolean> detectStructure = sgDetection.add(new BoolSetting.Builder()
        .name("detect-structure")
        .description("Score chunks on redstone, farms and storage, and report a base chance.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> baseChanceThreshold = sgDetection.add(new IntSetting.Builder()
        .name("min-base-chance")
        .description("Base chance percent needed before a chunk is reported.")
        .defaultValue(45)
        .range(20, 99)
        .sliderRange(20, 99)
        .visible(detectStructure::get)
        .build());

    private final Setting<Boolean> structRepeater = sgDetection.add(new BoolSetting.Builder()
        .name("powered-repeaters").description("Powered repeaters, the strongest single signal.").defaultValue(true).visible(detectStructure::get).build());
    private final Setting<Boolean> structBeehive = sgDetection.add(new BoolSetting.Builder()
        .name("occupied-beehives").description("Beehives and bee nests that still hold bees.").defaultValue(true).visible(detectStructure::get).build());
    private final Setting<Boolean> structDeepslateFloor = sgDetection.add(new BoolSetting.Builder()
        .name("cobbled-deepslate-floor").description("Large cobbled deepslate spreads between y=0 and y=20.").defaultValue(true).visible(detectStructure::get).build());
    private final Setting<Boolean> structVines = sgDetection.add(new BoolSetting.Builder()
        .name("vine-farm").description("Dense vine growth.").defaultValue(true).visible(detectStructure::get).build());
    private final Setting<Boolean> structSeagrass = sgDetection.add(new BoolSetting.Builder()
        .name("seagrass-farm").description("Dense seagrass at or below y=70.").defaultValue(true).visible(detectStructure::get).build());
    private final Setting<Boolean> structFlowers = sgDetection.add(new BoolSetting.Builder()
        .name("flower-farm").description("Planted flowers at or below y=70.").defaultValue(true).visible(detectStructure::get).build());
    private final Setting<Boolean> structStorage = sgDetection.add(new BoolSetting.Builder()
        .name("crafting-storage").description("Chests, barrels, furnaces, anvils and enchanting tables.").defaultValue(true).visible(detectStructure::get).build());

    private final Setting<SettingColor> structureChunkColor = sgDetection.add(new ColorSetting.Builder()
        .name("structure-chunk-color")
        .description("Colour for chunks flagged by the structure score.")
        .defaultValue(new SettingColor(255, 120, 0, 60))
        .visible(detectStructure::get)
        .build());

    private final Setting<Double> renderY = sgRender.add(new DoubleSetting.Builder()
        .name("render-height")
        .description("Height to render chunk highlights")
        .defaultValue(64.0)
        .range(-64.0, 320.0)
        .sliderRange(-64.0, 320.0)
        .build());

    private final Setting<ShapeMode> renderMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("render-mode")
        .description("How to render highlighted chunks")
        .defaultValue(ShapeMode.Both)
        .build());

    private final Setting<SettingColor> chunkColor = sgRender.add(new ColorSetting.Builder()
        .name("chunk-color")
        .description("Color for suspicious chunks")
        .defaultValue(new SettingColor(255, 215, 0, 120))
        .build());

    private final Setting<Double> thickness = sgRender.add(new DoubleSetting.Builder()
        .name("thickness")
        .description("Thickness of highlight box")
        .defaultValue(0.3)
        .range(0.1, 2.0)
        .sliderRange(0.1, 2.0)
        .build());

    private final Setting<Integer> maxChunksToRender = sgRender.add(new IntSetting.Builder()
        .name("max-chunks-render")
        .description("Maximum highlighted chunks drawn per category")
        .defaultValue(50)
        .range(10, 400)
        .sliderRange(10, 400)
        .build());

    private final Setting<Boolean> highlightBlocks = sgBlockHighlight.add(new BoolSetting.Builder()
        .name("highlight-blocks")
        .description("Highlight individual suspicious blocks")
        .defaultValue(true)
        .build());

    private final Setting<Integer> maxBlocksToRender = sgBlockHighlight.add(new IntSetting.Builder()
        .name("max-blocks-render")
        .description("Maximum number of blocks to highlight (performance)")
        .defaultValue(200)
        .range(50, 1000)
        .sliderRange(50, 1000)
        .visible(highlightBlocks::get)
        .build());

    private final Setting<ShapeMode> blockRenderMode = sgBlockHighlight.add(new EnumSetting.Builder<ShapeMode>()
        .name("block-render-mode")
        .description("How to render individual blocks")
        .defaultValue(ShapeMode.Lines)
        .visible(highlightBlocks::get)
        .build());

    private final Setting<SettingColor> deepslateBlockColor = sgBlockHighlight.add(new ColorSetting.Builder()
        .name("deepslate-color")
        .description("Color for deepslate blocks")
        .defaultValue(new SettingColor(100, 100, 100, 200))
        .visible(highlightBlocks::get)
        .build());

    private final Setting<SettingColor> cobbledDeepslateBlockColor = sgBlockHighlight.add(new ColorSetting.Builder()
        .name("cobbled-deepslate-color")
        .description("Color for cobbled deepslate blocks")
        .defaultValue(new SettingColor(80, 80, 80, 200))
        .visible(highlightBlocks::get)
        .build());

    private final Setting<SettingColor> rotatedDeepslateBlockColor = sgBlockHighlight.add(new ColorSetting.Builder()
        .name("rotated-deepslate-color")
        .description("Color for rotated deepslate blocks")
        .defaultValue(new SettingColor(120, 0, 120, 200))
        .visible(highlightBlocks::get)
        .build());

    private final Setting<SettingColor> endStoneBlockColor = sgBlockHighlight.add(new ColorSetting.Builder()
        .name("end-stone-color")
        .description("Color for end stone blocks")
        .defaultValue(new SettingColor(255, 255, 200, 200))
        .visible(highlightBlocks::get)
        .build());

    private final Setting<Boolean> useMultiThreading = sgPerformance.add(new BoolSetting.Builder()
        .name("threading")
        .description("Use background threads for scanning")
        .defaultValue(true)
        .build());

    private final Setting<Integer> threadCount = sgPerformance.add(new IntSetting.Builder()
        .name("thread-count")
        .description("Number of worker threads")
        .defaultValue(Math.max(1, Runtime.getRuntime().availableProcessors() / 2))
        .range(1, 4)
        .sliderRange(1, 4)
        .visible(useMultiThreading::get)
        .build());

    private final Setting<Integer> scanInterval = sgPerformance.add(new IntSetting.Builder()
        .name("scan-delay")
        .description("Milliseconds between scans")
        .defaultValue(100)
        .range(50, 2000)
        .sliderRange(50, 2000)
        .build());

    private final Setting<Integer> maxConcurrentScans = sgPerformance.add(new IntSetting.Builder()
        .name("max-concurrent-scans")
        .description("Max chunks scanned simultaneously")
        .defaultValue(3)
        .range(1, 8)
        .sliderRange(1, 8)
        .build());

    private final Setting<Integer> entityScanInterval = sgPerformance.add(new IntSetting.Builder()
        .name("entity-scan-ticks")
        .description("Ticks between entity sweeps for the mob, item and polar bear detectors")
        .defaultValue(20)
        .range(5, 200)
        .sliderRange(5, 200)
        .visible(baseDetectors::get)
        .build());

    private final Setting<Integer> cleanupInterval = sgPerformance.add(new IntSetting.Builder()
        .name("cleanup-interval")
        .description("Seconds between distant chunk cleanup")
        .defaultValue(30)
        .range(15, 300)
        .sliderRange(15, 300)
        .build());

    private final Setting<Mode> notificationMode = sgNotifications.add(new EnumSetting.Builder<Mode>()
        .name("notification-mode")
        .description("How to notify when suspicious chunks are detected")
        .defaultValue(Mode.Both)
        .build());

    private final Setting<Boolean> playSound = sgNotifications.add(new BoolSetting.Builder()
        .name("sound-alerts")
        .description("Play sound when suspicious chunks or blocks are found")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> chatAlerts = sgNotifications.add(new BoolSetting.Builder()
        .name("chat-alerts")
        .description("Send chat notifications for suspicious chunks or blocks")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> trialChamberAlerts = sgNotifications.add(new BoolSetting.Builder()
        .name("trial-chamber-alerts")
        .description("Send chat notifications for trial chambers")
        .defaultValue(true)
        .build());

    private final Setting<Integer> maxAlerts = sgNotifications.add(new IntSetting.Builder()
        .name("max-alerts")
        .description("Max alerts per minute")
        .defaultValue(5)
        .range(1, 20)
        .sliderRange(1, 20)
        .build());

    private final Set<ChunkPos> flaggedChunks = ConcurrentHashMap.newKeySet();
    private final Set<ChunkPos> baseFlaggedChunks = ConcurrentHashMap.newKeySet();
    private final Set<ChunkPos> amethystChunks = ConcurrentHashMap.newKeySet();
    private final Set<ChunkPos> structureChunks = ConcurrentHashMap.newKeySet();
    private final Set<ChunkPos> scannedChunks = ConcurrentHashMap.newKeySet();
    private final Set<ChunkPos> pendingChunks = ConcurrentHashMap.newKeySet();
    private final Map<ChunkPos, ChunkAnalysis> chunkData = new ConcurrentHashMap<>();
    private final Map<ChunkPos, EntitySignals> entitySignals = new ConcurrentHashMap<>();
    private final Map<ChunkPos, Long> notificationTimes = new ConcurrentHashMap<>();
    private final Map<BlockPos, BlockKind> suspiciousBlocks = new ConcurrentHashMap<>();
    private final Queue<Long> recentAlerts = new ConcurrentLinkedQueue<>();
    private final AtomicLong activeScanCount = new AtomicLong(0);

    private ExecutorService scannerPool;
    private volatile boolean shouldScan = false;
    private long lastCleanup = 0;
    private int entityTicks = 0;

    public ChunkFinder() {
        super(GlazedAddon.esp, "chunk-finder", "ChunkFinderV5");
    }

    @Override
    public void onActivate() {
        if (mc.level == null) return;

        clearAll();
        shouldScan = true;
        lastCleanup = System.currentTimeMillis();

        if (useMultiThreading.get()) {
            scannerPool = Executors.newFixedThreadPool(threadCount.get(), r -> {
                Thread t = new Thread(r, "ChunkFinder-Worker");
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY - 1);
                return t;
            });
        }

        startInitialScan();
    }

    @Override
    public void onDeactivate() {
        shouldScan = false;

        if (scannerPool != null) {
            scannerPool.shutdownNow();
            scannerPool = null;
        }

        clearAll();
    }

    private void clearAll() {
        flaggedChunks.clear();
        baseFlaggedChunks.clear();
        amethystChunks.clear();
        structureChunks.clear();
        scannedChunks.clear();
        pendingChunks.clear();
        chunkData.clear();
        entitySignals.clear();
        notificationTimes.clear();
        suspiciousBlocks.clear();
        recentAlerts.clear();
        activeScanCount.set(0);
        entityTicks = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.level == null || mc.player == null) return;

        long now = System.currentTimeMillis();

        while (!recentAlerts.isEmpty() && now - recentAlerts.peek() > 60000) {
            recentAlerts.poll();
        }

        if (baseDetectors.get() && ++entityTicks >= entityScanInterval.get()) {
            entityTicks = 0;
            refreshEntitySignals();
        }

        drainPending();

        if (now - lastCleanup > cleanupInterval.get() * 1000L) {
            performCleanup();
            lastCleanup = now;
        }
    }

    private void refreshEntitySignals() {
        Map<ChunkPos, EntitySignals> tally = new HashMap<>();

        for (Entity entity : mc.level.entitiesForRendering()) {
            ChunkPos pos = new ChunkPos(entity.blockPosition().getX() >> 4, entity.blockPosition().getZ() >> 4);
            EntitySignals signals = tally.computeIfAbsent(pos, key -> new EntitySignals());

            if (entity instanceof ItemEntity && entity.getY() < 0.0) signals.droppedItems++;
            if (entity instanceof PolarBear) signals.polarBears += 3;
            if (!(entity instanceof Player)) signals.mobs++;
        }

        entitySignals.keySet().retainAll(tally.keySet());
        entitySignals.putAll(tally);
    }

    private void drainPending() {
        if (pendingChunks.isEmpty() || mc.level == null) return;

        for (ChunkPos pos : List.copyOf(pendingChunks)) {
            if (activeScanCount.get() >= maxConcurrentScans.get()) return;

            pendingChunks.remove(pos);
            ChunkAccess chunk = mc.level.getChunkSource().getChunk(pos.x(), pos.z(), false);
            if (chunk instanceof LevelChunk levelChunk) submit(levelChunk, false);
        }
    }

    @EventHandler
    private void onChunkLoad(ChunkDataEvent event) {
        if (!shouldScan) return;

        ChunkPos pos = event.chunk().getPos();
        if (scannedChunks.contains(pos)) return;

        if (event.chunk() instanceof LevelChunk chunk) submit(chunk, true);
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (!shouldScan || mc.level == null) return;

        BlockPos blockPos = event.pos;
        if (blockPos.getY() < GEODE_MIN_Y || blockPos.getY() > 128) return;

        BlockState newState = event.newState;
        if (!isRelevantBlock(newState) && !newState.isAir()) return;

        ChunkPos chunkPos = new ChunkPos(blockPos.getX() >> 4, blockPos.getZ() >> 4);
        scannedChunks.remove(chunkPos);
        pendingChunks.add(chunkPos);
    }

    private boolean isRelevantBlock(BlockState state) {
        Block block = state.getBlock();

        return block == Blocks.DEEPSLATE
            || block == Blocks.COBBLED_DEEPSLATE
            || block == Blocks.POLISHED_DEEPSLATE
            || block == Blocks.DEEPSLATE_BRICKS
            || block == Blocks.DEEPSLATE_TILES
            || block == Blocks.CHISELED_DEEPSLATE
            || block == Blocks.END_STONE
            || block == Blocks.COPPER_BLOCK.waxed().pick(WeatherState.UNAFFECTED)
            || block == Blocks.COPPER_BLOCK.waxed().pick(WeatherState.WEATHERED)
            || block == Blocks.TUFF_BRICKS
            || block == Blocks.REPEATER
            || block == Blocks.AMETHYST_BLOCK
            || block == Blocks.BUDDING_AMETHYST;
    }

    private void startInitialScan() {
        Runnable task = () -> {
            try {
                for (ChunkAccess chunk : Utils.chunks()) {
                    if (!shouldScan) break;
                    if (!(chunk instanceof LevelChunk levelChunk)) continue;

                    submit(levelChunk, true);
                    Thread.sleep(scanInterval.get());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        if (useMultiThreading.get() && scannerPool != null) scannerPool.submit(task);
        else new Thread(task, "ChunkFinder-Initial").start();
    }

    private void submit(LevelChunk chunk, boolean queueIfBusy) {
        if (!shouldScan) return;

        if (activeScanCount.get() >= maxConcurrentScans.get()) {
            if (queueIfBusy) pendingChunks.add(chunk.getPos());
            return;
        }

        Runnable task = () -> analyzeChunk(chunk);

        if (useMultiThreading.get() && scannerPool != null) scannerPool.submit(task);
        else {
            Thread worker = new Thread(task, "ChunkFinder-Scan");
            worker.setDaemon(true);
            worker.start();
        }
    }

    private void analyzeChunk(LevelChunk chunk) {
        if (!shouldScan || chunk == null) return;

        ChunkPos pos = chunk.getPos();
        if (!scannedChunks.add(pos)) return;

        activeScanCount.incrementAndGet();

        try {
            ChunkAnalysis analysis = new ChunkAnalysis();

            scanBlockSignals(chunk, analysis);
            if (baseDetectors.get()) scanBaseSignals(chunk, analysis);
            if (detectAmethyst.get()) analysis.geode = scanGeode(chunk);
            if (magicAmethyst.get()) analysis.magicSpots = scanMagicAmethyst(chunk);
            if (detectStructure.get()) scanStructure(chunk, analysis);

            chunkData.put(pos, analysis);
            evaluate(pos, analysis);
        } finally {
            activeScanCount.decrementAndGet();
        }
    }

    private void scanBlockSignals(LevelChunk chunk, ChunkAnalysis analysis) {
        LevelChunkSection[] sections = chunk.getSections();
        int originX = chunk.getPos().getMinBlockX();
        int originZ = chunk.getPos().getMinBlockZ();
        int bottom = chunk.getMinY();
        boolean inEnd = mc.level != null && mc.level.dimension() == Level.END;

        for (int index = 0; index < sections.length; index++) {
            if (!shouldScan) return;

            LevelChunkSection section = sections[index];
            if (section == null || section.hasOnlyAir()) continue;

            int sectionBottom = bottom + index * 16;
            if (sectionBottom > 128 || sectionBottom + 15 < 0) continue;

            for (int y = 0; y < 16; y++) {
                int worldY = sectionBottom + y;
                if (worldY < 0 || worldY > 128) continue;

                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (state.isAir()) continue;

                        classifyBlock(new BlockPos(originX + x, worldY, originZ + z), state, analysis, inEnd);
                    }
                }
            }
        }
    }

    private void classifyBlock(BlockPos blockPos, BlockState state, ChunkAnalysis analysis, boolean inEnd) {
        if (ignoreTrialChambers.get() && isTrialChamberBlock(state)) analysis.trialChamber++;

        boolean exposed = ignoreExposed.get() && isExposed(blockPos);
        if (exposed) return;

        BlockKind kind = null;

        if (detectDeepslate.get() && isPlainDeepslate(state) && !isInLongDeepslateRun(blockPos, blockPos.getY())) {
            analysis.deepslate++;
            kind = BlockKind.DEEPSLATE;
        }

        if (detectRotatedDeepslate.get() && isRotatedDeepslate(state)) {
            analysis.rotatedDeepslate++;
            kind = BlockKind.ROTATED_DEEPSLATE;
        }

        if (detectCobbledDeepslate.get() && state.is(Blocks.COBBLED_DEEPSLATE)) {
            analysis.cobbledDeepslate++;
            kind = BlockKind.COBBLED_DEEPSLATE;
        }

        if (detectEndStone.get() && state.is(Blocks.END_STONE) && !inEnd) {
            analysis.endStone++;
            kind = BlockKind.END_STONE;
        }

        if (kind != null && highlightBlocks.get()) suspiciousBlocks.put(blockPos, kind);
    }

    private boolean isExposed(BlockPos pos) {
        if (mc.level == null) return false;

        for (Direction dir : Direction.values()) {
            BlockPos offset = pos.relative(dir);
            if (offset.getY() < mc.level.getMinY() || offset.getY() >= mc.level.getHeight()) continue;

            BlockState neighbour = mc.level.getBlockState(offset);
            if (neighbour.isAir()) return true;

            FluidState fluid = neighbour.getFluidState();
            if (fluid != null && !fluid.isEmpty()) return true;
        }

        return false;
    }

    private boolean isInLongDeepslateRun(BlockPos pos, int worldY) {
        if (mc.level == null) return false;

        int limit = worldY > -8 ? 50 : 20;

        if (runLength(pos, Direction.EAST, Direction.WEST, limit) >= limit) return true;
        if (runLength(pos, Direction.SOUTH, Direction.NORTH, limit) >= limit) return true;

        return worldY > 0 && runLength(pos, Direction.UP, Direction.DOWN, limit) >= limit;
    }

    private int runLength(BlockPos pos, Direction forward, Direction back, int limit) {
        int total = 1;

        for (Direction dir : new Direction[]{forward, back}) {
            for (int i = 1; i < limit; i++) {
                BlockPos next = pos.relative(dir, i);
                if (next.getY() < mc.level.getMinY() || next.getY() >= mc.level.getHeight()) break;
                if (!isPlainDeepslate(mc.level.getBlockState(next))) break;
                total++;
            }
        }

        return total;
    }

    private void scanBaseSignals(LevelChunk chunk, ChunkAnalysis analysis) {
        ChunkPos pos = chunk.getPos();
        int originX = pos.getMinBlockX();
        int originZ = pos.getMinBlockZ();
        int bottom = chunk.getMinY();
        int top = Math.min(chunk.getMinY() + chunk.getHeight() - 1, 60);

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                if (!shouldScan) return;

                int worldX = originX + x;
                int worldZ = originZ + z;

                int airRun = 0;
                int deepAirRun = 0;
                int oreColumn = 0;

                for (int y = bottom; y <= top; y++) {
                    cursor.set(worldX, y, worldZ);
                    BlockState state = chunk.getBlockState(cursor);
                    Block block = state.getBlock();

                    if (block == Blocks.AIR || block == Blocks.CAVE_AIR) {
                        airRun++;
                        if (y < 0) deepAirRun++;
                    } else {
                        if (airRun >= 12 && y < 20) analysis.airPocket += airRun / 4;
                        airRun = 0;
                        if (y < 0) deepAirRun = 0;
                    }

                    if (y < 0) {
                        if (block == Blocks.COBBLESTONE || block == Blocks.COBBLED_DEEPSLATE || block == Blocks.MOSSY_COBBLESTONE) {
                            cursor.set(worldX + 1, y, worldZ);
                            if (worldX + 1 < originX + 16 && chunk.getBlockState(cursor).getBlock() == block) analysis.cobbleLines++;
                            cursor.set(worldX, y, worldZ);
                        }

                        if (block == Blocks.POLISHED_DEEPSLATE || block == Blocks.DEEPSLATE_BRICKS
                            || block == Blocks.DEEPSLATE_TILES || block == Blocks.CHISELED_DEEPSLATE) {
                            analysis.deepslateBand++;
                        }

                        if (isStair(block)) analysis.stairs++;
                    }

                    if (y < 20) {
                        if (block == Blocks.OBSIDIAN) analysis.obsidian++;
                        if (isPiston(block)) analysis.pistons++;
                    }

                    if (y < 0 && (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST)) analysis.deepChests++;

                    if (isOre(block)) {
                        oreColumn++;

                        if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE || block == Blocks.ANCIENT_DEBRIS) {
                            analysis.valuableOre++;
                        }

                        if (isHiddenOre(chunk, worldX, y, worldZ)) analysis.hiddenOre += 2;
                    } else {
                        oreColumn = 0;
                    }

                    if (oreColumn == 4) analysis.vertVeins++;
                }

                if (deepAirRun >= 10) analysis.needles++;
            }
        }

        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            if (blockEntity instanceof ChestBlockEntity || blockEntity instanceof HopperBlockEntity) analysis.storage++;
        }

        EntitySignals signals = entitySignals.get(pos);
        if (signals == null) return;

        analysis.droppedItems = signals.droppedItems;
        analysis.polarBears = signals.polarBears;
        if (signals.mobs > 20) analysis.mobCluster = signals.mobs / 5;
    }

    private int scanMagicAmethyst(LevelChunk chunk) {
        Level level = mc.level;
        if (level == null) return 0;

        ChunkPos pos = chunk.getPos();
        int originX = pos.getMinBlockX();
        int originZ = pos.getMinBlockZ();
        int bottom = Math.max(GEODE_MIN_Y, chunk.getMinY());
        int top = Math.min(GEODE_MAX_Y, chunk.getMinY() + chunk.getHeight() - 1);

        long sumX = 0;
        long sumY = 0;
        long sumZ = 0;
        int count = 0;

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int y = bottom; y <= top; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    cursor.set(originX + x, y, originZ + z);
                    BlockState state = chunk.getBlockState(cursor);

                    if (!state.is(Blocks.AMETHYST_BLOCK) && !state.is(Blocks.BUDDING_AMETHYST)) continue;

                    sumX += cursor.getX();
                    sumY += cursor.getY();
                    sumZ += cursor.getZ();
                    count++;
                }
            }
        }

        if (count == 0) return 0;

        int centreX = (int) (sumX / count);
        int centreY = (int) (sumY / count);
        int centreZ = (int) (sumZ / count);
        int radius = magicScanRadius.get();
        int spots = 0;

        BlockPos.MutableBlockPos neighbour = new BlockPos.MutableBlockPos();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (!shouldScan) return spots;

                    cursor.set(centreX + dx, centreY + dy, centreZ + dz);

                    BlockState state = level.getBlockState(cursor);
                    if (!state.is(Blocks.AIR) && !state.is(Blocks.AMETHYST_CLUSTER)) continue;
                    if (level.getBrightness(LightLayer.BLOCK, cursor) != 0) continue;

                    BlockPos darkAir = null;
                    boolean lit = false;

                    for (Direction direction : Direction.values()) {
                        neighbour.set(cursor.getX() + direction.getStepX(), cursor.getY() + direction.getStepY(), cursor.getZ() + direction.getStepZ());
                        int light = level.getBrightness(LightLayer.BLOCK, neighbour);

                        if (light > MAGIC_NEIGHBOUR_LIGHT) {
                            lit = true;
                            break;
                        }

                        if (darkAir == null && light == 0 && level.getBlockState(neighbour).is(Blocks.AIR)) {
                            darkAir = neighbour.immutable();
                        }
                    }

                    if (lit || darkAir == null) continue;

                    for (Direction direction : Direction.values()) {
                        neighbour.set(darkAir.getX() + direction.getStepX(), darkAir.getY() + direction.getStepY(), darkAir.getZ() + direction.getStepZ());
                        if (level.getBrightness(LightLayer.BLOCK, neighbour) > MAGIC_NEIGHBOUR_LIGHT) {
                            lit = true;
                            break;
                        }
                    }

                    if (lit) continue;

                    spots++;
                }
            }
        }

        return spots;
    }

    private GeodeResult scanGeode(LevelChunk chunk) {
        LevelChunkSection[] sections = chunk.getSections();
        int bottom = chunk.getMinY();

        byte[] cells = new byte[16 * GEODE_HEIGHT * 16];
        List<int[]> crystals = new ArrayList<>();
        int matrix = 0;
        long sumX = 0;
        long sumY = 0;
        long sumZ = 0;

        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            if (section == null || section.hasOnlyAir()) continue;

            int sectionBottom = bottom + index * 16;
            if (sectionBottom > GEODE_MAX_Y || sectionBottom + 15 < GEODE_MIN_Y) continue;

            for (int y = 0; y < 16; y++) {
                int worldY = sectionBottom + y;
                if (worldY < GEODE_MIN_Y || worldY > GEODE_MAX_Y) continue;

                int cellY = worldY - GEODE_MIN_Y;

                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        BlockState state = section.getBlockState(x, y, z);

                        if (state.is(Blocks.BUDDING_AMETHYST)) return GeodeResult.intact();

                        byte cell;

                        if (isCrystal(state)) {
                            cell = CELL_CRYSTAL;
                            crystals.add(new int[]{x, worldY, z});
                            sumX += x;
                            sumY += worldY;
                            sumZ += z;
                        } else if (state.is(Blocks.CALCITE)) {
                            cell = CELL_CALCITE;
                            matrix++;
                        } else if (state.is(Blocks.SMOOTH_BASALT)) {
                            cell = CELL_BASALT;
                            matrix++;
                        } else if (state.isAir()) {
                            cell = CELL_AIR;
                        } else {
                            cell = CELL_SOLID;
                        }

                        cells[cellIndex(x, cellY, z)] = cell;
                    }
                }
            }
        }

        int count = crystals.size();
        if (count == 0) return GeodeResult.none();
        if (!amethystSmartCheck.get()) return new GeodeResult(count >= minCrystals(), count);

        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;

        for (int[] crystal : crystals) {
            lowest = Math.min(lowest, crystal[1]);
            highest = Math.max(highest, crystal[1]);
        }

        double density = count / (256.0 * Math.max(1, highest - lowest + 1));
        double centreX = (double) sumX / count;
        double centreY = (double) sumY / count;
        double centreZ = (double) sumZ / count;
        double spread = 0.0;

        for (int[] crystal : crystals) {
            double dx = crystal[0] - centreX;
            double dy = crystal[1] - centreY;
            double dz = crystal[2] - centreZ;
            spread += Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        spread /= count;

        int openFaces = 0;
        int buriedFaces = 0;

        for (int[] crystal : crystals) {
            int cx = crystal[0];
            int cy = crystal[1] - GEODE_MIN_Y;
            int cz = crystal[2];

            for (int face = 0; face < 6; face++) {
                int nx = cx + NEIGHBOUR_X[face];
                int ny = cy + NEIGHBOUR_Y[face];
                int nz = cz + NEIGHBOUR_Z[face];

                if (nx < 0 || nx > 15 || nz < 0 || nz > 15 || ny < 0 || ny >= GEODE_HEIGHT) continue;

                byte cell = cells[cellIndex(nx, ny, nz)];
                if (cell == CELL_AIR) openFaces++;
                else if (cell >= CELL_CALCITE) buriedFaces++;
            }
        }

        int faces = openFaces + buriedFaces;
        double exposure = faces > 0 ? (double) openFaces / faces : 0.0;

        boolean hit = count >= minCrystals()
            && density >= minDensity()
            && matrix >= minMatrix()
            && spread <= maxSpread()
            && exposure <= maxExposure();

        return new GeodeResult(hit, count);
    }

    private static int cellIndex(int x, int y, int z) {
        return (y * 16 + x) * 16 + z;
    }

    private int minCrystals() {
        return (int) (40.0 - (amethystSensitivity.get() - 1) * 3.5555555555555554);
    }

    private double minDensity() {
        return 0.015 - (amethystSensitivity.get() - 1) * 0.0013333333;
    }

    private int minMatrix() {
        return (int) (15.0 - (amethystSensitivity.get() - 1) * 1.3333333333333333);
    }

    private double maxExposure() {
        return 0.08 + (amethystSensitivity.get() - 1) * 0.027;
    }

    private double maxSpread() {
        return 6.0 + (amethystSensitivity.get() - 1) * 0.44444445;
    }

    private void scanStructure(LevelChunk chunk, ChunkAnalysis analysis) {
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            BlockState state = chunk.getBlockState(blockEntity.getBlockPos());

            if (state.is(Blocks.BEEHIVE) || state.is(Blocks.BEE_NEST)) {
                analysis.hives++;
                if (blockEntity instanceof BeehiveBlockEntity hive) analysis.bees += Math.max(0, hive.getOccupantCount());
            } else if (isWorkstation(state.getBlock())) {
                analysis.workstations++;
            }
        }

        LevelChunkSection[] sections = chunk.getSections();
        int bottom = chunk.getMinY();

        for (int index = 0; index < sections.length; index++) {
            if (!shouldScan) return;

            LevelChunkSection section = sections[index];
            if (section == null || section.hasOnlyAir()) continue;
            if (!section.maybeHas(ChunkFinder::isStructureBlock)) continue;

            int sectionBottom = bottom + index * 16;

            for (int y = 0; y < 16; y++) {
                int worldY = sectionBottom + y;

                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        BlockState state = section.getBlockState(x, y, z);

                        if (state.is(Blocks.REPEATER)) {
                            analysis.repeaters++;
                            if (state.hasProperty(BlockStateProperties.POWERED) && state.getValue(BlockStateProperties.POWERED)) {
                                analysis.poweredRepeaters++;
                            }
                        } else if (state.is(Blocks.VINE)) {
                            analysis.vines++;
                        } else if (state.is(Blocks.COBBLED_DEEPSLATE)) {
                            if (worldY >= 0 && worldY <= 20) analysis.deepslateFloor++;
                        } else if (state.is(Blocks.SEAGRASS) || state.is(Blocks.TALL_SEAGRASS)) {
                            if (worldY <= 70) analysis.seagrass++;
                        } else if (state.is(BlockTags.FLOWERS)) {
                            if (worldY <= 70) analysis.flowers++;
                        } else if (isWorkstation(state.getBlock())) {
                            analysis.workstations++;
                        }
                    }
                }
            }
        }
    }

    private static boolean isStructureBlock(BlockState state) {
        return state.is(Blocks.REPEATER)
            || state.is(Blocks.VINE)
            || state.is(Blocks.COBBLED_DEEPSLATE)
            || state.is(Blocks.SEAGRASS)
            || state.is(Blocks.TALL_SEAGRASS)
            || state.is(BlockTags.FLOWERS)
            || isWorkstation(state.getBlock());
    }

    private int structureScore(ChunkAnalysis a) {
        int score = 0;

        if (structBeehive.get()) score += Math.min(90, a.hives * 22 + a.bees * 10);
        if (structDeepslateFloor.get()) score += Math.min(65, a.deepslateFloor / 2);
        if (structVines.get()) score += Math.min(55, a.vines / 4);
        if (structSeagrass.get()) score += Math.min(70, a.seagrass);
        if (structFlowers.get()) score += Math.min(75, a.flowers * 12);
        if (structRepeater.get()) score += Math.min(90, a.repeaters * 16 + a.poweredRepeaters * 28);
        if (structStorage.get()) score += Math.min(100, a.workstations * 18);

        return score;
    }

    private static int baseChance(int score) {
        return Math.max(20, Math.min(99, 25 + score / 2));
    }

    private boolean isHiddenOre(LevelChunk chunk, int x, int y, int z) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        ChunkPos pos = chunk.getPos();
        int enclosed = 0;

        for (Direction direction : Direction.values()) {
            cursor.set(x + direction.getStepX(), y + direction.getStepY(), z + direction.getStepZ());

            if (cursor.getX() < pos.getMinBlockX() || cursor.getX() > pos.getMaxBlockX()) continue;
            if (cursor.getZ() < pos.getMinBlockZ() || cursor.getZ() > pos.getMaxBlockZ()) continue;

            Block neighbour = chunk.getBlockState(cursor).getBlock();

            if (neighbour == Blocks.COBBLESTONE || neighbour == Blocks.COBBLED_DEEPSLATE || neighbour == Blocks.STONE
                || neighbour == Blocks.DEEPSLATE || neighbour == Blocks.OBSIDIAN || neighbour == Blocks.NETHERRACK) {
                enclosed++;
            }
        }

        return enclosed >= 5;
    }

    private static boolean isCrystal(BlockState state) {
        return state.is(Blocks.AMETHYST_CLUSTER)
            || state.is(Blocks.LARGE_AMETHYST_BUD)
            || state.is(Blocks.MEDIUM_AMETHYST_BUD)
            || state.is(Blocks.SMALL_AMETHYST_BUD)
            || state.is(Blocks.AMETHYST_BLOCK);
    }

    private static boolean isWorkstation(Block block) {
        return block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || block == Blocks.BARREL
            || block == Blocks.ENDER_CHEST || block == Blocks.FURNACE || block == Blocks.BLAST_FURNACE
            || block == Blocks.SMOKER || block == Blocks.CRAFTING_TABLE || block == Blocks.ENCHANTING_TABLE
            || block == Blocks.ANVIL || block == Blocks.CHIPPED_ANVIL || block == Blocks.DAMAGED_ANVIL;
    }

    private static boolean isPiston(Block block) {
        return block == Blocks.PISTON || block == Blocks.STICKY_PISTON
            || block == Blocks.MOVING_PISTON || block == Blocks.PISTON_HEAD;
    }

    private static boolean isOre(Block block) {
        return block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE
            || block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE
            || block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE
            || block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE
            || block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE
            || block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE
            || block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE
            || block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE
            || block == Blocks.ANCIENT_DEBRIS;
    }

    private static boolean isStair(Block block) {
        return block == Blocks.STONE_STAIRS || block == Blocks.COBBLESTONE_STAIRS
            || block == Blocks.DEEPSLATE_BRICK_STAIRS || block == Blocks.POLISHED_DEEPSLATE_STAIRS
            || block == Blocks.OAK_STAIRS || block == Blocks.SPRUCE_STAIRS
            || block == Blocks.DARK_OAK_STAIRS || block == Blocks.COBBLED_DEEPSLATE_STAIRS;
    }

    private static boolean isPlainDeepslate(BlockState state) {
        if (!state.is(Blocks.DEEPSLATE) || !state.hasProperty(BlockStateProperties.AXIS)) return false;
        return state.getValue(BlockStateProperties.AXIS) == Direction.Axis.Y;
    }

    private static boolean isRotatedDeepslate(BlockState state) {
        if (!state.is(Blocks.DEEPSLATE) || !state.hasProperty(BlockStateProperties.AXIS)) return false;
        return state.getValue(BlockStateProperties.AXIS) != Direction.Axis.Y;
    }

    private static boolean isTrialChamberBlock(BlockState state) {
        return state.is(Blocks.COPPER_BLOCK.waxed().pick(WeatherState.UNAFFECTED)) || state.is(Blocks.COPPER_BLOCK.waxed().pick(WeatherState.OXIDIZED)) || state.is(Blocks.TUFF_BRICKS);
    }

    private int scoreBaseSignals(ChunkAnalysis a, List<String> tags) {
        int score = 0;

        if (detCobble.get() && a.cobbleLines >= 3) {
            score += Math.min(a.cobbleLines, 6);
            tags.add("CobbleLine(" + a.cobbleLines + ")");
        }

        if (detDeepslateBand.get() && a.deepslateBand >= 4) {
            score += Math.min(a.deepslateBand / 2, 5);
            tags.add("Deepslate(" + a.deepslateBand + ")");
        }

        if (detObsidian.get() && a.obsidian >= 3) {
            score += Math.min(a.obsidian, 6);
            tags.add("Obsidian(" + a.obsidian + ")");
        }

        if (detVeinInt.get() && a.valuableOre >= 1 && a.storage >= 1) {
            score += 4;
            tags.add("VeinInt");
        }

        if (detVertVein.get() && a.vertVeins >= 2) {
            score += a.vertVeins;
            tags.add("VertVein(" + a.vertVeins + ")");
        }

        if (detNeedle.get() && a.needles >= 2) {
            score += a.needles * 2;
            tags.add("Needle(" + a.needles + ")");
        }

        if (detHiddenOre.get() && a.hiddenOre >= 2) {
            score += a.hiddenOre;
            tags.add("HiddenOre(" + a.hiddenOre + ")");
        }

        if (detPiston.get() && a.pistons >= 3) {
            score += Math.min(a.pistons, 8);
            tags.add("PistonArray(" + a.pistons + ")");
        }

        if (detMobCluster.get() && a.mobCluster >= 2) {
            score += a.mobCluster;
            tags.add("MobCluster(" + a.mobCluster * 5 + ")");
        }

        if (detDropped.get() && a.droppedItems >= 2) {
            score += a.droppedItems * 2;
            tags.add("Dropped(" + a.droppedItems + ")");
        }

        if (detAirPocket.get() && a.airPocket >= 5) {
            score += a.airPocket / 3;
            tags.add("AirPocket(" + a.airPocket + ")");
        }

        if (detStair.get() && a.stairs >= 5) {
            score += a.stairs / 3;
            tags.add("Stairs(" + a.stairs + ")");
        }

        if (detPolarBear.get() && a.polarBears > 0) {
            score += a.polarBears;
            tags.add("PolarBear");
        }

        if (detChestCluster.get() && a.deepChests >= 10) {
            score += Math.min(a.deepChests, 12);
            tags.add("ChestCluster(" + a.deepChests + ")");
        }

        if (a.storage >= 3) {
            score += a.storage;
            tags.add("Storage(" + a.storage + ")");
        }

        return score;
    }

    private void evaluate(ChunkPos pos, ChunkAnalysis analysis) {
        if (ignoreTrialChambers.get() && analysis.trialChamber >= trialChamberThreshold.get()) {
            if (trialChamberAlerts.get()) {
                notify(pos, String.format("Trial chamber - Copper/Tuff blocks: %d", analysis.trialChamber), true);
            }

            forget(pos);
            return;
        }

        StringBuilder reasons = new StringBuilder();

        if (detectDeepslate.get() && analysis.deepslate >= deepslateThreshold.get()) {
            reasons.append("Deepslate[").append(analysis.deepslate).append("] ");
        }

        if (detectCobbledDeepslate.get() && analysis.cobbledDeepslate >= cobbledDeepslateThreshold.get()) {
            reasons.append("CobbledDeepslate[").append(analysis.cobbledDeepslate).append("] ");
        }

        if (detectRotatedDeepslate.get() && analysis.rotatedDeepslate >= rotatedDeepslateThreshold.get()) {
            reasons.append("RotatedDeepslate[").append(analysis.rotatedDeepslate).append("] ");
        }

        if (detectEndStone.get() && analysis.endStone >= endStoneThreshold.get()) {
            reasons.append("EndStone[").append(analysis.endStone).append("] ");
        }

        if (!reasons.isEmpty()) {
            if (flaggedChunks.add(pos)) notify(pos, reasons.toString().trim(), false);
        } else {
            flaggedChunks.remove(pos);
            notificationTimes.remove(pos);
        }

        if (baseDetectors.get()) {
            List<String> tags = new ArrayList<>();
            int score = scoreBaseSignals(analysis, tags);

            if (score >= minScore.get()) {
                if (baseFlaggedChunks.add(pos)) notify(pos, "BOII we MAYY Be RICHHH S:" + score + " [" + String.join(", ", tags) + "]", false);
            } else {
                baseFlaggedChunks.remove(pos);
            }
        } else {
            baseFlaggedChunks.remove(pos);
        }

        boolean strippedGeode = detectAmethyst.get() && analysis.geode.hit();
        boolean magicGeode = magicAmethyst.get() && analysis.magicSpots >= magicThreshold.get();

        if (strippedGeode || magicGeode) {
            if (amethystChunks.add(pos)) {
                String tag = strippedGeode ? "StrippedGeode[" + analysis.geode.crystals() + "]" : "";
                if (magicGeode) tag = (tag.isEmpty() ? "" : tag + " ") + "MagicAmethyst[" + analysis.magicSpots + "]";
                notify(pos, tag, false);
            }
        } else {
            amethystChunks.remove(pos);
        }

        if (detectStructure.get()) {
            int score = structureScore(analysis);
            int chance = baseChance(score);

            if (chance >= baseChanceThreshold.get() && score > 0) {
                if (structureChunks.add(pos)) {
                    String tag = analysis.poweredRepeaters >= 3 ? "Repeater chunk" : "Base chance " + chance + "%";
                    notify(pos, tag + " (score " + score + ")", false);
                }
            } else {
                structureChunks.remove(pos);
            }
        } else {
            structureChunks.remove(pos);
        }
    }

    private void forget(ChunkPos pos) {
        flaggedChunks.remove(pos);
        baseFlaggedChunks.remove(pos);
        amethystChunks.remove(pos);
        structureChunks.remove(pos);
        notificationTimes.remove(pos);
    }

    private void notify(ChunkPos pos, String details, boolean trialChamber) {
        long now = System.currentTimeMillis();

        if (recentAlerts.size() >= maxAlerts.get()) return;

        Long last = notificationTimes.get(pos);
        if (!trialChamber && last != null && now - last < 45000) return;

        String message = String.format("ChunkFinder [%d, %d] - %s", pos.x(), pos.z(), details);
        boolean allowChat = trialChamber ? trialChamberAlerts.get() : chatAlerts.get();

        mc.execute(() -> {
            Mode mode = notificationMode.get();

            if ((mode == Mode.Chat || mode == Mode.Both) && allowChat && mc.player != null) {
                mc.player.sendSystemMessage(Component.literal(message));
            }

            if (mode == Mode.Toast || mode == Mode.Both) {
                mc.gui.toastManager().addToast(new MeteorToast.Builder("ChunkFinder").text(message).icon(Items.CHEST).build());
            }

            if (playSound.get()) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.5f));
            }

            recentAlerts.offer(now);
            notificationTimes.put(pos, now);
        });
    }

    private void performCleanup() {
        if (mc.player == null) return;

        int viewDist = mc.options.renderDistance().get();
        ChunkPos centre = mc.player.chunkPosition();

        flaggedChunks.removeIf(pos -> beyond(pos, centre, viewDist + 5));
        baseFlaggedChunks.removeIf(pos -> beyond(pos, centre, viewDist + 5));
        amethystChunks.removeIf(pos -> beyond(pos, centre, viewDist + 5));
        structureChunks.removeIf(pos -> beyond(pos, centre, viewDist + 5));
        pendingChunks.removeIf(pos -> beyond(pos, centre, viewDist + 5));
        scannedChunks.removeIf(pos -> beyond(pos, centre, viewDist + 3));
        chunkData.keySet().removeIf(pos -> beyond(pos, centre, viewDist + 5));
        entitySignals.keySet().removeIf(pos -> beyond(pos, centre, viewDist + 5));
        notificationTimes.keySet().removeIf(pos -> beyond(pos, centre, viewDist + 5));

        suspiciousBlocks.keySet().removeIf(pos -> mc.player.position().distanceTo(Vec3.atCenterOf(pos)) > viewDist * 16 + 80);
    }

    private static boolean beyond(ChunkPos pos, ChunkPos centre, int radius) {
        return Math.abs(pos.x() - centre.x()) > radius || Math.abs(pos.z() - centre.z()) > radius;
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null) return;

        drawChunks(event, flaggedChunks, new Color(chunkColor.get()));
        drawChunks(event, baseFlaggedChunks, new Color(baseChunkColor.get()));
        drawChunks(event, amethystChunks, new Color(amethystChunkColor.get()));
        drawChunks(event, structureChunks, new Color(structureChunkColor.get()));

        if (highlightBlocks.get()) drawBlocks(event);
    }

    private void drawChunks(Render3DEvent event, Set<ChunkPos> chunks, Color color) {
        if (chunks.isEmpty()) return;

        int limit = maxChunksToRender.get();
        int drawn = 0;
        double y = renderY.get();
        double h = thickness.get();

        for (ChunkPos pos : chunks) {
            if (drawn++ >= limit) break;

            AABB box = new AABB(pos.getMinBlockX(), y, pos.getMinBlockZ(), pos.getMaxBlockX() + 1, y + h, pos.getMaxBlockZ() + 1);
            event.renderer.box(box, color, color, renderMode.get(), 0);
        }
    }

    private void drawBlocks(Render3DEvent event) {
        int limit = maxBlocksToRender.get();
        int drawn = 0;
        double maxDistance = mc.options.renderDistance().get() * 16.0;

        for (Map.Entry<BlockPos, BlockKind> entry : suspiciousBlocks.entrySet()) {
            if (drawn >= limit) break;

            BlockPos pos = entry.getKey();
            if (mc.player.position().distanceTo(Vec3.atCenterOf(pos)) > maxDistance) continue;

            Color color = colorFor(entry.getValue());
            event.renderer.box(new AABB(pos), color, color, blockRenderMode.get(), 0);
            drawn++;
        }
    }

    private Color colorFor(BlockKind kind) {
        return switch (kind) {
            case DEEPSLATE -> new Color(deepslateBlockColor.get());
            case COBBLED_DEEPSLATE -> new Color(cobbledDeepslateBlockColor.get());
            case ROTATED_DEEPSLATE -> new Color(rotatedDeepslateBlockColor.get());
            case END_STONE -> new Color(endStoneBlockColor.get());
        };
    }

    @Override
    public String getInfoString() {
        int chunks = flaggedChunks.size() + baseFlaggedChunks.size() + amethystChunks.size() + structureChunks.size();
        if (highlightBlocks.get()) return String.format("C:%d B:%d", chunks, suspiciousBlocks.size());
        return String.valueOf(chunks);
    }

    private record GeodeResult(boolean hit, int crystals) {
        static GeodeResult none() {
            return new GeodeResult(false, 0);
        }

        static GeodeResult intact() {
            return new GeodeResult(false, 0);
        }
    }

    private static final class EntitySignals {
        int droppedItems;
        int polarBears;
        int mobs;
    }

    private static final class ChunkAnalysis {
        int deepslate;
        int cobbledDeepslate;
        int rotatedDeepslate;
        int endStone;
        int trialChamber;

        int cobbleLines;
        int deepslateBand;
        int obsidian;
        int pistons;
        int stairs;
        int airPocket;
        int needles;
        int hiddenOre;
        int vertVeins;
        int valuableOre;
        int storage;
        int mobCluster;
        int droppedItems;
        int polarBears;
        int deepChests;

        int hives;
        int bees;
        int deepslateFloor;
        int vines;
        int seagrass;
        int flowers;
        int repeaters;
        int poweredRepeaters;
        int workstations;

        GeodeResult geode = GeodeResult.none();
        int magicSpots;
    }
}
