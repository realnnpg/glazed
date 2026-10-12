package com.nnpg.glazed;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.utils.network.Http;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyScreen extends WindowScreen {

    private static final Logger LOG = LoggerFactory.getLogger("Glazed");

    private static volatile boolean hasCheckedThisSession = false;

    // written on the net thread, read on render thread
    private volatile String latestVersion = null;
    private volatile boolean isVersionFetched = false;
    private volatile boolean fetchFailed = false;

    public MyScreen(GuiTheme theme) {
        super(theme, "Version Check");
        fetchLatestVersion();
    }

    public static void checkVersionOnServerJoin() {
        if (hasCheckedThisSession) return;
        hasCheckedThisSession = true;

        MeteorExecutor.execute(() -> {
            String latest = fetchVersion();
            if (latest == null) return;

            if (isNewerThanInstalled(latest)) {
                Minecraft.getInstance().execute(() ->
                    Minecraft.getInstance().gui.setScreen(new MyScreen(GuiThemes.get())));
            }
        });
    }

    public static void resetSessionCheck() {
        hasCheckedThisSession = false;
    }

    @Override
    public void initWidgets() {
        buildUI();
    }

    private void fetchLatestVersion() {
        MeteorExecutor.execute(() -> {
            String latest = fetchVersion();

            latestVersion = latest;
            fetchFailed = latest == null;
            isVersionFetched = true;

            // reload() rebuilds widgets, render thread only
            Minecraft.getInstance().execute(this::reload);
        });
    }

    private static String fetchVersion() {
        try {
            String versionString = Http.get(GlazedAddon.VERSION_CHECK_URL).sendString();
            if (versionString == null || versionString.isBlank()) return null;

            return versionString.trim();
        } catch (Exception e) {
            LOG.warn("Version check failed", e);
            return null;
        }
    }

    private static boolean isNewerThanInstalled(String latest) {
        return compareVersions(latest, GlazedAddon.VERSION) > 0;
    }

    // old code did Integer.parseInt("16.2") into an empty catch
    // so the update check has been dead this entire time and nobody knew
    static int compareVersions(String a, String b) {
        String[] left = a.split("\\.");
        String[] right = b.split("\\.");

        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            int l = segment(left, i);
            int r = segment(right, i);
            if (l != r) return Integer.compare(l, r);
        }

        return 0;
    }

    private static int segment(String[] parts, int index) {
        if (index >= parts.length) return 0;

        try {
            return Integer.parseInt(parts[index].replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void buildUI() {
        addWrappedMessage("Welcome to Glazed Client version checker. Here you can see your current version and check for updates.");

        add(theme.horizontalSeparator()).padVertical(theme.scale(4)).expandX();

        addMessage(String.format("Installed Version: %s", GlazedAddon.VERSION));

        if (!isVersionFetched) {
            addMessage("Latest Version: Checking...");
        } else if (fetchFailed) {
            addMessage("Latest Version: Unavailable");
        } else {
            addMessage(String.format("Latest Version: %s", latestVersion));
        }

        add(theme.horizontalSeparator()).padVertical(theme.scale(8)).expandX();

        if (!isVersionFetched) {
            addWrappedMessage("Checking for updates...");
        } else if (fetchFailed) {
            // dont say "youre up to date" when we never actually checked
            addWrappedMessage("Could not reach the update server, so your version could not be checked. Try again later.");
        } else if (isNewerThanInstalled(latestVersion)) {
            addWrappedMessage("You're using an outdated version of the Glazed addon. Please update to the latest version. Newer versions may include important bug fixes, improvements, and additional features.");
        } else {
            addWrappedMessage("You're using the latest version of the Glazed addon. No update needed.");
        }

        add(theme.horizontalSeparator()).padVertical(theme.scale(8)).expandX();

        WHorizontalList buttonsContainer = add(theme.horizontalList()).expandX().widget();

        WButton githubButton = buttonsContainer.add(theme.button("GitHub")).expandX().widget();
        githubButton.action = () -> Util.getPlatform().openUri("https://github.com/realnnpg/glazed");

        WButton websiteButton = buttonsContainer.add(theme.button("Website")).expandX().widget();
        websiteButton.action = () -> Util.getPlatform().openUri("https://glazedclient.com");
    }

    private void addMessage(String message) {
        WHorizontalList l = add(theme.horizontalList()).expandX().widget();
        l.add(theme.label(message)).expandX();
    }

    private void addWrappedMessage(String message) {
        StringBuilder currentLine = new StringBuilder();

        for (String word : message.split(" ")) {
            if (!currentLine.isEmpty() && currentLine.length() + word.length() + 1 > 60) {
                addMessage(currentLine.toString());
                currentLine.setLength(0);
            }

            if (!currentLine.isEmpty()) currentLine.append(' ');
            currentLine.append(word);
        }

        if (!currentLine.isEmpty()) addMessage(currentLine.toString());
    }
}
