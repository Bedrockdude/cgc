package com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.subactions.type;

import com.google.gson.JsonObject;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.subactions.SubAction;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.subactions.SubActionType;

public class LookAction extends SubAction {
   private final float yaw;
   private final float pitch;

   public LookAction() {
      super(SubActionType.LOOK);
      this.yaw = mc.gameRenderer.getCamera().getCameraYaw();
      this.pitch = mc.gameRenderer.getCamera().getPitch();
   }

   public LookAction(float yaw, float pitch) {
      super(SubActionType.LOOK);
      this.yaw = yaw;
      this.pitch = pitch;
   }

   @Override
   public boolean execute() {
      if (mc.player == null) {
         return false;
      } else {
         mc.player.setYaw(this.yaw);
         mc.player.setPitch(this.pitch);
         return true;
      }
   }

   @Override
   public void serialize(JsonObject obj) {
      JsonObject o = new JsonObject();
      o.addProperty("yaw", this.yaw);
      o.addProperty("pitch", this.pitch);
      obj.add(this.getType().name(), o);
   }
}
