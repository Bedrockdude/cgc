package cgc.cgc.mixin;

import cgc.cgc.module.CgcModules;
import cgc.cgc.module.impl.utils.TerminalTimes;
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
		TerminalTimes.observeDecodedPacket(packet);
		if (CgcModules.packetReceive(packet)) {
			TerminalTimes.observePacketReceiveCancelled(packet);
			ci.cancel();
		}
	}

	@Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V", at = @At("TAIL"))
	private void cgc$onPacketPostReceive(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
		CgcModules.packetPostReceive(packet);
	}

	@Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At("HEAD"), cancellable = true)
	private void cgc$onPacketSend(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
		TerminalTimes.observePacketSendRequest(packet);
		if (CgcModules.packetSend(packet)) {
			TerminalTimes.observePacketSendCancelled(packet);
			ci.cancel();
		}
	}

	@Inject(method = "doSendPacket", at = @At("HEAD"))
	private void cgc$onPacketWriteHandoff(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
		TerminalTimes.observePacketWriteHandoff(packet, flush);
	}

	@Inject(method = "doSendPacket", at = @At("RETURN"))
	private void cgc$onPacketWriteReturned(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
		TerminalTimes.observePacketWriteReturned(packet);
	}
}
