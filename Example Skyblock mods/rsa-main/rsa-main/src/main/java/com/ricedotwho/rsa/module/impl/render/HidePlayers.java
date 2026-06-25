package com.ricedotwho.rsa.module.impl.render;

import com.ricedotwho.rsm.RSM;
import com.ricedotwho.rsm.component.impl.location.Island;
import com.ricedotwho.rsm.component.impl.location.Location;
import com.ricedotwho.rsm.module.api.Category;
import com.ricedotwho.rsm.module.api.ModuleInfo;
import com.ricedotwho.rsm.ui.clickgui.settings.Setting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.ModeSetting;
import com.ricedotwho.rsm.utils.Utils;
import java.util.Arrays;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.player.PlayerEntity;

@ModuleInfo(aliases = "Hide", id = "HideEntity", category = Category.RENDER, isOverwrite = true)
public class HidePlayers extends com.ricedotwho.rsm.module.impl.render.HidePlayers {
   private final ModeSetting hitThroughMode = new ModeSetting("Hit Through", "Off", Arrays.asList("Off", "Dungeon & Kuudra", "Always"));

   public HidePlayers() {
      this.registerProperty(new Setting[]{this.hitThroughMode});
   }

   public static boolean shouldHitThrough(Entity e) {
      HidePlayers hidePlayers = (HidePlayers)RSM.getModule(HidePlayers.class);
      if (hidePlayers != null && hidePlayers.isEnabled()) {
         return hidePlayers.getWither().getValue() && e instanceof WitherEntity wither && wither.getMaxHealth() == 300.0F
            ? true
            : e instanceof PlayerEntity
               && (
                  hidePlayers.getHitThroughMode().getIndex() == 1 && Utils.equalsOneOf(Location.getArea(), new Object[]{Island.Dungeon, Island.Kuudra})
                     || hidePlayers.getHitThroughMode().getIndex() == 2
               );
      } else {
         return false;
      }
   }

   public ModeSetting getHitThroughMode() {
      return this.hitThroughMode;
   }
}
