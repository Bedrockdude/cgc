package cgc.cgc.mixin;

import cgc.cgc.utils.TickFreeze;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftTickFreezeMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void cgc$freezeTick(CallbackInfo ci) {
		if (TickFreeze.isFrozen()) {
			ci.cancel();
		}
	}
}
