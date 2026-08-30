package cgc.cgc.mixin;

import cgc.cgc.module.impl.dungeon.AutoC;
import cgc.cgc.module.impl.utils.TerminalTimes;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {
	@Inject(method = "startDestroyBlock", at = @At("RETURN"))
	private void cgc$onStartDestroyBlock(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			AutoC.recordBreakAttempt(pos);
		}
	}

	@Inject(method = "handleContainerInput", at = @At("HEAD"))
	private void cgc$onContainerInputStart(int containerId, int slot, int button, ContainerInput input, Player player, CallbackInfo ci) {
		TerminalTimes.observeContainerInputStart(containerId, slot, button, input);
	}

	@Inject(method = "handleContainerInput", at = @At("RETURN"))
	private void cgc$onContainerInputEnd(int containerId, int slot, int button, ContainerInput input, Player player, CallbackInfo ci) {
		TerminalTimes.observeContainerInputEnd();
	}
}
