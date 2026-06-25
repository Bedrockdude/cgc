package com.ricedotwho.rsa.mixins;

import com.ricedotwho.rsa.screen.SessionLoginScreen;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class MixinTitleScreen extends Screen {
   protected MixinTitleScreen(Text component) {
      super(component);
   }

   @Inject(at = @At("HEAD"), method = "init")
   private void onInit(CallbackInfo ci) {
      ButtonWidget theButton = ButtonWidget.builder(
            Text.literal("Session Login"), button -> MinecraftClient.getInstance().setScreen(SessionLoginScreen.getInstance())
         )
         .width(100)
         .position(this.width - 110, 20)
         .build();
      this.addDrawableChild(theButton);
   }
}
