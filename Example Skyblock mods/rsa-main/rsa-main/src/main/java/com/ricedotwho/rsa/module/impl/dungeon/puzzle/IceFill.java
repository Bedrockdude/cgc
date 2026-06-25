package com.ricedotwho.rsa.module.impl.dungeon.puzzle;

import com.google.common.math.DoubleMath;
import com.ricedotwho.rsa.component.impl.managers.PacketOrderManager;
import com.ricedotwho.rsa.component.impl.managers.SwapManager;
import com.ricedotwho.rsm.data.Pos;
import com.ricedotwho.rsm.event.api.SubscribeEvent;
import com.ricedotwho.rsm.event.impl.client.PacketEvent.Receive;
import com.ricedotwho.rsm.event.impl.game.ClientTickEvent.Start;
import com.ricedotwho.rsm.module.api.SubModuleInfo;
import com.ricedotwho.rsm.ui.clickgui.settings.Setting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.BooleanSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.NumberSetting;
import com.ricedotwho.rsm.utils.EtherUtils;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;

@SubModuleInfo(name = "Ice Fill", alwaysDisabled = false)
public class IceFill extends com.ricedotwho.rsm.module.impl.dungeon.puzzle.IceFill {
   public final BooleanSetting autoEnabled = new BooleanSetting("Auto Ice Fill", false);
   public final NumberSetting autoDelay = new NumberSetting("Delay", 0.0, 8.0, 0.0, 1.0);
   List<Pos> autoPath = null;
   int autoIndex = -1;
   int autoTicks = 0;

   public IceFill(Puzzles module) {
      super(module);
      this.registerProperty(new Setting[]{this.autoEnabled, this.autoDelay});
   }

   @SubscribeEvent
   public void onClientTickStart(Start event) {
      if ((Boolean)this.autoEnabled.getValue()) {
         if (this.path == null) {
            this.autoPath = null;
         }

         if (this.autoIndex >= 0) {
            if (this.autoPath == null) {
               this.autoIndex = -1;
            } else {
               int delay = ((BigDecimal)this.autoDelay.getValue()).intValue();
               if (delay >= 1) {
                  assert mc.player != null;

                  if (mc.player.getMainHandStack().getItem() == Items.DIAMOND_SHOVEL) {
                     this.autoTicks++;
                     if (this.autoTicks >= delay) {
                        this.autoTicks = 0;
                        if (++this.autoIndex < this.autoPath.size() - 1) {
                           this.doTeleport(this.autoIndex);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @SubscribeEvent
   public void onPacketReceive(Receive event) {
      if ((Boolean)this.autoEnabled.getValue()) {
         if (event.getPacket() instanceof PlayerPositionLookS2CPacket packet) {
            if (this.path == null) {
               this.autoPath = null;
            } else {
               if (this.autoPath == null) {
                  this.buildAutoPath();
               }

               assert mc.player != null;

               if (mc.player.getMainHandStack().getItem() == Items.DIAMOND_SHOVEL) {
                  int index = this.findIndex(packet.change().position());
                  int delay = ((BigDecimal)this.autoDelay.getValue()).intValue();
                  if (index >= 0 && index < this.autoPath.size() - 1) {
                     if (this.autoIndex < 0) {
                        this.autoIndex = index;
                        this.autoTicks = 0;
                        if (delay > 0) {
                           this.doTeleport(index);
                        }
                     }

                     if (delay < 1) {
                        this.doTeleport(index);
                     }
                  } else {
                     this.autoIndex = -1;
                  }
               }
            }
         }
      }
   }

   private void doTeleport(int index) {
      Pos cur = this.autoPath.get(index);
      Pos next = this.autoPath.get(index + 1);
      Pos diff = next.subtract(cur);
      float yaw = EtherUtils.getYawAndPitch(diff.x, diff.y, diff.z)[0];
      float pitch = diff.y > 0.0 ? 14.0F : 48.0F;
      PacketOrderManager.register(PacketOrderManager.STATE.ITEM_USE, () -> {
         if (SwapManager.checkClientItem(Items.DIAMOND_SHOVEL) && SwapManager.checkServerItem(Items.DIAMOND_SHOVEL)) {
            SwapManager.sendAirC08(yaw, pitch, false, false);
         }
      });
   }

   private void buildAutoPath() {
      this.autoPath = new ArrayList<>();

      for (int i = 0; i < this.path.size(); i++) {
         Pos cur = (Pos)this.path.get(i);
         if (i == this.path.size() - 1) {
            this.autoPath.add(cur);
         } else {
            Pos next = (Pos)this.path.get(i + 1);
            Pos diff = next.subtract(cur);
            Pos dir = new Pos(diff.x, 0.0, diff.z).normalize();
            if (!DoubleMath.fuzzyEquals(diff.y, 0.0, 1.0E-6)) {
               this.autoPath.add(cur);
            } else if (!DoubleMath.fuzzyEquals(diff.x, 0.0, 1.0E-6)) {
               for (int j = 0; j < Math.abs(diff.x); j++) {
                  this.autoPath.add(cur.add(dir.multiply(j)));
               }
            } else if (!DoubleMath.fuzzyEquals(diff.z, 0.0, 1.0E-6)) {
               for (int j = 0; j < Math.abs(diff.z); j++) {
                  this.autoPath.add(cur.add(dir.multiply(j)));
               }
            }
         }
      }
   }

   private int findIndex(Vec3d pos) {
      for (int i = 0; i < this.autoPath.size(); i++) {
         if (this.autoPath.get(i).squaredDistanceTo(pos) < 1.0E-6) {
            return i;
         }
      }

      return -1;
   }

   public BooleanSetting getAutoEnabled() {
      return this.autoEnabled;
   }

   public NumberSetting getAutoDelay() {
      return this.autoDelay;
   }

   public List<Pos> getAutoPath() {
      return this.autoPath;
   }

   public int getAutoIndex() {
      return this.autoIndex;
   }

   public int getAutoTicks() {
      return this.autoTicks;
   }
}
