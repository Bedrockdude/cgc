package cgc.cgc.mixin;

import cgc.cgc.utils.TickFreeze;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DeltaTracker.Timer.class)
public class DeltaTrackerFreezeMixin {
	@Inject(method = "advanceGameTime", at = @At("HEAD"))
	private void cgc$storePartialTick(long timeMillis, CallbackInfoReturnable<Integer> cir) {
		TickFreeze.setPartialTick(((DeltaTracker.Timer) (Object) this).getGameTimeDeltaPartialTick(true));
	}

	@Inject(method = "getGameTimeDeltaPartialTick", at = @At("HEAD"), cancellable = true)
	private void cgc$freezePartialTick(boolean runsNormally, CallbackInfoReturnable<Float> cir) {
		if (TickFreeze.isFrozen()) {
			cir.setReturnValue(TickFreeze.getPartialTick());
		}
	}
}
