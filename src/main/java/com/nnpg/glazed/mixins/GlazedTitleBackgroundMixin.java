package com.nnpg.glazed.mixins;

import com.nnpg.glazed.modules.main.BackgroundSwitcher;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class GlazedTitleBackgroundMixin {
    @Shadow public int width;
    @Shadow public int height;

    @Inject(method = "extractPanorama", at = @At("HEAD"), cancellable = true)
    private void glazed$customBackground(GuiGraphicsExtractor graphics, float partialTick, CallbackInfo ci) {
        if (BackgroundSwitcher.draw(graphics, this.width, this.height)) {
            ci.cancel();
        }
    }
}