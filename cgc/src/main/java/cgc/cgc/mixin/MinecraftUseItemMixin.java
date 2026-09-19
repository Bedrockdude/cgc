package cgc.cgc.mixin;

import cgc.cgc.runtime.PacketOrderManager;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftUseItemMixin {
	@Inject(method = "startUseItem", at = @At("HEAD"))
	private void cgc$beginVanillaUse(CallbackInfo ci) {
		PacketOrderManager.INSTANCE.beginVanillaUse();
	}

	@Inject(method = "startUseItem", at = @At("RETURN"))
	private void cgc$endVanillaUse(CallbackInfo ci) {
		PacketOrderManager.INSTANCE.endVanillaUse();
	}
}
