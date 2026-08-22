package com.nnpg.glazed.modules.esp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.Map;

public class RegionMap extends Module {
    private static final Logger LOG = LoggerFactory.getLogger("Glazed");

    private final SettingGroup displaySettings = settings.createGroup("Display");
    private final SettingGroup positionSettings = settings.createGroup("Position");
    private final SettingGroup visualSettings = settings.createGroup("Visual");
    private final SettingGroup themeSettings = settings.createGroup("Theme");

    private final Setting<Integer> mapPosX = positionSettings.add(new IntSetting.Builder()
            .name("position-x")
            .description("Horizontal position of the region map.")
            .defaultValue(15)
            .min(0)
            .sliderMax(1920)
            .build());

    private final Setting<Integer> mapPosY = positionSettings.add(new IntSetting.Builder()
            .name("position-y")
            .description("Vertical position of the region map.")
            .defaultValue(15)
            .min(0)
            .sliderMax(1080)
            .build());

    private final Setting<Integer> cellDimension = positionSettings.add(new IntSetting.Builder()
            .name("cell-size")
            .description("Size of each map cell.")
            .defaultValue(22)
            .range(12, 50)
            .sliderRange(12, 50)
            .build());

    private final Setting<Boolean> enableCoordinates = displaySettings.add(new BoolSetting.Builder()
            .name("enable-coordinates")
            .description("Display player coordinates below map.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> enableRegionLabels = displaySettings.add(new BoolSetting.Builder()
            .name("enable-region-labels")
            .description("Show the region labels below the map.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> enableMapGrid = displaySettings.add(new BoolSetting.Builder()
            .name("enable-grid")
            .description("Draw grid lines between regions.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> enablePlayerIndicator = displaySettings.add(new BoolSetting.Builder()
            .name("enable-player-indicator")
            .description("Show player position and direction on map.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> advancedMap = displaySettings.add(new BoolSetting.Builder()
            .name("advanced-region-map")
            .description("Use the new baltop/media region map")
            .defaultValue(false)
            .build());

    private final Setting<Double> mapTransparency = visualSettings.add(new DoubleSetting.Builder()
            .name("transparency")
            .description("Map background transparency level.")
            .defaultValue(0.75)
            .range(0.1, 1.0)
            .sliderRange(0.1, 1.0)
            .build());

    private final Setting<Double> labelTextSize = visualSettings.add(new DoubleSetting.Builder()
            .name("label-size")
            .description("Size of region number labels.")
            .defaultValue(0.9)
            .range(0.4, 2.5)
            .sliderRange(0.4, 2.5)
            .build());

    private final Setting<SettingColor> mapBackgroundColor = themeSettings.add(new ColorSetting.Builder()
            .name("background-color")
            .description("Map background color.")
            .defaultValue(new SettingColor(25, 25, 25, 180))
            .build());

    private final Setting<SettingColor> playerIndicatorColor = themeSettings.add(new ColorSetting.Builder()
            .name("player-color")
            .description("Player direction indicator color.")
            .defaultValue(new SettingColor(255, 50, 50, 255))
            .build());

    private final Setting<SettingColor> gridLineColor = themeSettings.add(new ColorSetting.Builder()
            .name("grid-color")
            .description("Grid line color.")
            .defaultValue(new SettingColor(15, 15, 15, 255))
            .build());

    private final MapDataManager mapData;
    private final RegionRenderer regionRenderer;
    private final PlayerTracker playerTracker;

    public RegionMap() {
        super(GlazedAddon.esp, "region-map",
                "DonutSMP region map and shows you your location");

        this.mapData = new MapDataManager();
        this.regionRenderer = new RegionRenderer();
        this.playerTracker = new PlayerTracker();
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!isValidRenderState()) return;

        try {
            Vec3 playerPos = new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ());
            MapRenderContext ctx = new MapRenderContext(
                    mapPosX.get(), mapPosY.get(),
                    cellDimension.get(), mapTransparency.get()
            );

            regionRenderer.renderMapBackground(ctx);

            boolean legacyMode = !advancedMap.get();
            regionRenderer.renderRegionCells(ctx, mapData, legacyMode);

            if (enableMapGrid.get()) {
                regionRenderer.renderGridLines(ctx, gridLineColor.get());
            }

            regionRenderer.renderRegionNumbers(ctx, mapData, labelTextSize.get());

            if (enablePlayerIndicator.get()) {
                playerTracker.renderPlayerPosition(ctx, playerPos,
                        mc.player.getYRot(), playerIndicatorColor.get());
            }

            if (legacyMode) {
                if (enableCoordinates.get()) {
                    renderPlayerInfoLegacy(ctx, playerPos);
                }

                if (enableRegionLabels.get()) {
                    renderRegionLegendLegacy(ctx);
                }
            } else {
                int legendBaseY = ctx.mapY + ctx.getMapHeight() + 8;

                if (enableCoordinates.get()) {
                    legendBaseY = renderPlayerInfoAdvanced(ctx, playerPos, legendBaseY);
                }

                if (enableRegionLabels.get()) {
                    renderRegionLegendAdvanced(ctx, legendBaseY);
                }
            }
        } catch (Exception e) {
            LOG.error("Unhandled error in " + getClass().getSimpleName(), e);
        }
    }

    private boolean isValidRenderState() {
        return mc != null && mc.player != null && mc.level != null;
    }

    private void renderPlayerInfoLegacy(MapRenderContext ctx, Vec3 pos) {
        if (pos == null || ctx == null) return;

        try {
            int infoY = ctx.mapY + ctx.getMapHeight() + 8;

            TextRenderer textRenderer = TextRenderer.get();
            if (textRenderer == null) return;

            textRenderer.begin(1.0, false, true);

            String coordsText = String.format("Position: X: %d, Z: %d", (int) pos.x, (int) pos.z);
            textRenderer.render(coordsText, ctx.mapX, infoY, Color.WHITE, false);

            int currentRegionId = mapData.getRegionAt(pos.x, pos.z);
            if (currentRegionId != -1) {
                String regionInfo = String.format("Current Region: %d (%s)",
                        currentRegionId, mapData.getContinentName(pos.x, pos.z));
                textRenderer.render(regionInfo, ctx.mapX, infoY + 15, Color.WHITE, false);
            }

            textRenderer.end();
        } catch (Exception e) {
            LOG.error("Unhandled error in " + getClass().getSimpleName(), e);
        }
    }

    private void renderRegionLegendLegacy(MapRenderContext ctx) {
        if (ctx == null) return;

        try {
            int legendStartY = ctx.mapY + ctx.getMapHeight() +
                    (enableCoordinates.get() ? 45 : 15);

            String[] continentNames = mapData.getContinentNames();
            Color[] continentColors = mapData.getContinentColors();

            if (continentNames == null || continentColors == null) return;

            Renderer2D.COLOR.begin();
            for (int i = 0; i < continentNames.length && i < continentColors.length; i++) {
                int legendY = legendStartY + i * 16;
                Renderer2D.COLOR.quad(ctx.mapX, legendY, 14, 14, continentColors[i]);
            }
            Renderer2D.COLOR.render();

            TextRenderer textRenderer = TextRenderer.get();
            if (textRenderer == null) return;

            textRenderer.begin(1.0, false, true);
            for (int i = 0; i < continentNames.length; i++) {
                int legendY = legendStartY + i * 16 + 3;
                textRenderer.render(continentNames[i], ctx.mapX + 18, legendY, Color.WHITE, false);
            }
            textRenderer.end();
        } catch (Exception e) {
            LOG.error("Unhandled error in " + getClass().getSimpleName(), e);
        }
    }

    private int renderPlayerInfoAdvanced(MapRenderContext ctx, Vec3 pos, int startY) {
        if (pos == null || ctx == null) return startY;

        try {
            TextRenderer textRenderer = TextRenderer.get();
            if (textRenderer == null) return startY;

            textRenderer.begin(1.0, false, true);

            String coordsText = String.format("Position: X: %d, Z: %d", (int) pos.x, (int) pos.z);
            textRenderer.render(coordsText, ctx.mapX, startY, Color.WHITE, false);

            int regionId = mapData.getRegionAt(pos.x, pos.z);
            if (regionId != -1) {
                String regionInfo = String.format("Current Region: %d (%s)",
                        regionId, mapData.getContinentName(pos.x, pos.z));
                textRenderer.render(regionInfo, ctx.mapX, startY + 15, Color.WHITE, false);
            }

            textRenderer.end();

            return startY + (regionId != -1 ? 31 : 16);
        } catch (Exception e) {
            LOG.error("Unhandled error in " + getClass().getSimpleName(), e);
            return startY;
        }
    }

    private int renderRegionLegendAdvanced(MapRenderContext ctx, int startY) {
        if (ctx == null) return startY;

        try {
            RegionCategory[] categories = RegionCategory.legendCategories();

            Renderer2D.COLOR.begin();
            for (int i = 0; i < categories.length; i++) {
                int legendY = startY + i * 16;
                Renderer2D.COLOR.quad(ctx.mapX, legendY, 14, 14, categories[i].color);
            }
            Renderer2D.COLOR.render();

            TextRenderer textRenderer = TextRenderer.get();
            if (textRenderer == null) return startY + categories.length * 16;

            textRenderer.begin(1.0, false, true);
            for (int i = 0; i < categories.length; i++) {
                int legendY = startY + i * 16 + 3;
                textRenderer.render(categories[i].label, ctx.mapX + 18, legendY, Color.WHITE, false);
            }
            textRenderer.end();

            return startY + categories.length * 16 + 6;
        } catch (Exception e) {
            LOG.error("Unhandled error in " + getClass().getSimpleName(), e);
            return startY;
        }
    }

    private enum RegionCategory {
        NONE("", null),
        GLITCHED("Glitched (no rtp spots)", new Color(190, 60, 220, 255)),
        MEDIA_BALTOP("Good for media / baltop", new Color(70, 130, 220, 255)),
        MEDIA_OTHER("Good for media / other bases", new Color(170, 170, 170, 255));

        final String label;
        final Color color;

        RegionCategory(String label, Color color) {
            this.label = label;
            this.color = color;
        }

        static RegionCategory[] legendCategories() {
            return new RegionCategory[]{GLITCHED, MEDIA_BALTOP, MEDIA_OTHER};
        }
    }

    private static class MapDataManager {
        private static final int GRID_ROWS = 9;
        private static final int GRID_COLS = 9;
        private static final double REGION_SIZE = 50000.0;
        private static final double MAP_OFFSET = 225000.0;

        private static final String[] CONTINENT_NAMES = {
                "EU Central", "EU West", "NA East", "NA West", "Asia", "Oceania"
        };

        private static final Color[] CONTINENT_COLORS = {
                new Color(159, 206, 99, 255),
                new Color(0, 166, 99, 255),
                new Color(79, 173, 234, 255),
                new Color(47, 110, 186, 255),
                new Color(245, 194, 66, 255),
                new Color(252, 136, 3, 255)
        };

        private final Map<Integer, RegionInfo> regionMap;

        public MapDataManager() {
            this.regionMap = new HashMap<>();
            initializeRegionData();
        }

        private void initializeRegionData() {
            int[][] regionLayout = {
                    {82, 5}, {100, 3}, {101, 3}, {102, 3}, {103, 2}, {104, 2}, {105, 2}, {106, 2}, {91, 2},
                    {83, 5}, {44, 3}, {75, 3}, {42, 3}, {41, 2}, {40, 2}, {39, 2}, {38, 2}, {92, 2},
                    {84, 5}, {45, 3}, {14, 3}, {13, 3}, {12, 2}, {11, 2}, {10, 2}, {37, 2}, {93, 2},
                    {85, 5}, {46, 5}, {74, 5}, {3, 3}, {2, 2}, {1, 2}, {25, 2}, {36, 2}, {94, 2},
                    {86, 4}, {47, 4}, {72, 4}, {71, 4}, {5, 2}, {4, 2}, {24, 2}, {35, 2}, {95, 2},
                    {87, 4}, {51, 1}, {17, 1}, {9, 0}, {8, 0}, {7, 0}, {23, 0}, {34, 0}, {96, 2},
                    {88, 4}, {54, 1}, {18, 1}, {61, 0}, {62, 0}, {21, 0}, {22, 0}, {33, 0}, {97, 0},
                    {89, 0}, {26, 1}, {27, 0}, {28, 0}, {29, 0}, {30, 0}, {59, 0}, {32, 0}, {98, 0},
                    {90, 0}, {107, 1}, {108, 1}, {109, 1}, {110, 1}, {111, 1}, {112, 1}, {113, 1}, {99, 0}
            };

            for (int i = 0; i < regionLayout.length; i++) {
                int row = i / GRID_COLS;
                int col = i % GRID_COLS;

                int regionId = regionLayout[i][0];
                int continentType = Math.min(regionLayout[i][1], CONTINENT_NAMES.length - 1);
                RegionCategory category = CATEGORY_OVERRIDES.getOrDefault(regionId, RegionCategory.NONE);

                regionMap.put(i, new RegionInfo(regionId, category, continentType, row, col));
            }
        }

        private static final Map<Integer, RegionCategory> CATEGORY_OVERRIDES = new HashMap<>();
        static {
            CATEGORY_OVERRIDES.put(89, RegionCategory.GLITCHED);
            CATEGORY_OVERRIDES.put(90, RegionCategory.GLITCHED);
            CATEGORY_OVERRIDES.put(97, RegionCategory.GLITCHED);

            CATEGORY_OVERRIDES.put(4, RegionCategory.MEDIA_BALTOP);
            CATEGORY_OVERRIDES.put(5, RegionCategory.MEDIA_BALTOP);
            CATEGORY_OVERRIDES.put(8, RegionCategory.MEDIA_BALTOP);
            CATEGORY_OVERRIDES.put(30, RegionCategory.MEDIA_BALTOP);

            CATEGORY_OVERRIDES.put(2, RegionCategory.MEDIA_OTHER);
            CATEGORY_OVERRIDES.put(7, RegionCategory.MEDIA_OTHER);
            CATEGORY_OVERRIDES.put(28, RegionCategory.MEDIA_OTHER);
            CATEGORY_OVERRIDES.put(29, RegionCategory.MEDIA_OTHER);
            CATEGORY_OVERRIDES.put(32, RegionCategory.MEDIA_OTHER);
            CATEGORY_OVERRIDES.put(34, RegionCategory.MEDIA_OTHER);
            CATEGORY_OVERRIDES.put(59, RegionCategory.MEDIA_OTHER);
            CATEGORY_OVERRIDES.put(86, RegionCategory.MEDIA_OTHER);
            CATEGORY_OVERRIDES.put(88, RegionCategory.MEDIA_OTHER);
            CATEGORY_OVERRIDES.put(98, RegionCategory.MEDIA_OTHER);
            CATEGORY_OVERRIDES.put(99, RegionCategory.MEDIA_OTHER);
            CATEGORY_OVERRIDES.put(109, RegionCategory.MEDIA_OTHER);
            CATEGORY_OVERRIDES.put(112, RegionCategory.MEDIA_OTHER);
            CATEGORY_OVERRIDES.put(113, RegionCategory.MEDIA_OTHER);
        }

        public RegionInfo getRegionInfo(int gridIndex) {
            return regionMap.get(gridIndex);
        }

        public int getRegionAt(double worldX, double worldZ) {
            try {
                int[] gridPos = worldToGrid(worldX, worldZ);
                if (isValidGridPosition(gridPos[0], gridPos[1])) {
                    int index = gridPos[1] * GRID_COLS + gridPos[0];
                    RegionInfo info = regionMap.get(index);
                    return info != null ? info.regionId : -1;
                }
            } catch (Exception e) {
                LOG.error("Unhandled error in " + getClass().getSimpleName(), e);
            }
            return -1;
        }

        public RegionCategory getCategoryAt(double worldX, double worldZ) {
            try {
                int[] gridPos = worldToGrid(worldX, worldZ);
                if (isValidGridPosition(gridPos[0], gridPos[1])) {
                    int index = gridPos[1] * GRID_COLS + gridPos[0];
                    RegionInfo info = regionMap.get(index);
                    if (info != null) return info.category;
                }
            } catch (Exception e) {
                LOG.error("Unhandled error in " + getClass().getSimpleName(), e);
            }
            return RegionCategory.NONE;
        }

        public String getContinentName(double worldX, double worldZ) {
            try {
                int[] gridPos = worldToGrid(worldX, worldZ);
                if (isValidGridPosition(gridPos[0], gridPos[1])) {
                    int index = gridPos[1] * GRID_COLS + gridPos[0];
                    RegionInfo info = regionMap.get(index);
                    if (info != null) return CONTINENT_NAMES[info.continentType];
                }
            } catch (Exception e) {
                LOG.error("Unhandled error in " + getClass().getSimpleName(), e);
            }
            return "Unknown";
        }

        public Color getContinentColor(int continentType) {
            if (continentType >= 0 && continentType < CONTINENT_COLORS.length) {
                return CONTINENT_COLORS[continentType];
            }
            return Color.WHITE;
        }

        public String[] getContinentNames() {
            return CONTINENT_NAMES.clone();
        }

        public Color[] getContinentColors() {
            return CONTINENT_COLORS.clone();
        }

        public int[] worldToGrid(double worldX, double worldZ) {
            if (REGION_SIZE == 0) return new int[]{0, 0};

            int gridX = (int) ((worldX + MAP_OFFSET) / REGION_SIZE);
            int gridZ = (int) ((worldZ + MAP_OFFSET) / REGION_SIZE);
            return new int[]{gridX, gridZ};
        }

        public double[] worldToCellPosition(double worldX, double worldZ) {
            if (REGION_SIZE == 0) return new double[]{0.0, 0.0};

            double cellX = ((worldX + MAP_OFFSET) % REGION_SIZE) / REGION_SIZE;
            double cellZ = ((worldZ + MAP_OFFSET) % REGION_SIZE) / REGION_SIZE;

            cellX = Math.max(0.0, Math.min(1.0, cellX));
            cellZ = Math.max(0.0, Math.min(1.0, cellZ));

            return new double[]{cellX, cellZ};
        }

        private boolean isValidGridPosition(int gridX, int gridZ) {
            return gridX >= 0 && gridX < GRID_COLS && gridZ >= 0 && gridZ < GRID_ROWS;
        }

        public int getGridCols() {
            return GRID_COLS;
        }

        public int getGridRows() {
            return GRID_ROWS;
        }
    }

    private static class RegionInfo {
        final int regionId;
        final RegionCategory category;
        final int continentType;
        final int gridRow;
        final int gridCol;

        RegionInfo(int regionId, RegionCategory category, int continentType, int gridRow, int gridCol) {
            this.regionId = regionId;
            this.category = category;
            this.continentType = continentType;
            this.gridRow = gridRow;
            this.gridCol = gridCol;
        }
    }

    private class MapRenderContext {
        final int mapX, mapY;
        final int cellSize;
        final double transparency;

        MapRenderContext(int x, int y, int cellSize, double transparency) {
            this.mapX = x;
            this.mapY = y;
            this.cellSize = Math.max(1, cellSize);
            this.transparency = Math.max(0.1, Math.min(1.0, transparency));
        }

        int getMapWidth() {
            return mapData.getGridCols() * cellSize;
        }

        int getMapHeight() {
            return mapData.getGridRows() * cellSize;
        }
    }

    private class RegionRenderer {

        void renderMapBackground(MapRenderContext ctx) {
            if (ctx == null || mapBackgroundColor.get() == null) return;

            try {
                Color bgColor = new Color(mapBackgroundColor.get());
                bgColor.a = (int) (ctx.transparency * 255.0);

                Renderer2D.COLOR.begin();
                Renderer2D.COLOR.quad(ctx.mapX, ctx.mapY, ctx.getMapWidth(), ctx.getMapHeight(), bgColor);
                Renderer2D.COLOR.render();
            } catch (Exception e) {
                LOG.error("Unhandled error in " + getClass().getSimpleName(), e);
            }
        }

        void renderRegionCells(MapRenderContext ctx, MapDataManager dataManager, boolean legacyMode) {
            if (ctx == null || dataManager == null) return;

            try {
                Renderer2D.COLOR.begin();

                int rows = dataManager.getGridRows();
                int cols = dataManager.getGridCols();

                for (int row = 0; row < rows; row++) {
                    for (int col = 0; col < cols; col++) {
                        int index = row * cols + col;
                        RegionInfo regionInfo = dataManager.getRegionInfo(index);

                        if (regionInfo == null) continue;

                        Color cellColor = null;

                        if (legacyMode) {
                            cellColor = dataManager.getContinentColor(regionInfo.continentType);
                        } else if (regionInfo.category != RegionCategory.NONE) {
                            cellColor = regionInfo.category.color;
                        }

                        if (cellColor != null) {
                            int cellX = ctx.mapX + col * ctx.cellSize;
                            int cellY = ctx.mapY + row * ctx.cellSize;

                            Color fill = new Color(cellColor);
                            fill.a = (int) (ctx.transparency * 255.0);

                            Renderer2D.COLOR.quad(cellX + 1, cellY + 1,
                                    ctx.cellSize - 2, ctx.cellSize - 2, fill);
                        }
                    }
                }

                Renderer2D.COLOR.render();
            } catch (Exception e) {
                LOG.error("Unhandled error in " + getClass().getSimpleName(), e);
            }
        }

        void renderGridLines(MapRenderContext ctx, SettingColor gridColor) {
            if (ctx == null || gridColor == null) return;

            try {
                Renderer2D.COLOR.begin();
                Color lineColor = new Color(gridColor);

                int cols = mapData.getGridCols();
                int rows = mapData.getGridRows();

                for (int i = 0; i <= cols; i++) {
                    int lineX = ctx.mapX + i * ctx.cellSize;
                    Renderer2D.COLOR.quad(lineX, ctx.mapY, 1, ctx.getMapHeight(), lineColor);
                }

                for (int i = 0; i <= rows; i++) {
                    int lineY = ctx.mapY + i * ctx.cellSize;
                    Renderer2D.COLOR.quad(ctx.mapX, lineY, ctx.getMapWidth(), 1, lineColor);
                }

                Renderer2D.COLOR.render();
            } catch (Exception e) {
                LOG.error("Unhandled error in " + getClass().getSimpleName(), e);
            }
        }

        void renderRegionNumbers(MapRenderContext ctx, MapDataManager dataManager, double textScale) {
            if (ctx == null || dataManager == null) return;

            try {
                TextRenderer textRenderer = TextRenderer.get();
                if (textRenderer == null) return;

                textRenderer.begin(textScale, false, true);

                int rows = dataManager.getGridRows();
                int cols = dataManager.getGridCols();

                for (int row = 0; row < rows; row++) {
                    for (int col = 0; col < cols; col++) {
                        int index = row * cols + col;
                        RegionInfo regionInfo = dataManager.getRegionInfo(index);

                        if (regionInfo != null) {
                            int cellX = ctx.mapX + col * ctx.cellSize;
                            int cellY = ctx.mapY + row * ctx.cellSize;

                            String numberText = String.valueOf(regionInfo.regionId);
                            double textWidth = textRenderer.getWidth(numberText) * textScale;
                            double textHeight = textRenderer.getHeight() * textScale;

                            double centeredX = cellX + (ctx.cellSize - textWidth) / 2.0;
                            double centeredY = cellY + (ctx.cellSize - textHeight) / 2.0;

                            textRenderer.render(numberText, centeredX, centeredY, Color.WHITE, false);
                        }
                    }
                }

                textRenderer.end();
            } catch (Exception e) {
                LOG.error("Unhandled error in " + getClass().getSimpleName(), e);
            }
        }
    }

    private class PlayerTracker {

        void renderPlayerPosition(MapRenderContext ctx, Vec3 playerPos, float yaw, SettingColor indicatorColor) {
            if (ctx == null || playerPos == null || indicatorColor == null) return;

            try {
                int[] gridPos = mapData.worldToGrid(playerPos.x, playerPos.z);

                if (gridPos[0] >= 0 && gridPos[0] < mapData.getGridCols() &&
                        gridPos[1] >= 0 && gridPos[1] < mapData.getGridRows()) {

                    double[] cellPos = mapData.worldToCellPosition(playerPos.x, playerPos.z);

                    int indicatorX = ctx.mapX + gridPos[0] * ctx.cellSize + (int) (cellPos[0] * ctx.cellSize);
                    int indicatorY = ctx.mapY + gridPos[1] * ctx.cellSize + (int) (cellPos[1] * ctx.cellSize);

                    double rotationAngle = Math.toRadians(-yaw - 90.0);
                    renderDirectionalIndicator(indicatorX, indicatorY, rotationAngle, indicatorColor);
                }
            } catch (Exception e) {
                LOG.error("Unhandled error in " + getClass().getSimpleName(), e);
            }
        }

        private void renderDirectionalIndicator(int centerX, int centerY, double angle, SettingColor color) {
            try {
                Renderer2D.COLOR.begin();
                Color indicatorCol = new Color(color);
                int arrowSize = 9;

                int tipX = centerX + (int) (Math.cos(angle) * arrowSize);
                int tipY = centerY - (int) (Math.sin(angle) * arrowSize);

                double leftBaseAngle = angle + Math.toRadians(135.0);
                double rightBaseAngle = angle - Math.toRadians(135.0);

                int leftBaseX = centerX + (int) (Math.cos(leftBaseAngle) * arrowSize);
                int leftBaseY = centerY - (int) (Math.sin(leftBaseAngle) * arrowSize);
                int rightBaseX = centerX + (int) (Math.cos(rightBaseAngle) * arrowSize);
                int rightBaseY = centerY - (int) (Math.sin(rightBaseAngle) * arrowSize);

                drawTriangleFilled(tipX, tipY, leftBaseX, leftBaseY, rightBaseX, rightBaseY, indicatorCol);
                Renderer2D.COLOR.render();
            } catch (Exception e) {
                LOG.error("Unhandled error in " + getClass().getSimpleName(), e);
            }
        }

        private void drawTriangleFilled(int x1, int y1, int x2, int y2, int x3, int y3, Color color) {
            if (color == null) return;

            int minY = Math.min(y1, Math.min(y2, y3));
            int maxY = Math.max(y1, Math.max(y2, y3));

            for (int scanY = minY; scanY <= maxY; scanY++) {
                int leftX = Integer.MAX_VALUE;
                int rightX = Integer.MIN_VALUE;

                int[] intersections = {
                        getEdgeIntersection(x1, y1, x2, y2, scanY),
                        getEdgeIntersection(x2, y2, x3, y3, scanY),
                        getEdgeIntersection(x3, y3, x1, y1, scanY)
                };

                for (int intersection : intersections) {
                    if (intersection != Integer.MAX_VALUE) {
                        leftX = Math.min(leftX, intersection);
                        rightX = Math.max(rightX, intersection);
                    }
                }

                if (leftX <= rightX && leftX != Integer.MAX_VALUE) {
                    Renderer2D.COLOR.quad(leftX, scanY, rightX - leftX + 1, 1, color);
                }
            }
        }

        private int getEdgeIntersection(int x1, int y1, int x2, int y2, int scanY) {
            if (scanY >= Math.min(y1, y2) && scanY <= Math.max(y1, y2)) {
                if (y1 == y2) {
                    return (x1 + x2) / 2;
                } else {
                    return x1 + (x2 - x1) * (scanY - y1) / (y2 - y1);
                }
            }
            return Integer.MAX_VALUE;
        }
    }
}
