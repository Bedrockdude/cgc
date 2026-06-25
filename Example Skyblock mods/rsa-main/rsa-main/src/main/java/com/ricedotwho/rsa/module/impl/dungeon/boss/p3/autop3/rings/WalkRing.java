package com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.rings;

import com.google.gson.JsonObject;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.AutoP3;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.RingType;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.args.ArgumentManager;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.subactions.SubActionManager;
import com.ricedotwho.rsm.data.Colour;
import com.ricedotwho.rsm.data.MutableInput;
import com.ricedotwho.rsm.data.Pos;
import java.util.Map;
import net.minecraft.util.PlayerInput;
import net.minecraft.client.MinecraftClient;

public class WalkRing extends Ring {
   private final float yaw;

   @Override
   public RingType getType() {
      return RingType.WALK;
   }

   public WalkRing(Pos min, Pos max, ArgumentManager manage, SubActionManager actions, Map<String, Object> extra) {
      this(min, max, (Float)extra.getOrDefault("yaw", MinecraftClient.getInstance().gameRenderer.getCamera().getCameraYaw()), manage, actions);
   }

   public WalkRing(Pos min, Pos max, float yaw, ArgumentManager manage, SubActionManager actions) {
      super(min, max, RingType.WALK.getRenderSizeOffset(), manage, actions);
      this.yaw = yaw;
   }

   @Override
   public boolean run() {
      return true;
   }

   @Override
   public Colour getColour() {
      return Colour.CYAN;
   }

   @Override
   public int getPriority() {
      return 50;
   }

   @Override
   public boolean tick(MutableInput mutableInput, PlayerInput input, AutoP3 autoP3) {
      if (this.hasInputPressed(input)) {
         return true;
      } else {
         autoP3.setDesync(true);
         if ((Boolean)autoP3.getStrafe().getValue() && !mc.player.isOnGround()) {
            mc.player.setYaw(this.yaw - 45.0F);
            mutableInput.right(true);
         } else {
            mc.player.setYaw(this.yaw);
         }

         mutableInput.forward(true);
         mutableInput.sprint(true);
         return false;
      }
   }

   private boolean hasInputPressed(PlayerInput input) {
      return input.forward() || input.backward() || input.left() || input.right() || input.jump();
   }

   @Override
   public JsonObject serialize() {
      JsonObject obj = super.serialize();
      obj.addProperty("yaw", this.yaw);
      return obj;
   }

   @Override
   public boolean shouldStop() {
      return true;
   }

   @Override
   public void feedback() {
      AutoP3.modMessage("Walking");
   }

   public float getYaw() {
      return this.yaw;
   }
}
