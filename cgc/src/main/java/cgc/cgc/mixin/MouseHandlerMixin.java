package cgc.cgc.mixin;

import cgc.cgc.runtime.PhysicalInputTracker;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
	@Inject(method = "onButton", at = @At("HEAD"))
	private void cgc$observePhysicalButton(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
		PhysicalInputTracker.onMouseButton(info.input(), action);
	}

	@Inject(method = "onMove", at = @At("HEAD"))
	private void cgc$observePhysicalMove(long window, double x, double y, CallbackInfo ci) {
		PhysicalInputTracker.onMouseMove(x, y);
	}

	@Inject(method = "onScroll", at = @At("HEAD"))
	private void cgc$observePhysicalScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
		PhysicalInputTracker.onScroll(xOffset, yOffset);
	}
}
