package com.ricedotwho.rsa.mixins;

import com.ricedotwho.rsa.IMixin.IMultiPlayerGameMode;
import com.ricedotwho.rsa.component.impl.managers.SwapManager;
import com.ricedotwho.rsa.module.impl.player.CancelInteract;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.network.SequencedPacketCreator;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class MixinMultiPlayerGameMode implements IMultiPlayerGameMode {
   @Shadow
   private int lastSelectedSlot;

   @Shadow
   protected abstract void syncSelectedSlot();

   @Shadow
   protected abstract void sendSequencedPacket(ClientWorld world, SequencedPacketCreator packetCreator);

   @Override
   public void sendPacketSequenced(ClientWorld world, SequencedPacketCreator packetCreator) {
      this.sendSequencedPacket(world, packetCreator);
   }

   @Override
   public void syncSlot() {
      this.syncSelectedSlot();
   }

   @Inject(method = "syncSelectedSlot", at = @At("HEAD"), cancellable = true)
   public void onSyncSlot(CallbackInfo ci) {
      if (!SwapManager.onEnsureHasSentCarriedItem(this.lastSelectedSlot)) {
         ci.cancel();
      }
   }

   @Inject(method = "interactBlock", at = @At("HEAD"), cancellable = true)
   private void onInteractBlock(ClientPlayerEntity player, Hand hand, BlockHitResult hit, CallbackInfoReturnable<ActionResult> cir) {
      if (CancelInteract.shouldCancelInteract(hit, player, player.getEquippedStack(hand.getEquipmentSlot()))) {
         cir.setReturnValue(ActionResult.PASS);
      }
   }
}
