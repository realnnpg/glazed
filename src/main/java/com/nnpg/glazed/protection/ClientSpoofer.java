package com.nnpg.glazed.protection;

import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public class ClientSpoofer {
    private static final Logger LOGGER = LoggerFactory.getLogger("Glazed-Protection");

    private static final String MINECRAFT_NAMESPACE = "minecraft";

    private static final Set<String> ALLOWED_CHANNELS = Set.of(
        "minecraft:brand",
        "minecraft:client_information",
        "minecraft:register",
        "minecraft:unregister"
    );

    private ClientSpoofer() {}

    public static boolean shouldBlockPayload(Identifier id) {
        if (id == null) return false;

        String channel = id.toString();
        String namespace = id.getNamespace();

        if (ALLOWED_CHANNELS.contains(channel)) {
            return false;
        }

        if (!MINECRAFT_NAMESPACE.equals(namespace)) {
            LOGGER.info("[Glazed Protection] Blocking mod channel: {}", channel);
            return true;
        }

        return false;
    }
}
