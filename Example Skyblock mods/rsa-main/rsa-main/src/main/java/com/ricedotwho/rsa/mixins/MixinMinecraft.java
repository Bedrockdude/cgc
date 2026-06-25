package com.ricedotwho.rsa.mixins;

import com.ricedotwho.rsa.component.impl.TickFreeze;
import com.ricedotwho.rsa.component.impl.managers.PacketOrderManager;
import com.ricedotwho.rsa.component.impl.managers.SwapManager;
import com.ricedotwho.rsa.event.impl.RawTickEvent;
import com.ricedotwho.rsa.screen.SessionLoginScreen;
import net.minecraft.util.profiler.Profilers;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;
import net.minecraft.client.gui.screen.Overlay;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MinecraftClient.class, priority = 600)
public abstract class MixinMinecraft {
   @Shadow
   @Nullable
   private Overlay overlay;
   @Shadow
   @Nullable
   public Screen currentScreen;
   @Unique
   private boolean bla = false;
   @Unique
   private boolean blu = false;

   @Shadow
   protected abstract void handleInputEvents();

   @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
   private void onTickStart(CallbackInfo ci) {
      new RawTickEvent().post();
      if (TickFreeze.isFrozen()) {
         ci.cancel();
      } else {
         SwapManager.onPreTickStart();
         PacketOrderManager.onPreTickStart();
      }
   }

   @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;handleInputEvents()V"))
   public void onHandleKeyBinds(MinecraftClient instance) {
   }

   @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/DebugHud;shouldShowDebugHud()Z"))
   public void onGetShowDebugScreen(CallbackInfo ci) {
      if (this.overlay == null && MinecraftClient.getInstance().player != null) {
         Profilers.get().swap("Keybindings");
         this.handleInputEvents();
      }
   }

   @Inject(method = "handleInputEvents", at = @At("HEAD"))
   public void onHandleKeybinds(CallbackInfo ci) {
      this.bla = true;
      this.blu = true;
   }

   @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/KeyBinding;wasPressed()Z", ordinal = 14), method = "handleInputEvents")
   public void onHandleInputEvent(CallbackInfo ci) {
      if (this.bla) {
         PacketOrderManager.execute(PacketOrderManager.STATE.ATTACK);
         this.bla = false;
      }
   }

   @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/KeyBinding;wasPressed()Z", ordinal = 15), method = "handleInputEvents")
   public void onHandleInputEvent2(CallbackInfo ci) {
      if (this.blu) {
         PacketOrderManager.execute(PacketOrderManager.STATE.ITEM_USE);
         this.blu = false;
      }
   }

   @Inject(at = @At("RETURN"), method = "getSession", cancellable = true)
   private void onGetSSID(CallbackInfoReturnable<Session> cir) {
      if (SessionLoginScreen.getUser() != null) {
         cir.setReturnValue(SessionLoginScreen.getUser());
      }
   }
}
