package com.nnpg.glazed.mixin.protection;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.nnpg.glazed.protection.ModRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.resource.language.TranslationStorage;
import net.minecraft.resource.DefaultResourcePack;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.VanillaDataPackProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

@Mixin(TranslationStorage.class)
public class TranslationStorageMixin {

    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger("Glazed-Protection");

    @Unique
    private static boolean glazed$loggedOnce = false;

    @Unique
    private static final ThreadLocal<ResourcePack> CURRENT_PACK = new ThreadLocal<>();

    @Inject(
        method = "load(Lnet/minecraft/resource/ResourceManager;Ljava/util/List;Z)Lnet/minecraft/client/resource/language/TranslationStorage;",
        at = @At("HEAD"),
        require = 0
    )
    private static void glazed$onLoadStart(
            ResourceManager resourceManager,
            List<String> definitions,
            boolean rightToLeft,
            CallbackInfoReturnable<TranslationStorage> cir) {
        try {
            ModRegistry.clearTranslationKeys();
            LOGGER.info("[Glazed Protection] Starting language load, clearing caches");
            glazed$loggedOnce = false;
        } catch (Throwable t) {
            LOGGER.error("[Glazed Protection] Error clearing translation keys", t);
        }
    }

    @Inject(
        method = "load(Lnet/minecraft/resource/ResourceManager;Ljava/util/List;Z)Lnet/minecraft/client/resource/language/TranslationStorage;",
        at = @At("RETURN"),
        require = 0
    )
    private static void glazed$onLoadComplete(
            ResourceManager resourceManager,
            List<String> definitions,
            boolean rightToLeft,
            CallbackInfoReturnable<TranslationStorage> cir) {
        try {
            ModRegistry.markInitialized();

            if (!glazed$loggedOnce) {
                glazed$loggedOnce = true;
                LOGGER.info("[Glazed Protection] Translation system initialized - {} vanilla keys, {} server pack keys, {} total keys tracked",
                    ModRegistry.getVanillaKeyCount(), ModRegistry.getServerPackKeyCount(), ModRegistry.getTranslationKeyCount());
            }
        } catch (Throwable t) {
            LOGGER.error("[Glazed Protection] Error in load complete", t);
        }
    }

