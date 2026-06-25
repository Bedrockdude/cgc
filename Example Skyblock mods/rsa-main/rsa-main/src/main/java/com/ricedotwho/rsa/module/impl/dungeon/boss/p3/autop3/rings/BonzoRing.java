package com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.rings;

import com.google.gson.JsonObject;
import com.ricedotwho.rsa.component.impl.managers.PacketOrderManager;
import com.ricedotwho.rsa.component.impl.managers.SwapManager;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.AutoP3;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.RingType;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.args.ArgumentManager;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.subactions.SubActionManager;
import com.ricedotwho.rsa.module.impl.movement.VelocityBuffer;
import com.ricedotwho.rsm.RSM;
import com.ricedotwho.rsm.data.Colour;
import com.ricedotwho.rsm.data.MutableInput;
import com.ricedotwho.rsm.data.Pos;
import java.util.Map;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.client.MinecraftClient;

public class BonzoRing extends Ring {
   private final float yaw;
   private final float pitch;
   protected byte state;
   protected static final byte END_STATE = 5;

   public BonzoRing(Vec3d pos) {
      super(pos, 0.5, (double)RingType.BONZO.getRenderSizeOffset());
      this.yaw = MinecraftClient.getInstance().gameRenderer.getCamera().getCameraYaw();
      this.pitch = MinecraftClient.getInstance().gameRenderer.getCamera().getPitch();
      this.state = 0;
   }

   public BonzoRing(Pos min, Pos max, ArgumentManager manager, SubActionManager actions, Map<String, Object> extra) {
      this(
         min,
         max,
         (Float)extra.getOrDefault("yaw", MinecraftClient.getInstance().gameRenderer.getCamera().getCameraYaw()),
         (Float)extra.getOrDefault("pitch", MinecraftClient.getInstance().gameRenderer.getCamera().getPitch()),
         manager,
         actions
      );
   }

   public BonzoRing(Pos min, Pos max, float yaw, float pitch, ArgumentManager manager, SubActionManager actions) {
      super(min, max, RingType.BONZO.getRenderSizeOffset(), manager, actions);
      this.yaw = yaw;
      this.pitch = pitch;
   }

   @Override
   public RingType getType() {
      return RingType.BONZO;
   }

   @Override
   public void reset() {
      super.reset();
      this.state = 0;
   }

   protected void registerWaitCondition() {
      PacketOrderManager.registerReceiveListener(p -> {
         if (MinecraftClient.getInstance().player != null && this.state == 1) {
            if (p instanceof EntityVelocityUpdateS2CPacket motionPacket && motionPacket.getEntityId() == MinecraftClient.getInstance().player.getId()) {
               this.state = 5;
               return true;
            } else {
               return false;
            }
         } else {
            return true;
         }
      });
   }

   @Override
   public boolean run() {
      if (MinecraftClient.getInstance().player == null) {
         return false;
      } else {
         switch (this.state) {
            case 0:
               super.reset();
               if (!SwapManager.swapItem("BONZO_STAFF")) {
                  return false;
               }

               VelocityBuffer velocityBuffer = (VelocityBuffer)RSM.getModule(VelocityBuffer.class);
               if (!velocityBuffer.isEnabled()) {
                  velocityBuffer.onKeyToggle();
               }

               PacketOrderManager.register(PacketOrderManager.STATE.ITEM_USE, () -> SwapManager.sendAirC08(this.yaw, this.pitch, true));
               this.state = 1;
               this.registerWaitCondition();
               return false;
            case 5:
               return false;
            default:
               super.reset();
               return false;
         }
      }
   }

   @Override
   public JsonObject serialize() {
      JsonObject obj = super.serialize();
      obj.addProperty("yaw", this.yaw);
      obj.addProperty("pitch", this.pitch);
      return obj;
   }

   @Override
   public Colour getColour() {
      return Colour.MAGENTA;
   }

   @Override
   public int getPriority() {
      return 75;
   }

   @Override
   public boolean tick(MutableInput mutableInput, PlayerInput input, AutoP3 autoP3) {
      return true;
   }

   @Override
   public void feedback() {
   }
}
