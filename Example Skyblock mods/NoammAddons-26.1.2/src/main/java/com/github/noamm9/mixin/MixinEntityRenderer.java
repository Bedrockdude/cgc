package com.github.noamm9.mixin;

import com.github.noamm9.features.impl.dungeon.TeammateESP;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public class MixinEntityRenderer {
    @Inject(method = "getNameTag", at = @At("HEAD"), cancellable = true)
    private void cancelNametag(Entity entity, CallbackInfoReturnable<Component> cir) {
        if (TeammateESP.shouldHideNametag(entity)) {
            cir.setReturnValue(null);
        }
    }
}