package com.nnpg.glazed.mixin.protection;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.nnpg.glazed.protection.PacketContext;
import com.nnpg.glazed.protection.TranslationProtectionHandler;
import com.nnpg.glazed.protection.TranslationProtectionHandler.InterceptionType;
import com.nnpg.glazed.protection.ModRegistry;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Language;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mixin(value = TranslatableTextContent.class, priority = 1500)
public abstract class TranslatableTextContentMixin {

    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger("Glazed-Protection");

    @Shadow @Final private String key;
    @Shadow @Final private String fallback;

    @Unique
    private boolean glazed$fromPacket = false;

    @Inject(method = "<init>(Ljava/lang/String;Ljava/lang/String;[Ljava/lang/Object;)V", at = @At("TAIL"), require = 0)
    private void glazed$tagFromPacket(String key, String fallback, Object[] args, CallbackInfo ci) {
        try {
            this.glazed$fromPacket = PacketContext.isProcessingPacket();
            if (this.glazed$fromPacket) {
                LOGGER.info("[Glazed-Debug] TranslatableTextContent created from packet: {} | key='{}' fallback='{}'",
                    PacketContext.getPacketName(), key, fallback);
            }
        } catch (Throwable t) {

            this.glazed$fromPacket = false;
        }
    }

    @Unique
    private static final String GLAZED_ALLOW_ORIGINAL = "\0__glazed_allow__";

    @WrapOperation(
        method = {
            "decompose(Lnet/minecraft/text/LanguageVisitor;Lnet/minecraft/text/Style;)Ljava/util/Optional;",
            "updateTranslations()V"
        },
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/Language;get(Ljava/lang/String;)Ljava/lang/String;"),
        require = 0
    )
    private String glazed$wrapGetSingle(Language instance, String keyArg, Operation<String> original) {

        if (!this.glazed$fromPacket) {
            return original.call(instance, keyArg);
        }

        String result = glazed$handleTranslationLookup(keyArg, keyArg);
        if (result == GLAZED_ALLOW_ORIGINAL) {
            return original.call(instance, keyArg);
        }
        return result;
    }

    @WrapOperation(
        method = {
            "decompose(Lnet/minecraft/text/LanguageVisitor;Lnet/minecraft/text/Style;)Ljava/util/Optional;",
            "updateTranslations()V"
        },
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/Language;get(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"),
        require = 0
    )
    private String glazed$wrapGet(Language instance, String keyArg, String fallbackArg, Operation<String> original) {

        if (!this.glazed$fromPacket) {
            return original.call(instance, keyArg, fallbackArg);
        }

        String result = glazed$handleTranslationLookup(keyArg, fallbackArg);
        if (result == GLAZED_ALLOW_ORIGINAL) {
            return original.call(instance, keyArg, fallbackArg);
        }
        return result;
    }

    @Unique
    private String glazed$handleTranslationLookup(String translationKey, String defaultValue) {

        try {

            if (!this.glazed$fromPacket || glazed$isIntegratedServerRunning()) {
                return GLAZED_ALLOW_ORIGINAL;
            }
        } catch (Throwable t) {

            return GLAZED_ALLOW_ORIGINAL;
        }

        if (ModRegistry.isVanillaTranslationKey(translationKey)) {
            return GLAZED_ALLOW_ORIGINAL;
        }

        if (ModRegistry.isServerPackTranslationKey(translationKey)) {
            return GLAZED_ALLOW_ORIGINAL;
        }

        String blockedValue = defaultValue;
        glazed$logBlocked(translationKey, blockedValue);
        return blockedValue;
    }

    @Unique
    private static boolean glazed$isIntegratedServerRunning() {
        try {

            return net.minecraft.client.MinecraftClient.getInstance().isIntegratedServerRunning();
        } catch (Exception e) {
            return false;
        }
    }

    @Unique
    private void glazed$logBlocked(String translationKey, String defaultValue) {
        String originalValue = glazed$getRealTranslation(translationKey, defaultValue);

        TranslationProtectionHandler.logDetection(InterceptionType.TRANSLATION, translationKey, originalValue, defaultValue);
    }

    @Unique
    private String glazed$getRealTranslation(String translationKey, String defaultValue) {
        try {
            Language lang = Language.getInstance();
            if (lang instanceof TranslationStorageAccessor accessor) {
                Map<String, String> translations = accessor.glazed$getTranslations();
                String value = translations.get(translationKey);
                return value != null ? value : defaultValue;
            }
        } catch (Exception e) {

        }
        return defaultValue;
    }
}
