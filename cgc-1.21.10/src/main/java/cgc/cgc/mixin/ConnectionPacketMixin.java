package cgc.cgc.mixin;

import cgc.cgc.module.CgcModules;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class ConnectionPacketMixin {
	@Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
	private void cgc$onPacketReceive(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
		if (CgcModules.packetReceive(packet)) {
			ci.cancel();
		}
	}

	@Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V", at = @At("TAIL"))
	private void cgc$onPacketPostReceive(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
		CgcModules.packetPostReceive(packet);
	}

	@Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At("HEAD"), cancellable = true)
	private void cgc$onPacketSend(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
		if (CgcModules.packetSend(packet)) {
			ci.cancel();
		}
	}
}
