package com.nnpg.glazed.modules.esp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DebrisLeakESP extends Module {

    public enum Narrow {
        Off,
        Enclosed
    }

    public enum TracerTarget {
        Box,
        Spot
    }

    public enum Sort {
        Distance,
        Value
    }

    private record Area(double x1, double y1, double z1, double x2, double y2, double z2, boolean exact, long section) {
        double cx() { return (x1 + x2) / 2.0; }
        double cy() { return (y1 + y2) / 2.0; }
        double cz() { return (z1 + z2) / 2.0; }
    }

    private final Map<Long, int[]> frozenSpots = new HashMap<>();
    private final Map<Long, Area> spotBySection = new HashMap<>();

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgCrystal = settings.createGroup("Crystal");
    private final SettingGroup sgReveal = settings.createGroup("Reveal");
    private final SettingGroup sgLog = settings.createGroup("Log");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> chunkRange = sgGeneral.add(new IntSetting.Builder()
        .name("chunk-range")
        .description("Chunk radius around you to check.")
        .defaultValue(8)
        .min(1)
        .max(16)
        .sliderRange(1, 16)
        .build()
    );

    private final Setting<Integer> refresh = sgGeneral.add(new IntSetting.Builder()
        .name("refresh")
        .description("Ticks between rescans.")
        .defaultValue(10)
        .min(1)
        .max(100)
        .sliderRange(1, 40)
        .build()
    );

    private final Setting<Boolean> showExact = sgGeneral.add(new BoolSetting.Builder()
        .name("show-exact")
        .description("Draw ancient debris the server actually sent you, block for block.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showHidden = sgGeneral.add(new BoolSetting.Builder()
        .name("show-hidden")
        .description("Draw the area of sections whose palette still lists ancient debris while every block of it was replaced.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Narrow> narrow = sgGeneral.add(new EnumSetting.Builder<Narrow>()
        .name("narrow")
        .description("Enclosed shrinks a hidden area to the netherrack that anti-xray was allowed to obfuscate: fully walled in, no transparent neighbour. Off draws the whole 16x16x16 section.")
        .defaultValue(Narrow.Enclosed)
        .visible(showHidden::get)
        .build()
    );

    private final Setting<Boolean> showCandidates = sgGeneral.add(new BoolSetting.Builder()
        .name("show-candidates")
        .description("Also draw every individual cell inside a narrowed area.")
        .defaultValue(false)
        .visible(() -> showHidden.get() && narrow.get() == Narrow.Enclosed)
        .build()
    );

    private final Setting<Sort> sort = sgGeneral.add(new EnumSetting.Builder<Sort>()
        .name("sort")
        .description("Value ranks boxes by how likely they are to actually hold debris: low sections score far above high ones, and tight boxes above loose ones. Distance is plain nearest-first.")
        .defaultValue(Sort.Value)
        .build()
    );

    private final Setting<Integer> maxResults = sgGeneral.add(new IntSetting.Builder()
        .name("max-results")
        .description("Keep only this many results, nearest first.")
        .defaultValue(64)
        .min(1)
        .max(512)
        .sliderRange(1, 256)
        .build()
    );

    private final Setting<Boolean> obsidianSpot = sgCrystal.add(new BoolSetting.Builder()
        .name("obsidian-spot")
        .description("Mark where to put an obsidian block in each orange box so one end crystal craters as much of it as possible. The crystal sits on top and the obsidian eats the downward rays, so the blast only goes up: the marker lands low in the box on purpose. Ancient debris survives it, so what stands afterwards is the debris.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> blastRadius = sgCrystal.add(new DoubleSetting.Builder()
        .name("blast-radius")
        .description("How far a crystal actually clears netherrack. 5 is a good starting guess, tune it once you have watched one go off.")
        .defaultValue(5.0)
        .min(1.0)
        .max(10.0)
        .sliderRange(2.0, 8.0)
        .onChanged(v -> frozenSpots.clear())
        .visible(obsidianSpot::get)
        .build()
    );

    private final Setting<Integer> spotLimit = sgCrystal.add(new IntSetting.Builder()
        .name("spot-limit")
        .description("Only work out spots for this many of the top boxes. Boxes past this get no marker, and with tracer-target on Spot they get no tracer either.")
        .defaultValue(16)
        .min(1)
        .max(64)
        .sliderRange(1, 32)
        .visible(obsidianSpot::get)
        .build()
    );

    private final Setting<Boolean> showCrystal = sgCrystal.add(new BoolSetting.Builder()
        .name("show-crystal")
        .description("Also outline the block above the obsidian, where the crystal goes. Clear it before placing.")
        .defaultValue(true)
        .visible(obsidianSpot::get)
        .build()
    );

    private final Setting<SettingColor> spotColor = sgCrystal.add(new ColorSetting.Builder()
        .name("spot-color")
        .description("Colour of the obsidian marker.")
        .defaultValue(new SettingColor(0, 225, 255))
        .visible(obsidianSpot::get)
        .build()
    );

    private final Setting<Boolean> revealLattice = sgReveal.add(new BoolSetting.Builder()
        .name("reveal-lattice")
        .description("Draw the sparse grid of blocks to break so anti-xray reveals the whole box. Every break un-hides the real blocks around it, so a few dozen breaks expose what would otherwise take thousands.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> revealRadius = sgReveal.add(new IntSetting.Builder()
        .name("reveal-radius")
        .description("How far the server un-hides blocks around a block update. Paper defaults to 2. Break one block in obfuscated rock, see how far the truth spreads, and set this to that.")
        .defaultValue(2)
        .min(1)
        .max(6)
        .sliderRange(1, 4)
        .visible(revealLattice::get)
        .build()
    );

    private final Setting<Integer> latticeLimit = sgReveal.add(new IntSetting.Builder()
        .name("lattice-limit")
        .description("Only draw lattices for this many of the top boxes.")
        .defaultValue(2)
        .min(1)
        .max(16)
        .sliderRange(1, 8)
        .visible(revealLattice::get)
        .build()
    );

    private final Setting<SettingColor> latticeColor = sgReveal.add(new ColorSetting.Builder()
        .name("lattice-color")
        .description("Colour of the blocks to break.")
        .defaultValue(new SettingColor(60, 255, 90))
        .visible(revealLattice::get)
        .build()
    );

    private final Setting<Boolean> log = sgLog.add(new BoolSetting.Builder()
        .name("log")
        .description("Append every section observation to glazed/debris_sections.csv and every real debris block to glazed/debris_blocks.csv, live as you fly.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> logClean = sgLog.add(new BoolSetting.Builder()
        .name("log-clean")
        .description("Also record sections whose palette proves there is no ancient debris left. This is the half that tells a mined-out vein from an untouched one, so leave it on.")
        .defaultValue(true)
        .visible(log::get)
        .build()
    );

    private final Setting<Integer> minY = sgRender.add(new IntSetting.Builder()
        .name("min-y")
        .description("Vertical band to draw. Ancient debris generates from 8 to 119, and the bulk of it sits below 24.")
        .defaultValue(8)
        .min(0)
        .max(127)
        .sliderRange(0, 127)
        .build()
    );

    private final Setting<Integer> maxY = sgRender.add(new IntSetting.Builder()
        .name("max-y")
        .description("Upper edge of the band.")
        .defaultValue(119)
        .min(0)
        .max(127)
        .sliderRange(0, 127)
        .build()
    );

    private final Setting<Integer> maxDistance = sgRender.add(new IntSetting.Builder()
        .name("max-distance")
        .description("Stop drawing past this many blocks.")
        .defaultValue(160)
        .min(16)
        .max(512)
        .sliderRange(16, 512)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the boxes are drawn.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> exactColor = sgRender.add(new ColorSetting.Builder()
        .name("exact-color")
        .description("Colour for debris the server actually showed you.")
        .defaultValue(new SettingColor(209, 27, 245))
        .build()
    );

    private final Setting<SettingColor> hiddenColor = sgRender.add(new ColorSetting.Builder()
        .name("hidden-color")
        .description("Colour for areas that only the palette gave away.")
        .defaultValue(new SettingColor(255, 145, 0))
        .build()
    );

    private final Setting<Integer> sideAlpha = sgRender.add(new IntSetting.Builder()
        .name("side-alpha")
        .description("Fill opacity of the boxes.")
        .defaultValue(40)
        .min(0)
        .max(255)
        .sliderRange(0, 255)
        .build()
    );

    private final Setting<Boolean> tracers = sgRender.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Draw a line to each area.")
        .defaultValue(true)
        .build()
    );

    private final Setting<TracerTarget> tracerTarget = sgRender.add(new EnumSetting.Builder<TracerTarget>()
        .name("tracer-target")
        .description("Spot aims the line at the cyan obsidian marker instead of the middle of the box, so you can fly straight at where you actually need to place. Boxes with no marker still trace to the box.")
        .defaultValue(TracerTarget.Box)
        .visible(tracers::get)
        .build()
    );

    private final Setting<Boolean> nearestOnly = sgRender.add(new BoolSetting.Builder()
        .name("nearest-only")
        .description("Trace only the closest area.")
        .defaultValue(true)
        .visible(tracers::get)
        .build()
    );

    private final List<Area> areas = new ArrayList<>();
    private final List<Area> candidates = new ArrayList<>();
    private final List<Area> spots = new ArrayList<>();
    private final List<Area> lattice = new ArrayList<>();
    private final Set<Long> dismissed = new HashSet<>();
    private final BlockPos.MutableBlockPos scratch = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos neighbour = new BlockPos.MutableBlockPos();
    private final Map<Long, Integer> sectionState = new HashMap<>();
    private final Set<Long> loggedBlocks = new HashSet<>();
    private final StringBuilder pendingSections = new StringBuilder();
    private final StringBuilder pendingBlocks = new StringBuilder();
    private int tickCounter;
    private int hiddenCount;
    private int loggedSections;

    private static final int CLEAN = 0;
    private static final int LEAK = 1;
    private static final int EXACT = 2;

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public DebrisLeakESP() {
        super(GlazedAddon.esp, "debris-leak", "Leaks area around ancient debris like leakestan addon");
    }

    @Override
    public void onActivate() {
        areas.clear();
        candidates.clear();
        spots.clear();
        lattice.clear();
        spotBySection.clear();
        frozenSpots.clear();
        sectionState.clear();
        loggedBlocks.clear();
        pendingSections.setLength(0);
        pendingBlocks.setLength(0);
        tickCounter = 0;
        hiddenCount = 0;
        loggedSections = 0;
        loadDismissed();
    }

    @Override
    public void onDeactivate() {
        areas.clear();
        candidates.clear();
        spots.clear();
        lattice.clear();
        spotBySection.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.level == null || mc.player == null) return;
        if (++tickCounter < refresh.get()) return;
        tickCounter = 0;
        scan();
    }

    private void scan() {
        checkPlacedObsidian();
        areas.clear();
        candidates.clear();
        hiddenCount = 0;

        ChunkPos playerChunk = mc.player.chunkPosition();
        int range = chunkRange.get();
        int lowY = minY.get();
        int highY = maxY.get();

        for (int cx = playerChunk.x() - range; cx <= playerChunk.x() + range; cx++) {
            for (int cz = playerChunk.z() - range; cz <= playerChunk.z() + range; cz++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
                if (chunk == null) continue;

                LevelChunkSection[] sections = chunk.getSections();
                int baseX = chunk.getPos().getMinBlockX();
                int baseZ = chunk.getPos().getMinBlockZ();

                for (int i = 0; i < sections.length; i++) {
                    LevelChunkSection section = sections[i];
                    if (section == null || section.hasOnlyAir()) continue;

                    int sectionY = chunk.getSectionYFromSectionIndex(i);
                    int baseY = sectionY << 4;
                    if (baseY + 15 < lowY || baseY > highY) continue;

                    if (!section.maybeHas(state -> state.is(Blocks.ANCIENT_DEBRIS))) {
                        if (logClean.get()) record(cx, cz, sectionY, CLEAN, 0, null);
                        continue;
                    }

                    collect(section, cx, cz, sectionY, baseX, baseY, baseZ, lowY, highY);
                }
            }
        }

        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();

        if (sort.get() == Sort.Value) {
            areas.sort(Comparator
                .comparingInt((Area a) -> a.exact() ? 0 : 1)
                .thenComparing(Comparator.comparingDouble(DebrisLeakESP::value).reversed())
                .thenComparingDouble(a -> distSq(a, px, py, pz)));
        } else {
            areas.sort(Comparator
                .comparingInt((Area a) -> a.exact() ? 0 : 1)
                .thenComparingDouble(a -> distSq(a, px, py, pz)));
        }

        if (areas.size() > maxResults.get()) areas.subList(maxResults.get(), areas.size()).clear();

        computeSpots(px, py, pz);
        computeLattice();
        flush();
    }

    private static double value(Area area) {
        if (area.exact()) return 1.0;

        double volume = Math.max(1.0, (area.x2() - area.x1()) * (area.y2() - area.y1()) * (area.z2() - area.z1()));
        double tightness = Math.min(1.0, 800.0 / volume);

        double top = area.y2();
        double height;
        if (top <= 25.0) height = 1.0;
        else if (area.y1() <= 25.0) height = 0.7;
        else if (area.y1() <= 48.0) height = 0.35;
        else height = 0.12;

        return height * tightness;
    }

    private void computeLattice() {
        lattice.clear();
        if (!revealLattice.get() || mc.level == null) return;

        int radius = revealRadius.get();
        int step = radius * 2 + 1;
        int done = 0;

        for (Area area : areas) {
            if (done >= latticeLimit.get()) break;
            if (area.exact()) continue;

            int minX = (int) area.x1(), maxX = (int) area.x2() - 1;
            int minY = (int) area.y1(), maxY = (int) area.y2() - 1;
            int minZ = (int) area.z1(), maxZ = (int) area.z2() - 1;

            for (int y = minY + radius; y <= maxY + radius; y += step) {
                for (int z = minZ + radius; z <= maxZ + radius; z += step) {
                    for (int x = minX + radius; x <= maxX + radius; x += step) {
                        int px = Math.min(x, maxX), py = Math.min(y, maxY), pz = Math.min(z, maxZ);
                        scratch.set(px, py, pz);

                        if (!mc.level.getBlockState(scratch).is(Blocks.NETHERRACK)) continue;

                        lattice.add(new Area(px, py, pz, px + 1, py + 1, pz + 1, false, area.section()));
                    }
                }
            }

            done++;
        }
    }

    private void computeSpots(double px, double py, double pz) {
        spots.clear();
        spotBySection.clear();
        if (!obsidianSpot.get()) return;

        double radius = blastRadius.get();
        int limit = spotLimit.get();

        for (Area area : areas) {
            if (spots.size() >= limit) break;
            if (area.exact()) continue;

            int[] best = frozenSpots.get(area.section());

            if (best == null) {
                best = bestObsidian(area, radius, px, py, pz);
                if (best == null) continue;
                frozenSpots.put(area.section(), best);
            }

            Area spot = new Area(best[0], best[1], best[2], best[0] + 1, best[1] + 1, best[2] + 1, false, area.section());
            spots.add(spot);
            spotBySection.put(area.section(), spot);
        }
    }

    private void checkPlacedObsidian() {
        if (frozenSpots.isEmpty()) return;

        var iterator = frozenSpots.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<Long, int[]> entry = iterator.next();
            int[] spot = entry.getValue();
            scratch.set(spot[0], spot[1], spot[2]);

            if (!mc.level.getBlockState(scratch).is(Blocks.OBSIDIAN)) continue;

            iterator.remove();
            dismiss(entry.getKey(), spot);
        }
    }

    private void dismiss(long key, int[] spot) {
        if (!dismissed.add(key)) return;

        info("Box marked done at %d %d %d, it will not come back.", spot[0], spot[1], spot[2]);

        try {
            Path file = doneFile();
            Files.createDirectories(file.getParent());
            Files.writeString(file, key + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            error("Could not save the done list: %s", e.getMessage());
        }
    }

    private Path doneFile() {
        return mc.gameDirectory.toPath().resolve("glazed").resolve("debris_done.txt");
    }

    private void loadDismissed() {
        dismissed.clear();
        Path file = doneFile();
        if (!Files.exists(file)) return;

        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) dismissed.add(Long.parseLong(trimmed));
            }
        } catch (IOException | NumberFormatException e) {
            error("Could not read the done list: %s", e.getMessage());
        }
    }

    public String forgetDone() {
        int had = dismissed.size();
        dismissed.clear();
        frozenSpots.clear();

        try {
            Files.deleteIfExists(doneFile());
        } catch (IOException e) {
            return "Cleared " + had + " in memory but could not delete the file: " + e.getMessage();
        }

        return "Forgot " + had + " dismissed boxes, they will show up again.";
    }

    private int[] bestObsidian(Area area, double radius, double px, double py, double pz) {
        int minBx = (int) area.x1(), maxBx = (int) area.x2() - 1;
        int minBy = (int) area.y1(), maxBy = (int) area.y2() - 1;
        int minBz = (int) area.z1(), maxBz = (int) area.z2() - 1;

        if (maxBx < minBx || maxBy < minBy || maxBz < minBz) return null;

        int seedX = seedAxis(minBx, maxBx, px, radius);
        int seedY = minBy;
        int seedZ = seedAxis(minBz, maxBz, pz, radius);

        int bestScore = -1;
        double bestDist = Double.MAX_VALUE;
        int[] best = null;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    int x = Math.clamp(seedX + dx, minBx, maxBx);
                    int y = Math.clamp(seedY + dy, minBy, maxBy);
                    int z = Math.clamp(seedZ + dz, minBz, maxBz);

                    int score = coverage(x, y, z, radius, minBx, maxBx, minBy, maxBy, minBz, maxBz);
                    double ddx = x + 0.5 - px, ddy = y + 0.5 - py, ddz = z + 0.5 - pz;
                    double dist = ddx * ddx + ddy * ddy + ddz * ddz;

                    if (score > bestScore || (score == bestScore && dist < bestDist)) {
                        bestScore = score;
                        bestDist = dist;
                        best = new int[]{x, y, z};
                    }
                }
            }
        }

        return best;
    }

    private static int seedAxis(int min, int max, double player, double radius) {
        int extent = max - min + 1;
        if (extent <= radius * 2.0) return (min + max) / 2;

        int margin = (int) radius;
        return Math.clamp((int) Math.floor(player), min + margin, max - margin);
    }

    private static int coverage(int ox, int oy, int oz, double radius, int minBx, int maxBx, int minBy, int maxBy, int minBz, int maxBz) {
        double ex = ox + 0.5, ey = oy + 1.0, ez = oz + 0.5;
        double rSq = radius * radius;

        int loX = Math.max(minBx, (int) Math.floor(ex - radius));
        int hiX = Math.min(maxBx, (int) Math.ceil(ex + radius));
        int loY = Math.max(minBy, oy);
        int hiY = Math.min(maxBy, (int) Math.ceil(ey + radius));
        int loZ = Math.max(minBz, (int) Math.floor(ez - radius));
        int hiZ = Math.min(maxBz, (int) Math.ceil(ez + radius));

        int count = 0;

        for (int x = loX; x <= hiX; x++) {
            double dx = x + 0.5 - ex;
            double dxSq = dx * dx;

            for (int y = loY; y <= hiY; y++) {
                double dy = y + 0.5 - ey;
                double dySq = dy * dy;
                if (dxSq + dySq > rSq) continue;

                for (int z = loZ; z <= hiZ; z++) {
                    double dz = z + 0.5 - ez;
                    if (dxSq + dySq + dz * dz <= rSq) count++;
                }
            }
        }

        return count;
    }

    private void collect(LevelChunkSection section, int cx, int cz, int sectionY, int baseX, int baseY, int baseZ, int lowY, int highY) {
        long key = sectionKey(cx, cz, sectionY);
        int exactBlocks = 0;

        for (int y = 0; y < 16; y++) {
            int worldY = baseY + y;
            if (worldY < lowY || worldY > highY) continue;

            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    if (!section.getBlockState(x, y, z).is(Blocks.ANCIENT_DEBRIS)) continue;

                    exactBlocks++;
                    logBlock(baseX + x, worldY, baseZ + z);

                    if (showExact.get()) {
                        areas.add(new Area(baseX + x, worldY, baseZ + z, baseX + x + 1, worldY + 1, baseZ + z + 1, true, key));
                    }
                }
            }
        }

        if (exactBlocks > 0) {
            record(cx, cz, sectionY, EXACT, exactBlocks, null);
            return;
        }

        if (dismissed.contains(key)) return;

        hiddenCount++;

        if (!showHidden.get()) {
            record(cx, cz, sectionY, LEAK, 0, null);
            return;
        }

        if (narrow.get() == Narrow.Off) {
            Area box = new Area(baseX, Math.max(baseY, lowY), baseZ, baseX + 16, Math.min(baseY + 16, highY + 1), baseZ + 16, false, key);
            areas.add(box);
            record(cx, cz, sectionY, LEAK, 0, box);
            return;
        }

        int candidateCells = 0;
        int minCx = Integer.MAX_VALUE, minCy = Integer.MAX_VALUE, minCz = Integer.MAX_VALUE;
        int maxCx = Integer.MIN_VALUE, maxCy = Integer.MIN_VALUE, maxCz = Integer.MIN_VALUE;
        boolean any = false;

        for (int y = 0; y < 16; y++) {
            int worldY = baseY + y;
            if (worldY < lowY || worldY > highY) continue;

            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    if (!section.getBlockState(x, y, z).is(Blocks.NETHERRACK)) continue;

                    int worldX = baseX + x;
                    int worldZ = baseZ + z;
                    if (!enclosed(worldX, worldY, worldZ)) continue;

                    any = true;
                    candidateCells++;
                    if (worldX < minCx) minCx = worldX;
                    if (worldY < minCy) minCy = worldY;
                    if (worldZ < minCz) minCz = worldZ;
                    if (worldX > maxCx) maxCx = worldX;
                    if (worldY > maxCy) maxCy = worldY;
                    if (worldZ > maxCz) maxCz = worldZ;

                    if (showCandidates.get()) {
                        candidates.add(new Area(worldX, worldY, worldZ, worldX + 1, worldY + 1, worldZ + 1, false, key));
                    }
                }
            }
        }

        if (!any) {
            Area box = new Area(baseX, Math.max(baseY, lowY), baseZ, baseX + 16, Math.min(baseY + 16, highY + 1), baseZ + 16, false, key);
            areas.add(box);
            record(cx, cz, sectionY, LEAK, 0, box);
            return;
        }

        Area box = new Area(minCx, minCy, minCz, maxCx + 1, maxCy + 1, maxCz + 1, false, key);
        areas.add(box);
        record(cx, cz, sectionY, LEAK, candidateCells, box);
    }

    private static long sectionKey(int cx, int cz, int sectionY) {
        return ((long) (cx & 0x3FFFFFF) << 38) | ((long) (cz & 0x3FFFFFF) << 12) | ((sectionY + 2048) & 0xFFF);
    }

    private void record(int cx, int cz, int sectionY, int state, int count, Area box) {
        if (!log.get()) return;

        long key = sectionKey(cx, cz, sectionY);
        Integer previous = sectionState.put(key, state);
        if (previous != null && previous == state) return;

        pendingSections.append(LocalDateTime.now().format(STAMP)).append(',')
            .append(cx).append(',')
            .append(cz).append(',')
            .append(sectionY).append(',')
            .append(state == CLEAN ? "clean" : state == LEAK ? "leak" : "exact").append(',')
            .append(previous == null ? "first" : "changed").append(',')
            .append(count).append(',');

        if (box == null) pendingSections.append(",,,,,");
        else pendingSections.append((int) box.x1()).append(',')
            .append((int) box.y1()).append(',')
            .append((int) box.z1()).append(',')
            .append((int) box.x2() - 1).append(',')
            .append((int) box.y2() - 1).append(',')
            .append((int) box.z2() - 1);

        pendingSections.append('\n');
        loggedSections++;
    }

    private void logBlock(int x, int y, int z) {
        if (!log.get()) return;
        if (!loggedBlocks.add(BlockPos.asLong(x, y, z))) return;

        pendingBlocks.append(LocalDateTime.now().format(STAMP)).append(',')
            .append(x).append(',').append(y).append(',').append(z).append('\n');
    }

    private void flush() {
        if (pendingSections.isEmpty() && pendingBlocks.isEmpty()) return;

        Path dir = mc.gameDirectory.toPath().resolve("glazed");

        try {
            Files.createDirectories(dir);
            append(dir.resolve("debris_sections.csv"), "time,chunk_x,chunk_z,section_y,state,kind,count,min_x,min_y,min_z,max_x,max_y,max_z\n", pendingSections);
            append(dir.resolve("debris_blocks.csv"), "time,x,y,z\n", pendingBlocks);
        } catch (IOException e) {
            error("Could not write the debris log: %s", e.getMessage());
            log.set(false);
        }

        pendingSections.setLength(0);
        pendingBlocks.setLength(0);
    }

    private void append(Path file, String header, StringBuilder body) throws IOException {
        if (body.isEmpty()) return;

        if (!Files.exists(file)) Files.writeString(file, header, StandardCharsets.UTF_8, StandardOpenOption.CREATE);

        Files.writeString(file, body.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private boolean enclosed(int x, int y, int z) {
        scratch.set(x, y, z);

        for (Direction direction : Direction.values()) {
            neighbour.setWithOffset(scratch, direction);
            BlockState state = mc.level.getBlockState(neighbour);
            if (!state.isSolidRender()) return false;
        }

        return true;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.level == null || areas.isEmpty()) return;

        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();
        double maxSq = (double) maxDistance.get() * maxDistance.get();

        Area nearest = null;
        double nearestSq = Double.MAX_VALUE;

        for (Area area : areas) {
            double distSq = distSq(area, px, py, pz);
            if (distSq > maxSq) continue;

            drawBox(event, area, area.exact() ? exactColor.get() : hiddenColor.get());

            if (!tracers.get()) continue;
            if (tracerTarget.get() == TracerTarget.Spot && !area.exact() && !spotBySection.containsKey(area.section())) continue;

            if (nearestOnly.get()) {
                if (distSq < nearestSq) {
                    nearestSq = distSq;
                    nearest = area;
                }
            } else {
                tracer(event, area);
            }
        }

        if (showCandidates.get()) {
            Color faded = hiddenColor.get();

            for (Area candidate : candidates) {
                if (distSq(candidate, px, py, pz) > maxSq) continue;
                drawBox(event, candidate, faded);
            }
        }

        if (!lattice.isEmpty()) {
            Color line = latticeColor.get();
            Color side = new Color(line.r, line.g, line.b, sideAlpha.get());

            for (Area point : lattice) {
                if (distSq(point, px, py, pz) > maxSq) continue;

                event.renderer.box(
                    point.x1(), point.y1(), point.z1(),
                    point.x2(), point.y2(), point.z2(),
                    side, line, shapeMode.get(), 0);
            }
        }

        if (!spots.isEmpty()) {
            Color spot = spotColor.get();

            for (Area area : spots) {
                if (distSq(area, px, py, pz) > maxSq) continue;

                drawBox(event, area, spot);

                if (showCrystal.get()) {
                    Area crystal = new Area(area.x1(), area.y1() + 1, area.z1(), area.x2(), area.y2() + 1, area.z2(), false, area.section());
                    event.renderer.box(
                        crystal.x1(), crystal.y1(), crystal.z1(),
                        crystal.x2(), crystal.y2(), crystal.z2(),
                        new Color(spot.r, spot.g, spot.b, 0), spot, ShapeMode.Lines, 0);
                }
            }
        }

        if (tracers.get() && nearestOnly.get() && nearest != null) tracer(event, nearest);
    }

    private void drawBox(Render3DEvent event, Area area, Color line) {
        Color side = new Color(line.r, line.g, line.b, sideAlpha.get());

        event.renderer.box(
            area.x1(), area.y1(), area.z1(),
            area.x2(), area.y2(), area.z2(),
            side, line, shapeMode.get(), 0);
    }

    private void tracer(Render3DEvent event, Area area) {
        Area target = area;
        Color color = area.exact() ? exactColor.get() : hiddenColor.get();

        if (tracerTarget.get() == TracerTarget.Spot && !area.exact()) {
            Area spot = spotBySection.get(area.section());

            if (spot != null) {
                target = spot;
                color = spotColor.get();
            }
        }

        event.renderer.line(
            RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z,
            target.cx(), target.cy(), target.cz(),
            color);
    }

    private static double distSq(Area area, double px, double py, double pz) {
        double dx = area.cx() - px;
        double dy = area.cy() - py;
        double dz = area.cz() - pz;
        return dx * dx + dy * dy + dz * dz;
    }

    @Override
    public String getInfoString() {
        String base = hiddenCount + " hidden";
        if (!lattice.isEmpty()) base += ", " + lattice.size() + " to break";
        if (!log.get()) return base;
        return base + ", " + loggedSections + " logged";
    }
}
