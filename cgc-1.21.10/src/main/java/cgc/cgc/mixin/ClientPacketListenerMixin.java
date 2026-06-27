package cgc.cgc.mixin;

import cgc.cgc.dungeon.DungeonState;
import cgc.cgc.location.Location;
import cgc.cgc.module.CgcModules;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
	@Inject(method = "handleBlockUpdate", at = @At("HEAD"))
	private void cgc$onBlockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
		CgcModules.blockChange(packet.getPos(), packet.getBlockState());
	}

	@Inject(method = "handleChunkBlocksUpdate", at = @At("HEAD"))
	private void cgc$onChunkBlocksUpdate(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
		packet.runUpdates((pos, state) -> CgcModules.blockChange(pos.immutable(), state));
	}

	@Inject(method = "handleSystemChat", at = @At("HEAD"))
	private void cgc$onSystemChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
		if (packet.overlay()) {
			CgcModules.actionBarMessage(packet.content().getString());
		} else {
			CgcModules.chatMessage(packet.content().getString());
		}
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
