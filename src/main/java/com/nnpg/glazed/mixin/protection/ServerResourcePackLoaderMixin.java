package com.nnpg.glazed.mixin.protection;

import com.nnpg.glazed.protection.ModRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.resource.server.ServerResourcePackLoader;
import net.minecraft.resource.ResourcePackProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ServerResourcePackLoader.class)
public class ServerResourcePackLoaderMixin {

    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger("Glazed-Protection");

    @Inject(
        method = "loadServerPack(Lnet/minecraft/resource/ResourcePackProfile;Ljava/util/List;)V",
        at = @At("HEAD"),
        require = 0
    )
    private static void glazed$onServerPackLoad(
            ResourcePackProfile profile,
            List<ResourcePackProfile> profiles,
            CallbackInfo ci) {
        try {
            LOGGER.info("[Glazed Protection] Server resource pack loading: {}",
                profile != null ? profile.getId() : "unknown");
        } catch (Throwable t) {
            LOGGER.error("[Glazed Protection] Error in server pack load", t);
        }
    }

    @Inject(
        method = "loadServerPack(Lnet/minecraft/resource/ResourcePackProfile;Ljava/util/List;)V",
        at = @At("RETURN"),
        require = 0
    )
    private static void glazed$afterServerPackLoad(
            ResourcePackProfile profile,
            List<ResourcePackProfile> profiles,
            CallbackInfo ci) {
        try {
            LOGGER.info("[Glazed Protection] Server resource pack load complete");
        } catch (Throwable t) {
            LOGGER.error("[Glazed Protection] Error after server pack load", t);
        }
    }
}
