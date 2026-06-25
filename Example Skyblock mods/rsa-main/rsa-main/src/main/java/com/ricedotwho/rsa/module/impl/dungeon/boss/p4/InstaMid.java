package com.ricedotwho.rsa.module.impl.dungeon.boss.p4;

import com.ricedotwho.rsa.RSA;
import com.ricedotwho.rsa.component.impl.Jump;
import com.ricedotwho.rsm.component.impl.location.Floor;
import com.ricedotwho.rsm.component.impl.location.Island;
import com.ricedotwho.rsm.component.impl.location.Location;
import com.ricedotwho.rsm.component.impl.map.handler.Dungeon;
import com.ricedotwho.rsm.data.Phase7;
import com.ricedotwho.rsm.event.api.SubscribeEvent;
import com.ricedotwho.rsm.event.impl.client.PacketEvent.Send;
import com.ricedotwho.rsm.event.impl.game.ChatEvent.Chat;
import com.ricedotwho.rsm.event.impl.world.WorldEvent.Load;
import com.ricedotwho.rsm.module.Module;
import com.ricedotwho.rsm.module.api.Category;
import com.ricedotwho.rsm.module.api.ModuleInfo;
import com.ricedotwho.rsm.ui.clickgui.settings.Setting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.NumberSetting;
import com.ricedotwho.rsm.utils.DungeonUtils;
import java.math.BigDecimal;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

@ModuleInfo(aliases = "InstaMid", id = "InstaMid", category = Category.DUNGEONS)
public class InstaMid extends Module {
   private final NumberSetting millis = new NumberSetting("Millis", 6000.0, 7000.0, 6415.0, 5.0);
   private boolean startOnNextFlying = false;
   private int airTicks = 0;

   public InstaMid() {
      this.registerProperty(new Setting[]{this.millis});
   }

   public void reset() {
      this.startOnNextFlying = false;
      this.airTicks = 0;
   }

   @SubscribeEvent
   public void onLoad(Load event) {
      this.reset();
   }

   @SubscribeEvent
   public void onPacketSend(Send event) {
      if (event.getPacket() instanceof PlayerMoveC2SPacket packet
         && Location.getArea().is(Island.Dungeon)
         && (Location.getFloor() == Floor.F7 || Location.getFloor() == Floor.M7)
         && Dungeon.isInBoss()
         && DungeonUtils.isPhase(Phase7.P4)
         && this.startOnNextFlying
         && !packet.isOnGround()
         && this.isOnPlatform()) {
         this.airTicks++;
         if (this.airTicks > 3) {
            this.startIMid();
         }
      }
   }

   @SubscribeEvent
   public void onChat(Chat event) {
      String unformatted = Formatting.strip(event.getMessage().getString());
      if (Location.getArea().is(Island.Dungeon)
         && (Location.getFloor() == Floor.F7 || Location.getFloor() == Floor.M7)
         && DungeonUtils.isPhase(Phase7.P4)
         && "[BOSS] Necron: You went further than any human before, congratulations.".equals(unformatted)
         && this.isOnPlatform()
         && mc.player != null) {
         if (mc.player.isOnGround()) {
            this.startOnNextFlying = true;
            Jump.jump();
         } else {
            this.startIMid();
         }
      }
   }

   private void startIMid() {
      this.startOnNextFlying = false;
      RSA.chat("Attempting to InstaMid");
      this.freeze();
   }

   public void freeze() {
      try {
         Thread.sleep(((BigDecimal)this.millis.getValue()).longValue());
      } catch (InterruptedException interruptedException) {
         throw new RuntimeException(interruptedException);
      }
   }

   private boolean isOnPlatform() {
      Vec3d pos = mc.player.getEntityPos();
      return pos.getY() > 63.0
         && pos.getY() < 100.0
         && Math.pow(Math.abs(pos.getX() - 54.5), 2.0) + Math.pow(Math.abs(pos.getZ() - 76.5), 2.0) < 56.25;
   }

   public NumberSetting getMillis() {
      return this.millis;
   }

   public boolean isStartOnNextFlying() {
      return this.startOnNextFlying;
   }

   public int getAirTicks() {
      return this.airTicks;
   }
}
