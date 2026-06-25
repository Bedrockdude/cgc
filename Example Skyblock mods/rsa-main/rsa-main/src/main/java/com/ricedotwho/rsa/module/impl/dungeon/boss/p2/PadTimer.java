package com.ricedotwho.rsa.module.impl.dungeon.boss.p2;

import com.ricedotwho.rsa.RSA;
import com.ricedotwho.rsm.component.impl.location.Island;
import com.ricedotwho.rsm.component.impl.location.Location;
import com.ricedotwho.rsm.data.Colour;
import com.ricedotwho.rsm.data.Phase7;
import com.ricedotwho.rsm.event.api.SubscribeEvent;
import com.ricedotwho.rsm.event.impl.game.ServerTickEvent;
import com.ricedotwho.rsm.event.impl.game.ChatEvent.Chat;
import com.ricedotwho.rsm.event.impl.render.Render2DEvent;
import com.ricedotwho.rsm.event.impl.world.WorldEvent.Load;
import com.ricedotwho.rsm.module.Module;
import com.ricedotwho.rsm.module.api.Category;
import com.ricedotwho.rsm.module.api.ModuleInfo;
import com.ricedotwho.rsm.ui.clickgui.settings.Setting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.BooleanSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.ButtonSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.DragSetting;
import com.ricedotwho.rsm.utils.DungeonUtils;
import com.ricedotwho.rsm.utils.render.render2d.NVGUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.StringHelper;
import net.minecraft.client.network.ClientPlayerEntity;
import org.joml.Vector2d;

@ModuleInfo(aliases = "Pad Timer", id = "PadTimer", category = Category.DUNGEONS)
public class PadTimer extends Module {
   private int seconds = 4;
   private int second = 20;
   private int padSeconds = 4;
   private boolean IsEnabled = false;
   private boolean pPadcountdown = false;
   private boolean countdownP = false;
   private boolean pPadmsg = false;
   private int stopShowing = 44;
   private int stopShowing2 = 1;
   private int pPadTicks = 80;
   private int yPadTicks = 48;
   private final ButtonSetting rsvalues = new ButtonSetting("Restart Values", "restartvalues", this::reset);
   private final BooleanSetting debug = new BooleanSetting("Debug", false, () -> true);
   private final DragSetting padAlert = new DragSetting("Pad Alert", new Vector2d(0.0, 0.0), new Vector2d(0.0, 0.0));
   String string = "Pad in " + this.seconds;

   public PadTimer() {
      this.registerProperty(new Setting[]{this.padAlert, this.rsvalues, this.debug});
   }

   public void onEnable() {
      this.IsEnabled = true;
   }

   public void onDisable() {
      this.IsEnabled = false;
   }

   public void reset() {
      this.seconds = 4;
      this.second = 20;
      this.padSeconds = 4;
      this.IsEnabled = false;
      this.pPadcountdown = false;
      this.countdownP = false;
      this.pPadmsg = false;
      this.stopShowing = 44;
      this.stopShowing2 = 1;
      this.pPadTicks = 80;
      this.yPadTicks = 48;
   }

   @SubscribeEvent
   public void onWorldLoad(Load event) {
      this.reset();
   }

   @SubscribeEvent
   public void onChat(Chat event) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null) {
         String unformatted = StringHelper.stripTextFormat(event.getMessage().getString());
         if (unformatted.contains("I'd be happy to show you what that's like!") && Location.getArea() == Island.Dungeon && DungeonUtils.isPhase(Phase7.P2)
            || (Boolean)this.debug.getValue()) {
            this.pPadcountdown = true;
            this.pPadmsg = true;
            this.IsEnabled = true;
            RSA.chat("Pad Countdown Started.");
         }
      }
   }

   @SubscribeEvent
   public void onTick(ServerTickEvent event) {
      if (Location.getArea() == Island.Dungeon && DungeonUtils.isPhase(Phase7.P2) && this.IsEnabled || (Boolean)this.debug.getValue()) {
         if (this.pPadcountdown) {
            this.countdownP = true;
         }

         if (this.padSeconds <= 0) {
            this.countdownP = false;
         }

         if (this.second > 0 && this.pPadTicks <= 0 && this.countdownP) {
            this.second--;
            if (this.second == 0) {
               RSA.chat("PAD IN: " + this.padSeconds);
               this.padSeconds--;
            }

            if (this.padSeconds <= 0) {
               this.countdownP = false;
            }

            return;
         }

         this.seconds--;
         if (this.seconds <= 0 && this.pPadcountdown) {
            this.second = 20;
         }

         if (this.stopShowing > 0 && this.pPadTicks <= 0) {
            this.stopShowing--;
            return;
         }

         if (this.pPadTicks > 0 && this.pPadcountdown && this.countdownP) {
            this.pPadTicks--;
            return;
         }

         if (this.pPadTicks == 0) {
            this.pPadTicks = 1;
         }

         this.pPadcountdown = false;
      }
   }

   @SubscribeEvent
   public void onRender2D(Render2DEvent event) {
      if (this.padSeconds <= 0 && this.stopShowing > this.stopShowing2 && Location.getArea() == Island.Dungeon) {
         this.padAlert.renderScaled(event.getGfx(), () -> NVGUtils.drawText("Pad Now", 0.0F, 0.0F, 50.0F, Colour.blue, NVGUtils.JOSEFIN), 60.0F, 30.0F);
      }
   }

   public int getSeconds() {
      return this.seconds;
   }

   public int getSecond() {
      return this.second;
   }

   public int getPadSeconds() {
      return this.padSeconds;
   }

   public boolean isIsEnabled() {
      return this.IsEnabled;
   }

   public boolean isPPadcountdown() {
      return this.pPadcountdown;
   }

   public boolean isCountdownP() {
      return this.countdownP;
   }

   public boolean isPPadmsg() {
      return this.pPadmsg;
   }

   public int getStopShowing() {
      return this.stopShowing;
   }

   public int getStopShowing2() {
      return this.stopShowing2;
   }

   public int getPPadTicks() {
      return this.pPadTicks;
   }

   public int getYPadTicks() {
      return this.yPadTicks;
   }

   public ButtonSetting getRsvalues() {
      return this.rsvalues;
   }

   public BooleanSetting getDebug() {
      return this.debug;
   }

   public DragSetting getPadAlert() {
      return this.padAlert;
   }

   public String getString() {
      return this.string;
   }
}
