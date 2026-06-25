package com.ricedotwho.rsa.mixins;

import com.ricedotwho.rsa.module.impl.dungeon.SecretHitboxes;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.block.BlockState;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(WorldRenderer.class)
public class MixinLevelRenderer {
   @Shadow
   private ClientWorld world;
   @Shadow
   @Final
   private MinecraftClient client;

   @ModifyVariable(method = "fillEntityOutlineRenderStates", at = @At(value = "STORE", ordinal = 0), ordinal = 0)
   private VoxelShape extractBlockOutline(VoxelShape original) {
      if (this.world == null || !(this.client.crosshairTarget instanceof BlockHitResult hit)) {
         return original;
      } else {
         BlockPos blockPos = hit.getBlockPos();
         BlockState state = this.world.getBlockState(blockPos);
         VoxelShape shape = SecretHitboxes.getShape(state, blockPos);
         return shape != null ? shape : original;
      }
   }
}
