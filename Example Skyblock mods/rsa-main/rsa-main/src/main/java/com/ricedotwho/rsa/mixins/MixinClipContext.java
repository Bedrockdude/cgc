package com.ricedotwho.rsa.mixins;

import com.ricedotwho.rsa.module.impl.dungeon.SecretHitboxes;
import net.minecraft.world.BlockView;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.block.BlockState;
import net.minecraft.world.RaycastContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RaycastContext.class)
public class MixinClipContext {
   @Inject(method = "getBlockShape", at = @At("HEAD"), cancellable = true)
   private void getBlockShape(BlockState blockState, BlockView blockGetter, BlockPos blockPos, CallbackInfoReturnable<VoxelShape> cir) {
      VoxelShape shape = SecretHitboxes.getShape(blockState, blockPos);
      if (shape != null) {
         cir.setReturnValue(shape);
      }
   }
}
