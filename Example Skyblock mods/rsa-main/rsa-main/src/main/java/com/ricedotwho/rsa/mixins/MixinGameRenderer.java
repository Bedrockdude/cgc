package com.ricedotwho.rsa.mixins;

import com.ricedotwho.rsa.module.impl.render.Freecam;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {
   @Redirect(
      method = "renderWorld",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/Perspective;isFirstPerson()Z", ordinal = 0)
   )
   private boolean onRenderLevel(Perspective instance) {
      return instance.isFirstPerson() && !Freecam.isDetached();
   }
}
