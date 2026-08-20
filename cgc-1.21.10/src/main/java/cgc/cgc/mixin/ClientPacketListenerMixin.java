package cgc.cgc.mixin;

import cgc.cgc.dungeon.DungeonState;
import cgc.cgc.location.Location;
import cgc.cgc.module.CgcModules;
import cgc.cgc.module.impl.dungeon.AutoTerms;
import cgc.cgc.module.impl.utils.TerminalTimes;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
	@Inject(method = "handleOpenScreen", at = @At("TAIL"))
	private void cgc$onOpenScreenHandlerTail(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
		TerminalTimes.observePacketHandlerTail(packet);
		AutoTerms.handleAppliedTerminalPacket(packet);
	}

	@Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
	private void cgc$onContainerSetSlotHandlerTail(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
		TerminalTimes.observePacketHandlerTail(packet);
		AutoTerms.handleAppliedTerminalPacket(packet);
	}

	@Inject(method = "handleContainerContent", at = @At("TAIL"))
	private void cgc$onContainerContentHandlerTail(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
		TerminalTimes.observePacketHandlerTail(packet);
		AutoTerms.handleAppliedTerminalPacket(packet);
	}

	@Inject(method = "handleContainerSetData", at = @At("TAIL"))
	private void cgc$onContainerSetDataHandlerTail(ClientboundContainerSetDataPacket packet, CallbackInfo ci) {
		TerminalTimes.observePacketHandlerTail(packet);
	}

	@Inject(method = "handleSetCursorItem", at = @At("TAIL"))
	private void cgc$onSetCursorItemHandlerTail(ClientboundSetCursorItemPacket packet, CallbackInfo ci) {
		TerminalTimes.observePacketHandlerTail(packet);
	}

	@Inject(method = "handleContainerClose", at = @At("TAIL"))
	private void cgc$onContainerCloseHandlerTail(ClientboundContainerClosePacket packet, CallbackInfo ci) {
		TerminalTimes.observePacketHandlerTail(packet);
		AutoTerms.handleAppliedTerminalPacket(packet);
	}

	@Inject(method = "handleSystemChat", at = @At("TAIL"))
	private void cgc$onSystemChatHandlerTail(ClientboundSystemChatPacket packet, CallbackInfo ci) {
		TerminalTimes.observePacketHandlerTail(packet);
	}

	@Inject(
		method = "handleBlockUpdate",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
			shift = At.Shift.AFTER
		)
	)
	private void cgc$onBlockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
		CgcModules.blockChange(packet.getPos(), packet.getBlockState());
	}

	@Inject(
		method = "handleChunkBlocksUpdate",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
			shift = At.Shift.AFTER
		)
	)
	private void cgc$onChunkBlocksUpdate(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
		packet.runUpdates((pos, state) -> CgcModules.blockChange(pos.immutable(), state));
	}

	@Inject(
		method = "handleSystemChat",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
			shift = At.Shift.AFTER
		)
	)
	private void cgc$onSystemChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
		if (packet.overlay()) {
			CgcModules.actionBarMessage(packet.content().getString());
		} else {
			CgcModules.chatMessage(packet.content().getString());
		}
	}

	@Inject(
		method = "setActionBarText",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
			shift = At.Shift.AFTER
		)
	)
	private void cgc$onSetActionBarText(ClientboundSetActionBarTextPacket packet, CallbackInfo ci) {
		CgcModules.actionBarMessage(packet.text().getString());
	}

	@Inject(method = "handleAddObjective", at = @At("HEAD"))
	private void cgc$onObjective(ClientboundSetObjectivePacket packet, CallbackInfo ci) {
		Location.handleObjective(packet);
	}

	@Inject(method = "handleSetScore", at = @At("HEAD"))
	private void cgc$onSetScore(ClientboundSetScorePacket packet, CallbackInfo ci) {
		Location.handleSetScore(packet);
	}

	@Inject(method = "handleSetPlayerTeamPacket", at = @At("HEAD"))
	private void cgc$onSetPlayerTeam(ClientboundSetPlayerTeamPacket packet, CallbackInfo ci) {
		Location.handleSetPlayerTeam(packet);
	}

	@Inject(method = "handlePlayerInfoUpdate", at = @At("HEAD"))
	private void cgc$onPlayerInfoUpdate(ClientboundPlayerInfoUpdatePacket packet, CallbackInfo ci) {
		Location.handlePlayerInfo(packet);
		DungeonState.handlePlayerInfo(packet);
	}
}
