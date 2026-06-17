package com.nnpg.glazed.protection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TranslationProtectionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("Glazed-Protection");

    public enum InterceptionType {
        TRANSLATION("Translation"),
        KEYBIND("Keybind");

        private final String displayName;
        InterceptionType(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }

    private record AlertDedupeKey(InterceptionType type, String keyName) {}
    private record LogDedupeKey(InterceptionType type, String packetName, String keyName, String originalValue, String spoofedValue) {}

    private static final Set<AlertDedupeKey> alertedKeys = ConcurrentHashMap.newKeySet();
    private static final Set<LogDedupeKey> loggedKeys = ConcurrentHashMap.newKeySet();

    private static final int MAX_DEDUPE_ENTRIES = 500;

    private TranslationProtectionHandler() {}

    public static void notifyExploitDetected() {

    }

    public static void sendDetail(InterceptionType type, String keyName, String originalValue, String spoofedValue) {

    }

    public static void sendDetailDebug(InterceptionType type, String keyName, String originalValue, String spoofedValue) {

    }

    public static void logDetection(InterceptionType type, String keyName, String originalValue, String spoofedValue) {
        String packetName = PacketContext.getPacketName();

        if (loggedKeys.size() >= MAX_DEDUPE_ENTRIES) {
            loggedKeys.clear();
        }

        if (!loggedKeys.add(new LogDedupeKey(type, packetName, keyName, originalValue, spoofedValue))) {
            return;
        }

        LOGGER.info("[{}:{}] '{}' '{}' -> '{}'",
            type.getDisplayName(), packetName, keyName, originalValue, spoofedValue);
    }

    public static void clearCache() {
        alertedKeys.clear();
        loggedKeys.clear();
    }
}
