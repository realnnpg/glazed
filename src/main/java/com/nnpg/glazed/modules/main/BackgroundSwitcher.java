package com.nnpg.glazed.modules.main;

import com.mojang.blaze3d.platform.NativeImage;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class BackgroundSwitcher extends Module {
    private record Entry(boolean custom, String stem, Path path) {}

    private static final Logger LOG = LoggerFactory.getLogger("glazed-backgrounds");
    private static final String VANILLA = "Vanilla";
    private static final String PREFERRED_DEFAULT = "Glazed Default";
    private static final String NAMESPACE = "backgrounds";
    private static final Pattern VALID = Pattern.compile("[a-z0-9_\\-]+");
    private static final Set<String> CUSTOM_EXT = Set.of("png", "jpg", "jpeg", "gif", "bmp");
    private static final Path CUSTOM_DIR = FabricLoader.getInstance().getGameDir().resolve("glazed").resolve("backgrounds");

    private static String cachedName = null;
    private static Identifier cachedId = null;
    private static Identifier dynamicId = null;
    private static int cachedW = -1;
    private static int cachedH = -1;

    private GuiTheme uiTheme = null;
    private WVerticalList uiColumn = null;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<String> background = sgGeneral.add(new StringSetting.Builder()
            .name("background")
            .description("Which background to show behind menus. Vanilla keeps the normal spinning panorama.")
            .defaultValue(defaultChoice())
            .visible(() -> false)
            .build()
    );

    public final Setting<String> importPath = sgGeneral.add(new StringSetting.Builder()
            .name("import-background")
            .description("Paste the full path of an image (png, jpg, gif, bmp) and press enter to import it as a background.")
            .defaultValue("")
            .onChanged(this::importFile)
            .build()
    );

    public BackgroundSwitcher() {
        super(Categories.Misc, "background-switcher", "Pick which background is shown behind the menus. The setting applies even when this module is toggled off.");
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList column = theme.verticalList();
        uiTheme = theme;
        uiColumn = column;
        build(theme, column);
        return column;
    }

    private void build(GuiTheme theme, WVerticalList column) {
        column.clear();

        List<String> options = choices();
        String current = background.get();
        if (!options.contains(current)) {
            current = defaultChoice();
            background.set(current);
        }

        WHorizontalList selectRow = column.add(theme.horizontalList()).widget();
        selectRow.add(theme.label("Background"));
        WDropdown<String> dropdown = selectRow.add(theme.dropdown(options.toArray(new String[0]), current)).widget();
        dropdown.action = () -> background.set(dropdown.get());

        WHorizontalList top = column.add(theme.horizontalList()).widget();

        WButton pick = top.add(theme.button("Import background...")).widget();
        pick.action = this::chooseAndImport;

        WButton open = top.add(theme.button("Show backgrounds folder")).widget();
        open.action = () -> {
            try {
                Files.createDirectories(CUSTOM_DIR);
                openFolder(CUSTOM_DIR);
            } catch (IOException e) {
                LOG.warn("Could not open backgrounds folder", e);
            }
        };

        WButton reload = top.add(theme.button("Reload")).widget();
        reload.action = () -> {
            reload();
            queueRefresh();
        };

        WHorizontalList bottom = column.add(theme.horizontalList()).widget();

        String removeLabel = "Remove selected imported background";
        String blockedLabel = "Can't remove built-in backgrounds";
        WButton remove = bottom.add(theme.button(removeLabel)).widget();
        String[] armedFor = {null};
        remove.action = () -> {
            String selected = background.get();
            Entry entry = scan().get(selected);
            if (entry == null || !entry.custom()) {
                armedFor[0] = null;
                remove.set(blockedLabel);
                warning("Can't remove built-in backgrounds.");
                return;
            }
            if (!selected.equals(armedFor[0])) {
                armedFor[0] = selected;
                remove.set("Click again to delete " + selected);
                return;
            }
            armedFor[0] = null;
            removeSelected();
            queueRefresh();
        };
    }

    private void queueRefresh() {
        Minecraft.getInstance().execute(() -> {
            if (uiTheme != null && uiColumn != null) build(uiTheme, uiColumn);
        });
    }

    private void removeSelected() {
        String label = background.get();
        Entry entry = scan().get(label);
        if (entry == null || !entry.custom()) {
            warning("Can't remove built-in backgrounds.");
            return;
        }
        try {
            Files.deleteIfExists(entry.path());
        } catch (IOException e) {
            error("Could not delete that background.");
            LOG.warn("Delete failed", e);
            return;
        }
        cachedName = null;
        cachedId = null;
        releaseDynamic();
        String next = defaultChoice();
        background.set(next);
        info("Removed %s. Switched to %s.", label, next);
    }

    private static void openFolder(Path dir) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if (os.contains("win")) {
                new ProcessBuilder("explorer.exe", dir.toString()).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", dir.toString()).start();
            } else {
                new ProcessBuilder("xdg-open", dir.toString()).start();
            }
        } catch (IOException e) {
            LOG.warn("Could not open folder", e);
        }
    }

    public static void reload() {
        cachedName = null;
    }

    private void chooseAndImport() {
        String picked;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            String[] patterns = {"*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"};
            PointerBuffer filters = stack.mallocPointer(patterns.length);
            for (String pattern : patterns) filters.put(stack.UTF8(pattern));
            filters.flip();

            Path home = Path.of(System.getProperty("user.home", ""));
            Path start = home;
            for (Path candidate : new Path[]{home.resolve("Pictures"), home.resolve("OneDrive").resolve("Pictures")}) {
                if (Files.isDirectory(candidate)) {
                    start = candidate;
                    break;
                }
            }
            String startPath = start.toString() + java.io.File.separator;

            picked = TinyFileDialogs.tinyfd_openFileDialog("Choose a background to import", startPath, filters, "Images (png, jpg, gif, bmp)", false);
        } catch (LinkageError | RuntimeException e) {
            LOG.warn("File picker unavailable", e);
            error("The file picker is not available here. Paste the path into the import-background box instead.");
            return;
        }

        if (picked == null || picked.isBlank()) return;
        try {
            importPicture(Path.of(picked));
        } catch (InvalidPathException e) {
            error("That is not a valid path.");
        }
    }

    private void importFile(String raw) {
        if (raw == null || raw.isBlank()) return;
        try {
            importPicture(Path.of(raw.trim().replace("\"", "")));
        } catch (InvalidPathException e) {
            error("That is not a valid path.");
        }
        importPath.set("");
    }

    private void importPicture(Path src) {
        try {
            String fileName = src.getFileName().toString();
            int dot = fileName.lastIndexOf('.');
            String ext = dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
            if (!Files.isRegularFile(src)) {
                error("That file does not exist.");
                return;
            }
            if (!CUSTOM_EXT.contains(ext)) {
                error("Unsupported file type. Use png, jpg, gif or bmp.");
                return;
            }
            Files.createDirectories(CUSTOM_DIR);
            Path dest = CUSTOM_DIR.resolve(fileName);
            Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
            cachedName = null;
            for (Map.Entry<String, Entry> e : scan().entrySet()) {
                if (e.getValue().custom() && e.getValue().path().equals(dest)) {
                    background.set(e.getKey());
                    break;
                }
            }
            info("Imported %s.", fileName);
            queueRefresh();
        } catch (IOException e) {
            error("Could not import that background.");
            LOG.warn("Import failed", e);
        }
    }

    private static String defaultChoice() {
        Map<String, Entry> found = scan();
        if (found.containsKey(PREFERRED_DEFAULT)) return PREFERRED_DEFAULT;
        return found.isEmpty() ? VANILLA : found.keySet().iterator().next();
    }

    private static List<String> choices() {
        List<String> all = new ArrayList<>();
        all.add(VANILLA);
        all.addAll(scan().keySet());
        return all;
    }

    private static String prettify(String stem) {
        StringBuilder sb = new StringBuilder();
        for (String part : stem.split("[-_]+")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    private static Map<String, Entry> scan() {
        Map<String, Entry> out = new TreeMap<>();

        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            Optional<Path> dir = mod.findPath("assets/" + NAMESPACE);
            if (dir.isEmpty()) continue;
            try (Stream<Path> files = Files.list(dir.get())) {
                files.forEach(p -> {
                    String file = p.getFileName().toString();
                    if (!file.toLowerCase(Locale.ROOT).endsWith(".png")) return;
                    String stem = file.substring(0, file.length() - 4);
                    if (VALID.matcher(stem).matches()) {
                        out.putIfAbsent(prettify(stem), new Entry(false, stem, p));
                    } else {
                        LOG.warn("Skipping bundled background '{}': use only lowercase letters, numbers, - and _ in the file name", file);
                    }
                });
            } catch (IOException e) {
                LOG.warn("Could not read backgrounds folder", e);
            }
        }

        try {
            Files.createDirectories(CUSTOM_DIR);
            try (Stream<Path> files = Files.list(CUSTOM_DIR)) {
                files.filter(Files::isRegularFile).sorted().forEach(p -> {
                    String file = p.getFileName().toString();
                    int dot = file.lastIndexOf('.');
                    if (dot <= 0) return;
                    String ext = file.substring(dot + 1).toLowerCase(Locale.ROOT);
                    if (!CUSTOM_EXT.contains(ext)) return;
                    String stem = file.substring(0, dot);
                    String label = (out.containsKey(stem) || stem.equals(VANILLA)) ? stem + " (custom)" : stem;
                    out.putIfAbsent(label, new Entry(true, stem, p));
                });
            }
        } catch (IOException e) {
            LOG.warn("Could not read custom pictures folder", e);
        }

        return out;
    }

    private static void releaseDynamic() {
        if (dynamicId != null) {
            Minecraft.getInstance().getTextureManager().release(dynamicId);
            dynamicId = null;
        }
    }

    private static void load(String label) {
        cachedName = label;
        cachedId = null;
        cachedW = -1;
        cachedH = -1;
        releaseDynamic();

        Entry entry = scan().get(label);
        if (entry == null) return;

        if (entry.custom()) {
            loadCustom(entry);
        } else {
            loadBundled(entry);
        }
    }

    private static void loadBundled(Entry entry) {
        Identifier id = Identifier.fromNamespaceAndPath(NAMESPACE, entry.stem() + ".png");
        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(id);
        if (res.isEmpty()) return;
        try (InputStream in = Minecraft.getInstance().getResourceManager().open(id); DataInputStream data = new DataInputStream(in)) {
            data.skipNBytes(16);
            cachedW = data.readInt();
            cachedH = data.readInt();
            cachedId = id;
        } catch (IOException e) {
            LOG.warn("Could not read size of background {}", entry.stem(), e);
        }
    }

    private static void loadCustom(Entry entry) {
        try {
            BufferedImage img = ImageIO.read(entry.path().toFile());
            if (img == null) return;
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            if (!ImageIO.write(img, "png", png)) return;
            NativeImage image = NativeImage.read(new ByteArrayInputStream(png.toByteArray()));
            Identifier id = Identifier.fromNamespaceAndPath("glazed", "custom_bg/" + Integer.toHexString(entry.path().toString().hashCode()));
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "glazed custom background", image));
            dynamicId = id;
            cachedId = id;
            cachedW = image.getWidth();
            cachedH = image.getHeight();
        } catch (IOException | RuntimeException e) {
            LOG.warn("Could not load custom background {}", entry.path(), e);
        }
    }

    public static boolean draw(GuiGraphicsExtractor graphics, int screenW, int screenH) {
        Modules modules = Modules.get();
        if (modules == null) return false;
        BackgroundSwitcher module = modules.get(BackgroundSwitcher.class);
        if (module == null) return false;

        String name = module.background.get();
        if (name == null || name.equals(VANILLA)) return false;

        if (!name.equals(cachedName)) load(name);
        if (cachedId == null || cachedW <= 0 || cachedH <= 0) return false;

        float scale = Math.max(screenW / (float) cachedW, screenH / (float) cachedH);
        int regionW = Math.min(cachedW, Math.round(screenW / scale));
        int regionH = Math.min(cachedH, Math.round(screenH / scale));
        float u = (cachedW - regionW) / 2f;
        float v = (cachedH - regionH) / 2f;

        graphics.blit(RenderPipelines.GUI_TEXTURED, cachedId, 0, 0, u, v, screenW, screenH, regionW, regionH, cachedW, cachedH);
        return true;
    }
}