package com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.rings;

import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.AutoP3;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.MovementPredictor;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.RingType;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.args.ArgumentManager;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.subactions.SubActionManager;
import com.ricedotwho.rsm.data.Colour;
import com.ricedotwho.rsm.data.MutableInput;
import com.ricedotwho.rsm.data.Pos;
import java.util.Map;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;

public class StopRing extends Ring {
   public StopRing(Pos min, Pos max, ArgumentManager manage, SubActionManager actions) {
      super(min, max, RingType.STOP.getRenderSizeOffset(), manage, actions);
   }

   public StopRing(Pos min, Pos max, ArgumentManager manage, SubActionManager actions, Map<String, Object> ignored) {
      super(min, max, RingType.STOP.getRenderSizeOffset(), manage, actions);
   }

   @Override
   public RingType getType() {
      return RingType.STOP;
   }

   @Override
   public boolean run() {
      KeyBinding.unpressAll();
      return false;
   }

   @Override
   public Colour getColour() {
      return Colour.RED;
   }

   @Override
   public int getPriority() {
      return 110;
   }

   @Override
   public void reset() {
      super.reset();
   }

   @Override
   public boolean tick(MutableInput mutableInput, PlayerInput input, AutoP3 autoP3) {
      if (MinecraftClient.getInstance().player == null) {
         return true;
      } else {
         Vec3d velocity = MinecraftClient.getInstance().player.getVelocity();
         double speedSq = velocity.horizontalLengthSquared();
         if (speedSq < 1.0E-4) {
            return true;
         } else {
            float yaw = (float)Math.toRadians(MinecraftClient.getInstance().player.getYaw());
            float fwdX = -MathHelper.sin(yaw);
            float fwdZ = MathHelper.cos(yaw);
            float rightX = MathHelper.cos(yaw);
            float rightZ = MathHelper.sin(yaw);
            double fwdDot = velocity.x * fwdX + velocity.z * fwdZ;
            double rightDot = velocity.x * rightX + velocity.z * rightZ;
            double accel = MinecraftClient.getInstance().player.getMovementSpeed() * 0.98;
            double baseNextSq = MovementPredictor.squaredAfterTick(fwdDot, rightDot, 0.0, 0.0);
            boolean pressFwd = fwdDot < -0.01 && MovementPredictor.squaredAfterTick(fwdDot, rightDot, accel, 0.0) < baseNextSq;
            boolean pressBack = fwdDot > 0.01 && MovementPredictor.squaredAfterTick(fwdDot, rightDot, -accel, 0.0) < baseNextSq;
            boolean pressLeft = rightDot > 0.01 && MovementPredictor.squaredAfterTick(fwdDot, rightDot, 0.0, -accel) < baseNextSq;
            boolean pressRight = rightDot < -0.01 && MovementPredictor.squaredAfterTick(fwdDot, rightDot, 0.0, accel) < baseNextSq;
            mutableInput.forward(pressFwd);
            mutableInput.backward(pressBack);
            mutableInput.left(pressLeft);
            mutableInput.right(pressRight);
            return true;
         }
      }
   }

   @Override
   public boolean isStop() {
      return true;
   }

   @Override
   public void feedback() {
      AutoP3.modMessage("Stopping");
   }
}
