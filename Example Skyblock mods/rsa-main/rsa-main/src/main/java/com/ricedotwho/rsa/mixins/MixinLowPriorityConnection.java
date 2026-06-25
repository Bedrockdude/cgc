package com.ricedotwho.rsa.mixins;

import com.ricedotwho.rsa.IMixin.IConnection;
import com.ricedotwho.rsa.component.impl.managers.PacketOrderManager;
import com.ricedotwho.rsa.module.impl.dungeon.boss.Blink;
import com.ricedotwho.rsa.module.impl.movement.VelocityBuffer;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientConnection.class, priority = 400)
public abstract class MixinLowPriorityConnection implements IConnection {
   @Shadow
   protected abstract void sendImmediately(Packet<?> packet, @Nullable ChannelFutureListener listener, boolean flush);

   @Inject(
      method = "channelRead0",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/ClientConnection;handlePacket(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;)V"
      ),
      cancellable = true
   )
   private void channelRead0(ChannelHandlerContext channelHandlerContext, Packet<?> packet, CallbackInfo ci) {
      PacketOrderManager.onPreReceivePacket(packet);
      if (VelocityBuffer.onReceivePacketPre(packet)) {
         ci.cancel();
      }
   }

   @Inject(method = "sendImmediately", at = @At("HEAD"), cancellable = true)
   private void onSendPacket(Packet<?> packet, @Nullable ChannelFutureListener channelFutureListener, boolean flush, CallbackInfo ci) {
      if (Blink.onSendPacket(packet)) {
         ci.cancel();
      }
   }

   @Override
   public void sendPacketImmediately(Packet<?> packet) {
      this.sendImmediately(packet, null, true);
   }
}
