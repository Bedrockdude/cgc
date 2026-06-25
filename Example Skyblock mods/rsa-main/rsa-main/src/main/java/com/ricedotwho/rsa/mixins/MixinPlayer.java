package com.ricedotwho.rsa.mixins;

import com.ricedotwho.rsa.module.impl.dungeon.DungeonBreaker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.block.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class MixinPlayer {
   @Shadow
   public abstract PlayerInventory getInventory();

   @Inject(method = "getBlockBreakingSpeed", at = @At("RETURN"), cancellable = true)
   private void modifyBreakSpeed(BlockState state, CallbackInfoReturnable<Float> cir) {
      DungeonBreaker.handleDigSpeed(state, this.getInventory().getSelectedStack(), cir);
   }
}
