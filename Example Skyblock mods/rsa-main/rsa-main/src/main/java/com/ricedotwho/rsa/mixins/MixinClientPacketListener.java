package com.ricedotwho.rsa.mixins;

import com.ricedotwho.rsa.component.impl.managers.SwapManager;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class MixinClientPacketListener {
   @Inject(
      method = "onGameJoin",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;<init>(Lnet/minecraft/client/MinecraftClient;Lnet/minecraft/client/network/ClientPlayNetworkHandler;)V"
      )
   )
   public void onHandleLogin(CallbackInfo ci) {
      SwapManager.onHandleLogin();
   }
}
