package com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.rings;

import com.ricedotwho.rsa.component.impl.managers.PacketOrderManager;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.RingType;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.args.ArgumentManager;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.subactions.SubActionManager;
import com.ricedotwho.rsm.data.Colour;
import com.ricedotwho.rsm.data.Pos;
import java.util.Map;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;

public class FastBonzoRing extends BonzoRing {
   public FastBonzoRing(Vec3d pos) {
      super(pos);
   }

   public FastBonzoRing(Pos min, Pos max, float yaw, float pitch, ArgumentManager manager, SubActionManager actions) {
      super(min, max, yaw, pitch, manager, actions);
   }

   public FastBonzoRing(Pos min, Pos max, ArgumentManager manager, SubActionManager actions, Map<String, Object> extra) {
      this(
         min,
         max,
         (Float)extra.getOrDefault("yaw", MinecraftClient.getInstance().gameRenderer.getCamera().getCameraYaw()),
         (Float)extra.getOrDefault("pitch", MinecraftClient.getInstance().gameRenderer.getCamera().getPitch()),
         manager,
         actions
      );
   }

   @Override
   protected void registerWaitCondition() {
      PacketOrderManager.registerReceiveListener(p -> {
         if (MinecraftClient.getInstance().player == null || this.state < 1) {
            return true;
         } else if (!(p instanceof CommonPingS2CPacket)) {
            return false;
         } else {
            this.state++;
            return this.state >= 5;
         }
      });
   }

   @Override
   public Colour getColour() {
      return Colour.PINK;
   }

   @Override
   public RingType getType() {
      return RingType.FAST_BONZO;
   }
}
