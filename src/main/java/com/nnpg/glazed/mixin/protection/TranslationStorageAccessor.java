package com.nnpg.glazed.mixin.protection;

import net.minecraft.client.resource.language.TranslationStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(TranslationStorage.class)
public interface TranslationStorageAccessor {
    @Accessor("translations")
    Map<String, String> glazed$getTranslations();
}
