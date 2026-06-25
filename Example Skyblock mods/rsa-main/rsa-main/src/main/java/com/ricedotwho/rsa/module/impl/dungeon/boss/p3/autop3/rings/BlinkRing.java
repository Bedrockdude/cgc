package com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.rings;

import com.google.gson.JsonObject;
import com.ricedotwho.rsa.component.impl.managers.PacketOrderManager;
import com.ricedotwho.rsa.module.impl.dungeon.boss.Blink;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.AutoP3;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.RingType;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.args.ArgumentManager;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.recorder.MovementRecorder;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.subactions.SubActionManager;
import com.ricedotwho.rsa.module.impl.render.Freecam;
import com.ricedotwho.rsm.RSM;
import com.ricedotwho.rsm.data.Colour;
import com.ricedotwho.rsm.data.MutableInput;
import com.ricedotwho.rsm.data.Pos;
import java.util.Map;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;

public class BlinkRing extends Ring {
   private final String route;
   private final int size;
   private int ticks = -1;
   private Vec3d endPos;
   private Vec3d endVelo;

   public BlinkRing(Pos min, Pos max, ArgumentManager manage, SubActionManager actions, Map<String, Object> extra) {
      this(min, max, (String)extra.getOrDefault("route", MovementRecorder.getData().getFileName()), manage, actions, (Integer)extra.getOrDefault("blink", 17));
   }

   public BlinkRing(Pos min, Pos max, String route, ArgumentManager manage, SubActionManager actions, int length) {
      super(min, max, RingType.BLINK.getRenderSizeOffset(), manage, actions);
      this.size = MathHelper.clamp(1, length, 17);
      this.route = route;
      this.endPos = null;
      this.endVelo = null;
   }

   @Override
   public RingType getType() {
      return RingType.BLINK;
   }

   @Override
   public boolean run() {
      if (MinecraftClient.getInstance().player == null) {
         return false;
      } else {
         Blink blink = (Blink)RSM.getModule(Blink.class);
         if (!blink.isEnabled()) {
            blink.onKeyToggle();
            blink.setCurrentRing(this);
         }

         if ((Boolean)((AutoP3)RSM.getModule(AutoP3.class)).getFreecamBlink().getValue()) {
            Freecam freecam = (Freecam)RSM.getModule(Freecam.class);
            if (!freecam.isEnabled()) {
               freecam.setEnabled(true);
            }
         }

         this.ticks = 0;
         MovementRecorder.playRecording(this.route);
         return false;
      }
   }

   @Override
   public Colour getColour() {
      return Colour.pink;
   }

   @Override
   public int getPriority() {
      return 40;
   }

   private void cancel() {
      Blink blink = (Blink)RSM.getModule(Blink.class);
      if (blink.isEnabled()) {
         blink.clearMovements();
         blink.onKeyToggle();
      }
   }

   private void flush() {
      Blink blink = (Blink)RSM.getModule(Blink.class);
      if (blink.isEnabled()) {
         blink.onKeyToggle();
      }

      if (MinecraftClient.getInstance().player != null) {
         MinecraftClient.getInstance().player.setPosition(this.endPos);
         MinecraftClient.getInstance().player.setVelocity(this.endVelo);
         if ((Boolean)((AutoP3)RSM.getModule(AutoP3.class)).getFreecamBlink().getValue()) {
            Freecam freecam = (Freecam)RSM.getModule(Freecam.class);
            if (freecam.isEnabled()) {
               freecam.setEnabled(false);
            }
         }

         MovementRecorder.resumeRecording();
      }

      this.ticks = -1;
   }

   public boolean isDonePlaying() {
      return this.ticks > this.size;
   }

   public void flushNext() {
      PacketOrderManager.register(PacketOrderManager.STATE.START, this::flush);
   }

   @Override
   public boolean tick(MutableInput mutableInput, PlayerInput input, AutoP3 autoP3) {
      if (MinecraftClient.getInstance().player != null && this.ticks >= 0) {
         this.ticks++;
         if (this.ticks <= this.size + 1) {
            this.endPos = MinecraftClient.getInstance().player.getEntityPos();
            this.endVelo = MinecraftClient.getInstance().player.getVelocity();
         }

         if (this.ticks == this.size + 1) {
            MinecraftClient.getInstance().player.setPosition(this.endPos);
            MinecraftClient.getInstance().player.setVelocity(0.0, 0.0, 0.0);
            MovementRecorder.pauseRecording();
         }

         return false;
      } else {
         return true;
      }
   }

   @Override
   public JsonObject serialize() {
      JsonObject obj = super.serialize();
      obj.addProperty("route", this.route);
      obj.addProperty("size", this.size);
      return obj;
   }

   @Override
   public void feedback() {
   }
}
