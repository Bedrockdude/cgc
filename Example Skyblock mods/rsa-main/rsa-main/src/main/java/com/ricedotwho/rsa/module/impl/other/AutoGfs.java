package com.ricedotwho.rsa.module.impl.other;

import com.ricedotwho.rsa.component.impl.managers.SwapManager;
import com.ricedotwho.rsm.component.impl.location.Island;
import com.ricedotwho.rsm.component.impl.location.Location;
import com.ricedotwho.rsm.event.api.SubscribeEvent;
import com.ricedotwho.rsm.event.impl.game.ServerTickEvent;
import com.ricedotwho.rsm.event.impl.game.ClientTickEvent.Start;
import com.ricedotwho.rsm.event.impl.world.WorldEvent.Load;
import com.ricedotwho.rsm.module.Module;
import com.ricedotwho.rsm.module.api.Category;
import com.ricedotwho.rsm.module.api.ModuleInfo;
import com.ricedotwho.rsm.ui.clickgui.settings.Setting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.BooleanSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.NumberSetting;
import java.math.BigDecimal;
import net.minecraft.item.ItemStack;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

@ModuleInfo(aliases = "Auto Gfs", id = "AutoGfs", category = Category.OTHER)
public class AutoGfs extends Module {
   private final BooleanSetting enderPearl = new BooleanSetting("Ender Pearl", false);
   private final BooleanSetting spiritLeap = new BooleanSetting("Spirit Leap", false);
   private final BooleanSetting superBoom = new BooleanSetting("Super Boom", false);
   private final NumberSetting worldLoadTicks = new NumberSetting("World Load Delay", 20.0, 80.0, 40.0, 1.0);
   private final NumberSetting getItemDelay = new NumberSetting("Get Item Delay", 20.0, 80.0, 40.0, 1.0);
   private int loadDelay = 0;
   private boolean worldLoaded = false;
   private boolean countdownStarted = false;
   private int globalDelay = 0;

   public AutoGfs() {
      this.registerProperty(new Setting[]{this.enderPearl, this.spiritLeap, this.superBoom, this.getItemDelay, this.worldLoadTicks});
   }

   @SubscribeEvent
   public void onTick(Start event) {
      if (Location.getArea() != Island.Unknown) {
         if (this.worldLoaded) {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if (player != null) {
               if (this.globalDelay > 0) {
                  this.globalDelay--;
               } else {
                  boolean sentCommand = false;
                  if ((Boolean)this.enderPearl.getValue() && tryGetItem(16, "ENDER_PEARL")) {
                     this.globalDelay = 20;
                     sentCommand = true;
                  }

                  if (!sentCommand && (Boolean)this.spiritLeap.getValue() && tryGetItem(16, "ENDER_PEARL")) {
                     this.globalDelay = 20;
                     sentCommand = true;
                  }

                  if (!sentCommand && (Boolean)this.superBoom.getValue() && tryGetItem(64, "SUPERBOOM_TNT")) {
                     this.globalDelay = 20;
                  }
               }
            }
         }
      }
   }

   public static boolean tryGetItem(int maxStack, String sbId) {
      return tryGetItem(maxStack, sbId, false);
   }

   public static boolean tryGetItem(int maxStack, String sbId, boolean notExisting) {
      int slot = SwapManager.getItemSlot(sbId);
      if (slot == -1) {
         if (notExisting) {
            mc.player.networkHandler.sendChatCommand("gfs " + sbId + " " + maxStack);
            return true;
         } else {
            return false;
         }
      } else {
         ItemStack stack = mc.player.getInventory().getStack(slot);
         int count = stack.getCount();
         if (count > 0 && count < maxStack) {
            int missing = maxStack - count;
            mc.player.networkHandler.sendChatCommand("gfs " + sbId + " " + missing);
            return true;
         } else {
            return false;
         }
      }
   }

   @SubscribeEvent
   public void worldLoad(Load event) {
      this.countdownStarted = true;
      this.loadDelay = ((BigDecimal)this.worldLoadTicks.getValue()).intValue();
   }

   @SubscribeEvent
   public void countDown(ServerTickEvent event) {
      if (Location.getArea() != Island.Unknown) {
         if (this.countdownStarted) {
            this.worldLoaded = false;
            if (this.loadDelay > 0) {
               this.loadDelay--;
               return;
            }

            this.countdownStarted = false;
            this.worldLoaded = true;
         }
      }
   }

   public BooleanSetting getEnderPearl() {
      return this.enderPearl;
   }

   public BooleanSetting getSpiritLeap() {
      return this.spiritLeap;
   }

   public BooleanSetting getSuperBoom() {
      return this.superBoom;
   }

   public NumberSetting getWorldLoadTicks() {
      return this.worldLoadTicks;
   }

   public NumberSetting getGetItemDelay() {
      return this.getItemDelay;
   }

   public int getLoadDelay() {
      return this.loadDelay;
   }

   public boolean isWorldLoaded() {
      return this.worldLoaded;
   }

   public boolean isCountdownStarted() {
      return this.countdownStarted;
   }

   public int getGlobalDelay() {
      return this.globalDelay;
   }
}
