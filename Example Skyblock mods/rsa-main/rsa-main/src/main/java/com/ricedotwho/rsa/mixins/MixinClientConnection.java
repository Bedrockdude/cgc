package com.ricedotwho.rsa.mixins;

import com.ricedotwho.rsa.component.impl.managers.SwapManager;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.handler.PacketSizeLogger;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientConnection.class, priority = 600)
public abstract class MixinClientConnection {
   @Shadow
   @Nullable
   PacketSizeLogger packetSizeLogger;

   @Inject(method = "send", at = @At("HEAD"), cancellable = true)
   private void onSend(Packet<?> packet, CallbackInfo ci) {
      if (!SwapManager.onPostSendPacket(packet)) {
         ci.cancel();
      }
   }
}
