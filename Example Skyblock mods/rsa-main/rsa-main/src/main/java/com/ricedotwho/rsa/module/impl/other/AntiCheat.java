package com.ricedotwho.rsa.module.impl.other;

import com.ricedotwho.rsa.module.impl.other.checks.InvWalkCheck;
import com.ricedotwho.rsm.event.api.SubscribeEvent;
import com.ricedotwho.rsm.event.impl.game.ChatEvent.Chat;
import com.ricedotwho.rsm.event.impl.render.Render3DEvent.Extract;
import com.ricedotwho.rsm.module.Module;
import com.ricedotwho.rsm.module.api.Category;
import com.ricedotwho.rsm.module.api.ModuleInfo;
import com.ricedotwho.rsm.ui.clickgui.settings.Setting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.BooleanSetting;
import java.util.regex.Pattern;

@ModuleInfo(aliases = "AntiCheat", id = "AntiCheat", category = Category.OTHER)
public class AntiCheat extends Module {
   public static final BooleanSetting termWalking = new BooleanSetting("Terminal Walking", false, () -> true);
   private static final Pattern playerName = Pattern.compile("^(\\w+)\\s+activated a terminal");

   public AntiCheat() {
      this.registerProperty(new Setting[]{termWalking});
   }

   public void onEnable() {
   }

   public void onDisable() {
   }

   public void reset() {
   }

   @SubscribeEvent
   public void InvWalk(Extract event) {
      if ((Boolean)termWalking.getValue()) {
         InvWalkCheck.setRunning();
         InvWalkCheck.Check1();
      }
   }

   @SubscribeEvent
   public void InvWalk2(Chat event) {
      if ((Boolean)termWalking.getValue()) {
         InvWalkCheck.terminalCompletedMsg(event);
      }
   }
}
