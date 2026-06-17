package com.nnpg.glazed.protection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ModRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger("Glazed-Protection");

    private static final Set<String> vanillaTranslationKeys = ConcurrentHashMap.newKeySet();

    private static final Set<String> vanillaKeybinds = ConcurrentHashMap.newKeySet();

    private static final Set<String> serverPackKeys = ConcurrentHashMap.newKeySet();

    private static final Set<String> allKnownTranslationKeys = ConcurrentHashMap.newKeySet();

    private static final Map<String, String> translationKeyToModId = new ConcurrentHashMap<>();

    private static final Map<String, ModInfo> modRegistry = new ConcurrentHashMap<>();

    private static volatile boolean initialized = false;

    private ModRegistry() {}

    public static class ModInfo {
        private final String modId;
        private final Set<String> translationKeys = ConcurrentHashMap.newKeySet();
        private final Set<String> keybinds = ConcurrentHashMap.newKeySet();
        private boolean whitelisted = false;

        public ModInfo(String modId) {
            this.modId = modId;
        }

        public String getModId() {
            return modId;
        }

        public Set<String> getTranslationKeys() {
            return translationKeys;
        }

        public Set<String> getKeybinds() {
            return keybinds;
        }

        public boolean isWhitelisted() {
            return whitelisted;
        }

        public void setWhitelisted(boolean whitelisted) {
            this.whitelisted = whitelisted;
        }

        public void addTranslationKey(String key) {
            translationKeys.add(key);
        }

        public void addKeybind(String keybind) {
            keybinds.add(keybind);
        }

        public boolean hasTranslationKeys() {
            return !translationKeys.isEmpty();
        }

        public boolean hasKeybinds() {
            return !keybinds.isEmpty();
        }
    }

    public static void recordTranslationKey(String modId, String key) {
        if (modId == null || key == null) return;
        allKnownTranslationKeys.add(key);
        translationKeyToModId.put(key, modId);

        ModInfo info = modRegistry.computeIfAbsent(modId, ModInfo::new);
        info.addTranslationKey(key);
    }

    public static void recordVanillaTranslationKey(String key) {
        if (key == null) return;
        vanillaTranslationKeys.add(key);
        allKnownTranslationKeys.add(key);
    }

    public static void recordServerPackKey(String key) {
        if (key == null) return;
        serverPackKeys.add(key);
        allKnownTranslationKeys.add(key);
    }

    public static boolean isVanillaTranslationKey(String key) {
        return key != null && vanillaTranslationKeys.contains(key);
    }

    public static boolean isServerPackTranslationKey(String key) {
        return key != null && serverPackKeys.contains(key);
    }

    public static String getModForTranslationKey(String key) {
        if (key == null) return null;
        return translationKeyToModId.get(key);
    }

    public static boolean isWhitelistedTranslationKey(String key) {
        if (key == null) return false;
        String modId = translationKeyToModId.get(key);
        if (modId == null) return false;
        ModInfo info = modRegistry.get(modId);
        return info != null && info.isWhitelisted();
    }

    public static void clearTranslationKeys() {
        vanillaTranslationKeys.clear();
        serverPackKeys.clear();
        allKnownTranslationKeys.clear();
        translationKeyToModId.clear();
        LOGGER.info("[ModRegistry] Cleared translation key cache");
    }

    public static void clearServerPackKeys() {
        serverPackKeys.clear();
        LOGGER.info("[ModRegistry] Cleared server pack keys");
    }

    public static void recordVanillaKeybind(String keybindName) {
        if (keybindName == null) return;
        vanillaKeybinds.add(keybindName);
    }

    public static void recordModKeybind(String modId, String keybindName) {
        if (modId == null || keybindName == null) return;
        ModInfo info = modRegistry.computeIfAbsent(modId, ModInfo::new);
        info.addKeybind(keybindName);
    }

    public static boolean isVanillaKeybind(String keybindName) {
        return keybindName != null && vanillaKeybinds.contains(keybindName);
    }

    public static boolean isWhitelistedKeybind(String keybindName) {
        if (keybindName == null) return false;

        for (ModInfo info : modRegistry.values()) {
            if (info.getKeybinds().contains(keybindName)) {
                return info.isWhitelisted();
            }
        }
        return false;
    }

    public static ModInfo getModInfo(String modId) {
        return modRegistry.get(modId);
    }

    public static Set<String> getAllModIds() {
        return modRegistry.keySet();
    }

    public static void setModWhitelisted(String modId, boolean whitelisted) {
        ModInfo info = modRegistry.get(modId);
        if (info != null) {
            info.setWhitelisted(whitelisted);
            LOGGER.info("[ModRegistry] Mod '{}' whitelist status: {}", modId, whitelisted);
        }
    }

    public static void markInitialized() {
        initialized = true;
        LOGGER.info("[ModRegistry] Initialized with {} translation keys",
            allKnownTranslationKeys.size());
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static int getVanillaKeyCount() {
        return vanillaTranslationKeys.size();
    }

    public static int getServerPackKeyCount() {
        return serverPackKeys.size();
    }

    public static int getTranslationKeyCount() {
        return allKnownTranslationKeys.size();
    }

    public static int getModCount() {
        return modRegistry.size();
    }

    public static int getWhitelistedModCount() {
        return (int) modRegistry.values().stream()
            .filter(ModInfo::isWhitelisted)
            .count();
    }
}
