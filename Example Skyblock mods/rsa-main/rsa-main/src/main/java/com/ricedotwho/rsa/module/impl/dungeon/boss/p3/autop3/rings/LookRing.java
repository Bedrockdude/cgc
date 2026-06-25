package com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.rings;

import com.google.gson.JsonObject;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.AutoP3;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.RingType;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.args.ArgumentManager;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.subactions.SubActionManager;
import com.ricedotwho.rsm.data.Colour;
import com.ricedotwho.rsm.data.MutableInput;
import com.ricedotwho.rsm.data.Pos;
import com.ricedotwho.rsm.utils.Accessor;
import java.util.Map;
import net.minecraft.util.PlayerInput;

public class LookRing extends Ring implements Accessor {
   private final float yaw;
   private final float pitch;

   @Override
   public RingType getType() {
      return RingType.LOOK;
   }

   public LookRing(Pos min, Pos max, ArgumentManager manage, SubActionManager actions, Map<String, Object> extra) {
      this(
         min,
         max,
         (Float)extra.getOrDefault("yaw", mc.gameRenderer.getCamera().getCameraYaw()),
         (Float)extra.getOrDefault("yaw", mc.gameRenderer.getCamera().getPitch()),
         manage,
         actions
      );
   }

   public LookRing(Pos min, Pos max, float yaw, float pitch, ArgumentManager manage, SubActionManager actions) {
      super(min, max, RingType.LOOK.getRenderSizeOffset(), manage, actions);
      this.yaw = yaw;
      this.pitch = pitch;
   }

   @Override
   public boolean run() {
      mc.player.setYaw(this.yaw);
      mc.player.setPitch(this.pitch);
      return true;
   }

   @Override
   public Colour getColour() {
      return Colour.GREEN;
   }

   @Override
   public int getPriority() {
      return 50;
   }

   @Override
   public boolean tick(MutableInput mutableInput, PlayerInput input, AutoP3 autoP3) {
      return true;
   }

   @Override
   public JsonObject serialize() {
      JsonObject obj = super.serialize();
      obj.addProperty("yaw", this.yaw);
      obj.addProperty("pitch", this.pitch);
      return obj;
   }

   @Override
   public void feedback() {
      AutoP3.modMessage("Looking");
   }

   public float getYaw() {
      return this.yaw;
   }

   public float getPitch() {
      return this.pitch;
   }
}
