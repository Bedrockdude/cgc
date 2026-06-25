package com.ricedotwho.rsa.module.impl.dungeon.boss.p3;

import com.ricedotwho.rsa.component.impl.managers.PacketOrderManager;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.terminals.auto.AutoTerms;
import com.ricedotwho.rsa.utils.InteractUtils;
import com.ricedotwho.rsm.RSM;
import com.ricedotwho.rsm.component.impl.location.Floor;
import com.ricedotwho.rsm.component.impl.location.Island;
import com.ricedotwho.rsm.component.impl.location.Location;
import com.ricedotwho.rsm.component.impl.map.handler.Dungeon;
import com.ricedotwho.rsm.data.Phase7;
import com.ricedotwho.rsm.event.api.SubscribeEvent;
import com.ricedotwho.rsm.event.impl.game.ClientTickEvent.Start;
import com.ricedotwho.rsm.module.Module;
import com.ricedotwho.rsm.module.api.Category;
import com.ricedotwho.rsm.module.api.ModuleInfo;
import com.ricedotwho.rsm.ui.clickgui.settings.Setting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.BooleanSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.NumberSetting;
import com.ricedotwho.rsm.utils.DungeonUtils;
import com.ricedotwho.rsm.utils.MathUtils;
import java.math.BigDecimal;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;

@ModuleInfo(aliases = "Term Aura", id = "TermAura", category = Category.DUNGEONS)
public class TermAura extends Module {
   private static final double AURA_RANGE = 4.0;
   private static final double AURA_RANGE_SQ = 16.0;
   private final NumberSetting delay = new NumberSetting("Delay", 50.0, 5000.0, 500.0, 50.0);
   private final BooleanSetting showArmorStands = new BooleanSetting("Show Hitboxes", false);
   private final BooleanSetting forceSkyblock = new BooleanSetting("Force Skyblock", false);
   private long lastClick = 0L;

   public TermAura() {
      this.registerProperty(new Setting[]{this.delay, this.showArmorStands, this.forceSkyblock});
   }

   @SubscribeEvent
   public void onTick(Start event) {
      if (mc.currentScreen == null) {
         PacketOrderManager.register(PacketOrderManager.STATE.ITEM_USE, this::rapeArmorstands);
      }
   }

   private void rapeArmorstands() {
      if (MinecraftClient.getInstance().player != null && MinecraftClient.getInstance().world != null && MinecraftClient.getInstance().getNetworkHandler() != null) {
         if (System.currentTimeMillis() - this.lastClick >= ((BigDecimal)this.delay.getValue()).longValue()) {
            if (this.locationCheck()) {
               if (!AutoTerms.isInTerminal() && !(MinecraftClient.getInstance().currentScreen instanceof HandledScreen)) {
                  Vec3d eyePos = MinecraftClient.getInstance().player.getEntityPos().add(0.0, MinecraftClient.getInstance().player.getStandingEyeHeight(), 0.0);
                  double bestDistance = 16.0;
                  ArmorStandEntity bestCandidate = null;
                  Vec3d retardedPos = MinecraftClient.getInstance().player.getEntityPos().add(0.0, -2.0, 0.0);
                  Box box = new Box(retardedPos, retardedPos).expand(4.0, 4.0, 4.0);

                  for (ArmorStandEntity stand : MinecraftClient.getInstance().world.getEntitiesByClass(ArmorStandEntity.class, box, TermAura::filterEntities)) {
                     double distance = stand.getEntityPos().squaredDistanceTo(retardedPos);
                     if (distance <= bestDistance) {
                        bestCandidate = stand;
                        bestDistance = distance;
                     }
                  }

                  if (bestCandidate != null) {
                     Vec3d vec3 = MathUtils.clamp(bestCandidate.getBoundingBox(), eyePos)
                        .subtract(bestCandidate.getX(), bestCandidate.getY(), bestCandidate.getZ());
                     InteractUtils.interactOnEntity(bestCandidate, vec3);
                     this.lastClick = System.currentTimeMillis();
                  }
               }
            }
         }
      }
   }

   public static boolean getEntityVisibility(Entity entity) {
      if (!entity.isInvisible()) {
         return true;
      } else {
         TermAura termAura = (TermAura)RSM.getModule(TermAura.class);
         return termAura == null ? false : termAura.isEnabled() && (Boolean)termAura.showArmorStands.getValue() && termAura.locationCheck();
      }
   }

   private boolean locationCheck() {
      return (Boolean)this.forceSkyblock.getValue()
         || Location.getArea().is(Island.Dungeon)
            && (Location.getFloor() == Floor.F7 || Location.getFloor() == Floor.M7)
            && DungeonUtils.isPhase(Phase7.P3)
            && Dungeon.isInBoss();
   }

   private static boolean filterEntities(ArmorStandEntity armorStand) {
      if (armorStand.isDead()) {
         return false;
      } else {
         Text name = armorStand.getCustomName();
         return name == null ? false : name.getString().equals("Inactive Terminal");
      }
   }
}