    @Inject(
        method = "load(Lnet/minecraft/resource/ResourceManager;Ljava/util/List;Z)Lnet/minecraft/client/resource/language/TranslationStorage;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Language;load(Ljava/io/InputStream;Ljava/util/function/BiConsumer;)V"),
        require = 0
    )
    private static void glazed$capturePack(
            ResourceManager resourceManager, List<String> definitions, boolean rightToLeft, CallbackInfoReturnable<TranslationStorage> cir,
            @Local Resource resource) {
        try {
            CURRENT_PACK.set(glazed$getPackFromResource(resource));
        } catch (Throwable t) {
            CURRENT_PACK.set(null);
        }
    }

    @Unique
    private static ResourcePack glazed$getPackFromResource(Resource resource) {
        if (resource == null) return null;
        try {

            var method = resource.getClass().getMethod("getPack");
            return (ResourcePack) method.invoke(resource);
        } catch (Throwable t) {
            try {

                var method = resource.getClass().getMethod("pack");
                return (ResourcePack) method.invoke(resource);
            } catch (Exception e) {
                return null;
            }
        }
    }

    @Unique
    private static String glazed$getPackId(ResourcePack pack) {
        if (pack == null) return "unknown";
        try {
            var method = pack.getClass().getMethod("getId");
            return (String) method.invoke(pack);
        } catch (Throwable t) {
            try {
                var method = pack.getClass().getMethod("getName");
                return (String) method.invoke(pack);
            } catch (Exception e) {
                return pack.getClass().getSimpleName();
            }
        }
    }

    @Unique
    private static String glazed$getModIdFromPack(ResourcePack pack) {
        if (pack == null) return null;
        try {
            var method = pack.getClass().getMethod("getFabricModMetadata");
            var metadata = method.invoke(pack);
            if (metadata != null) {
                var getIdMethod = metadata.getClass().getMethod("getId");
                String id = (String) getIdMethod.invoke(metadata);
                if (id != null) return id;
            }
        } catch (Exception e) {}
        try {
            var method = pack.getClass().getMethod("getModMetadata");
            var metadata = method.invoke(pack);
            if (metadata != null) {
                var getIdMethod = metadata.getClass().getMethod("getId");
                String id = (String) getIdMethod.invoke(metadata);
                if (id != null) return id;
            }
        } catch (Exception e) {}

        String packId = glazed$getPackId(pack);
        if (packId != null && !packId.equals("vanilla") && !packId.startsWith("file/") && !packId.startsWith("server/")) {
            if (FabricLoader.getInstance().getModContainer(packId).isPresent()) {
                return packId;
            }

            String extractedModId = packId.replace("fabric/", "").replace("mod/", "");
            if (!extractedModId.isEmpty() && FabricLoader.getInstance().getModContainer(extractedModId).isPresent()) {
                return extractedModId;
            }
        }
        return null;
    }

    @WrapOperation(
        method = "load(Lnet/minecraft/resource/ResourceManager;Ljava/util/List;Z)Lnet/minecraft/client/resource/language/TranslationStorage;",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/Language;load(Ljava/io/InputStream;Ljava/util/function/BiConsumer;)V"),
        require = 0
    )
    private static void glazed$wrapLanguageLoad(InputStream stream, BiConsumer<String, String> consumer, Operation<Void> original) {
        original.call(stream, (BiConsumer<String, String>) (key, value) -> {
            glazed$trackKeyBySource(key, value);
            consumer.accept(key, value);
        });
    }

    @Unique
    private static void glazed$trackKeyBySource(String key, String value) {
        ResourcePack pack = CURRENT_PACK.get();

        try {
            if (pack != null) {
                String packId = glazed$getPackId(pack);

                boolean isVanillaPack = (pack instanceof DefaultResourcePack) || "vanilla".equals(packId);

                boolean isServerPack = packId != null && (packId.equals("server") || packId.startsWith("server/"));

                if (isVanillaPack) {
                    ModRegistry.recordVanillaTranslationKey(key);
                } else if (isServerPack) {
                    ModRegistry.recordServerPackKey(key);
                } else {
                    String modId = glazed$getModIdFromPack(pack);
                    if (modId != null) {
                        ModRegistry.recordTranslationKey(modId, key);
                    } else {

                        String extracted = glazed$extractModId(key);
                        if (extracted != null) {
                            ModRegistry.recordTranslationKey(extracted, key);
                        }
                    }
                }
            }
        } catch (Throwable t) {

            LOGGER.info("[Glazed Protection] Error tracking key '{}': {}", key, t.getMessage());
        }
    }

    @Unique
    private static boolean glazed$isVanillaKey(String key) {
        if (key == null) return false;

        return key.startsWith("key.") && !key.contains("-") && !key.contains("_")
            || key.startsWith("gui.") && !key.contains("-")
            || key.startsWith("menu.")
            || key.startsWith("options.")
            || key.startsWith("chat.")
            || key.startsWith("commands.")
            || key.startsWith("block.minecraft.")
            || key.startsWith("item.minecraft.")
            || key.startsWith("entity.minecraft.")
            || key.startsWith("biome.minecraft.")
            || key.startsWith("enchantment.minecraft.")
            || key.startsWith("effect.minecraft.")
            || key.startsWith("container.")
            || key.startsWith("death.")
            || key.startsWith("gameMode.")
            || key.startsWith("selectWorld.")
            || key.startsWith("createWorld.")
            || key.startsWith("multiplayer.")
            || key.startsWith("lanServer.")
            || key.startsWith("advMode.")
            || key.startsWith("narrator.")
            || key.startsWith("subtitles.")
            || key.startsWith("language.")
            || key.startsWith("resourcePack.")
            || key.startsWith("dataPack.")
            || key.startsWith("tutorial.")
            || key.startsWith("demo.")
            || key.startsWith("disconnect.")
            || key.startsWith("book.")
            || key.startsWith("sign.")
            || key.startsWith("filled_map.")
            || key.startsWith("structure_block.")
            || key.startsWith("jigsaw_block.")
            || key.startsWith("argument.")
            || key.startsWith("parsing.")
            || key.startsWith("color.minecraft.")
            || key.startsWith("stat.")
            || key.startsWith("controls.")
            || key.startsWith("attribute.name.")
            || key.startsWith("attribute.modifier.")
            || key.startsWith("gamerule.")
            || key.startsWith("difficulty.")
            || key.startsWith("potion.")
            || key.startsWith("recipe.")
            || key.startsWith("advancements.")
            || key.startsWith("translation.")
            || key.startsWith("pack.source.")
            || key.startsWith("pack.nameAndSource")
            || key.startsWith("soundCategory.")
            || key.startsWith("title.")
            || key.startsWith("screenshot.")
            || key.startsWith("mco.")
            || key.startsWith("realms.")
            || key.startsWith("telemetry.")
            || key.startsWith("accessibility.")
            || key.startsWith("editGamerule.")
            || key.startsWith("spectatorMenu.")
            || key.startsWith("record.")
            || key.startsWith("instrument.")
            || key.startsWith("painting.")
            || key.startsWith("trim_");
    }

    @Unique
    private static String glazed$extractModId(String key) {
        if (key == null || !key.contains(".")) return null;

        String[] parts = key.split("\\.");
        if (parts.length < 2) return null;

        String candidate = parts[1];
        if (candidate.contains("-") || candidate.contains("_")) {
            return candidate;
        }

        candidate = parts[0];
        if (candidate.contains("-") || candidate.contains("_")) {
            return candidate;
        }

        if (!glazed$isVanillaCategory(parts[0])) {
            return parts[1];
        }

        return null;
    }

    @Unique
    private static boolean glazed$isVanillaCategory(String category) {
        return category.equals("key") || category.equals("gui") || category.equals("menu")
            || category.equals("options") || category.equals("chat") || category.equals("commands")
            || category.equals("block") || category.equals("item") || category.equals("entity")
            || category.equals("biome") || category.equals("enchantment") || category.equals("effect")
            || category.equals("container") || category.equals("death") || category.equals("gameMode")
            || category.equals("selectWorld") || category.equals("createWorld") || category.equals("multiplayer")
            || category.equals("lanServer") || category.equals("advMode") || category.equals("narrator")
            || category.equals("subtitles") || category.equals("language") || category.equals("resourcePack")
            || category.equals("dataPack") || category.equals("tutorial") || category.equals("demo")
            || category.equals("disconnect") || category.equals("book") || category.equals("sign")
            || category.equals("filled_map") || category.equals("structure_block") || category.equals("jigsaw_block")
            || category.equals("argument") || category.equals("parsing") || category.equals("color")
            || category.equals("stat") || category.equals("controls") || category.equals("attribute")
            || category.equals("gamerule") || category.equals("difficulty") || category.equals("potion")
            || category.equals("recipe") || category.equals("advancements") || category.equals("translation")
            || category.equals("pack") || category.equals("soundCategory") || category.equals("title")
            || category.equals("screenshot") || category.equals("mco") || category.equals("realms")
            || category.equals("telemetry") || category.equals("accessibility") || category.equals("editGamerule")
            || category.equals("spectatorMenu") || category.equals("record") || category.equals("instrument")
            || category.equals("painting") || category.equals("trim");
    }
}
