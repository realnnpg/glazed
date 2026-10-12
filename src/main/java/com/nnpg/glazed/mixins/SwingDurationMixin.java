package com.nnpg.glazed.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.nnpg.glazed.modules.main.SwingSpeed;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public abstract class SwingDurationMixin {

    @ModifyReturnValue(method = "getCurrentSwingDuration", at = @At("RETURN"))
    private int glazed$modifySwingDuration(int original) {
        if (!((Object) this instanceof LocalPlayer)) return original;
        if (SwingSpeed.INSTANCE == null || !SwingSpeed.INSTANCE.isActive()) return original;

        int modified = (int) Math.round(original / SwingSpeed.INSTANCE.getMultiplier());
        return Math.max(1, modified);
    }
}