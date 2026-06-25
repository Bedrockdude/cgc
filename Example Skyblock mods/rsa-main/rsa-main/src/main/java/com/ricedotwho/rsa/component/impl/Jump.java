package com.ricedotwho.rsa.component.impl;

import com.ricedotwho.rsm.component.api.ModComponent;
import com.ricedotwho.rsm.event.api.SubscribeEvent;
import com.ricedotwho.rsm.event.impl.client.InputPollEvent;

public class Jump extends ModComponent {
   private static boolean jump = false;

   public Jump() {
      super("Jump");
   }

   public static void jump() {
      jump = true;
   }

   @SubscribeEvent
   public void onInput(InputPollEvent event) {
      if (jump && mc.player != null && mc.player.isOnGround() && !mc.options.jumpKey.isPressed()) {
         jump = false;
         event.getInput().jump(true);
      }
   }

   public static boolean isJump() {
      return jump;
   }
}
