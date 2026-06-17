package com.nnpg.glazed.mixin.protection;

import com.nnpg.glazed.protection.TranslationProtectionHandler;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.DisconnectionInfo;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonNetworkHandler.class)
public abstract class SessionProtectionMixin {

    @Inject(method = "onDisconnected", at = @At("HEAD"), require = 0)
    private void glazed$onDisconnect(DisconnectionInfo info, CallbackInfo ci) {
        TranslationProtectionHandler.clearCache();
    }

    @Mixin(ClientPlayNetworkHandler.class)
    public static abstract class JoinMixin {
        @Inject(method = "onGameJoin", at = @At("HEAD"), require = 0)
        private void glazed$onJoin(GameJoinS2CPacket packet, CallbackInfo ci) {
            TranslationProtectionHandler.clearCache();
        }
    }
}
