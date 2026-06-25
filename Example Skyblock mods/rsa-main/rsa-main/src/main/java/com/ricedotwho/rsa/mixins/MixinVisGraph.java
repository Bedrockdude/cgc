package com.ricedotwho.rsa.mixins;

import com.ricedotwho.rsa.module.impl.render.Freecam;
import net.minecraft.util.math.BlockPos;
import net.minecraft.client.render.chunk.ChunkOcclusionDataBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkOcclusionDataBuilder.class)
public class MixinVisGraph {
   @Inject(at = @At("HEAD"), method = "markClosed", cancellable = true)
   private void onMarkClosed(BlockPos blockPos, CallbackInfo ci) {
      if (Freecam.isDetached()) {
         ci.cancel();
      }
   }
}
