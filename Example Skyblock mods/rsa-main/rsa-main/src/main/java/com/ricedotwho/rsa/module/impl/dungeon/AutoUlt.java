package com.ricedotwho.rsa.module.impl.dungeon;

import com.ricedotwho.rsm.component.impl.location.Island;
import com.ricedotwho.rsm.component.impl.location.Location;
import com.ricedotwho.rsm.component.impl.map.handler.Dungeon;
import com.ricedotwho.rsm.component.impl.task.TaskComponent;
import com.ricedotwho.rsm.data.DungeonClass;
import com.ricedotwho.rsm.event.api.SubscribeEvent;
import com.ricedotwho.rsm.event.impl.game.ChatEvent.Chat;
import com.ricedotwho.rsm.module.api.Category;
import com.ricedotwho.rsm.module.api.ModuleInfo;
import com.ricedotwho.rsm.module.impl.dungeon.Abilities;
import com.ricedotwho.rsm.ui.clickgui.settings.Setting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.BooleanSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.NumberSetting;
import java.math.BigDecimal;
import java.util.regex.Pattern;
import net.minecraft.util.Formatting;

@ModuleInfo(aliases = "Abilities", id = "Abilities", category = Category.DUNGEONS, isOverwrite = true)
public class AutoUlt extends Abilities {
   private final BooleanSetting autoUlt = new BooleanSetting("Auto Ult", false);
   private final NumberSetting tankUltDelay = new NumberSetting("Tank Delay", 0.0, 40.0, 15.0, 1.0);
   private final NumberSetting healerUltDelay = new NumberSetting("Healer Delay", 0.0, 40.0, 3.0, 1.0);
   private final BooleanSetting wishCommand = new BooleanSetting("Wish Chat Command", false);
   private static final Pattern wishPattern = Pattern.compile("Party > (?:\\[(.*?)] )?(.+?): !wish");

   public AutoUlt() {
      this.registerProperty(new Setting[]{this.autoUlt, this.tankUltDelay, this.healerUltDelay, this.wishCommand});
   }

   @SubscribeEvent
   public void onChat(Chat event) {
      if (Location.getArea().is(Island.Dungeon) && Dungeon.isStarted()) {
         String value = Formatting.strip(event.getMessage().getString());
         if ((Boolean)this.wishCommand.getValue() && wishPattern.matcher(value).find()) {
            this.drop(false);
         } else {
            switch (Location.getFloor()) {
               case F6:
               case M6:
                  if ("[BOSS] Sadan: My giants! Unleashed!".equals(value)) {
                     this.drop(false);
                  }
                  break;
               case F7:
               case M7:
                  if (Dungeon.isMyClass(DungeonClass.TANK) && "[BOSS] Maxor: DON'T DISAPPOINT ME, I HAVEN'T HAD A GOOD FIGHT IN A WHILE.".equals(value)) {
                     this.useUlt(((BigDecimal)this.tankUltDelay.getValue()).longValue());
                  } else if (Dungeon.isMyClass(DungeonClass.HEALER)
                     && ("⚠ Maxor is enraged! ⚠".equals(value) || "[BOSS] Goldor: You have done it, you destroyed the factory…".equals(value))) {
                     this.useUlt(((BigDecimal)this.healerUltDelay.getValue()).longValue());
                  }
            }
         }
      }
   }

   private void useUlt(long delay) {
      TaskComponent.onServerTick(delay, () -> this.drop(false));
   }

   public BooleanSetting getAutoUlt() {
      return this.autoUlt;
   }

   public NumberSetting getTankUltDelay() {
      return this.tankUltDelay;
   }

   public NumberSetting getHealerUltDelay() {
      return this.healerUltDelay;
   }

   public BooleanSetting getWishCommand() {
      return this.wishCommand;
   }
}
