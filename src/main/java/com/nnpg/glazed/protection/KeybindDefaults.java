package com.nnpg.glazed.protection;

import java.util.HashMap;
import java.util.Map;

public class KeybindDefaults {
    private static final Map<String, String> DEFAULTS = new HashMap<>();

    static {

        DEFAULTS.put("key.forward", "W");
        DEFAULTS.put("key.left", "A");
        DEFAULTS.put("key.back", "S");
        DEFAULTS.put("key.right", "D");
        DEFAULTS.put("key.jump", "Space");
        DEFAULTS.put("key.sneak", "Left Shift");
        DEFAULTS.put("key.sprint", "Left Control");

        DEFAULTS.put("key.attack", "Left Button");
        DEFAULTS.put("key.use", "Right Button");
        DEFAULTS.put("key.pickItem", "Middle Button");
        DEFAULTS.put("key.drop", "Q");
        DEFAULTS.put("key.swapOffhand", "F");

        DEFAULTS.put("key.inventory", "E");
        DEFAULTS.put("key.hotbar.1", "1");
        DEFAULTS.put("key.hotbar.2", "2");
        DEFAULTS.put("key.hotbar.3", "3");
        DEFAULTS.put("key.hotbar.4", "4");
        DEFAULTS.put("key.hotbar.5", "5");
        DEFAULTS.put("key.hotbar.6", "6");
        DEFAULTS.put("key.hotbar.7", "7");
        DEFAULTS.put("key.hotbar.8", "8");
        DEFAULTS.put("key.hotbar.9", "9");

        DEFAULTS.put("key.chat", "T");
        DEFAULTS.put("key.playerlist", "Tab");
        DEFAULTS.put("key.command", "/");
        DEFAULTS.put("key.socialInteractions", "P");
        DEFAULTS.put("key.advancements", "L");
        DEFAULTS.put("key.screenshot", "F2");
        DEFAULTS.put("key.fullscreen", "F11");
        DEFAULTS.put("key.spectatorOutlines", "");

        DEFAULTS.put("key.saveToolbarActivator", "C");
        DEFAULTS.put("key.loadToolbarActivator", "X");

        DEFAULTS.put("key.smoothCamera", "");
    }

    public static boolean hasDefault(String keybindName) {
        return keybindName != null && DEFAULTS.containsKey(keybindName);
    }

    public static String getDefault(String keybindName) {
        return keybindName != null ? DEFAULTS.getOrDefault(keybindName, keybindName) : null;
    }

    private KeybindDefaults() {}
}
