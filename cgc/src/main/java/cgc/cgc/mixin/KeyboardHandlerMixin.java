package cgc.cgc.mixin;

import cgc.cgc.runtime.PhysicalInputTracker;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
	@Inject(method = "keyPress", at = @At("HEAD"))
	private void cgc$observePhysicalKey(long window, int action, KeyEvent event, CallbackInfo ci) {
		PhysicalInputTracker.onKey(event.input(), action);
	}
}
