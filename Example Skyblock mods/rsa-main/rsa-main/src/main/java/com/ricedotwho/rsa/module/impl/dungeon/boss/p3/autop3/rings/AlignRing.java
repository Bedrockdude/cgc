package com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.rings;

import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.AutoP3;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.MovementPredictor;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.RingType;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.args.ArgumentManager;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.subactions.SubActionManager;
import com.ricedotwho.rsm.data.Colour;
import com.ricedotwho.rsm.data.MutableInput;
import com.ricedotwho.rsm.data.Pos;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.MinecraftClient;
import oshi.util.tuples.Pair;

public class AlignRing extends Ring {
   private Queue<Pair<Float, Boolean>> yaws;

   public AlignRing(Pos min, Pos max, double renderOffset, ArgumentManager manager, SubActionManager actions) {
      super(min, max, renderOffset, manager, actions);
   }

   public AlignRing(Pos min, Pos max, ArgumentManager manager, SubActionManager actions) {
      super(min, max, RingType.ALIGN.getRenderSizeOffset(), manager, actions);
   }

   public AlignRing(Pos min, Pos max, ArgumentManager manager, SubActionManager actions, Map<String, Object> ignored) {
      super(min, max, RingType.ALIGN.getRenderSizeOffset(), manager, actions);
   }

   @Override
   public RingType getType() {
      return RingType.ALIGN;
   }

   @Override
   public boolean run() {
      this.yaws = null;
      if (MinecraftClient.getInstance().player != null && MinecraftClient.getInstance().player.isOnGround()) {
         Vec3d initialVelocity = MinecraftClient.getInstance().player.getVelocity();
         Vec2f initialDisplacement = MovementPredictor.getDisplacementVector(
            new Vec2f((float)initialVelocity.x, (float)initialVelocity.z)
         );
         Vec3d position = MinecraftClient.getInstance().player.getEntityPos();
         Vec3d boxCenter = this.getBox().getCenter();
         Vec3d target = new Vec3d(boxCenter.x, position.y, boxCenter.z);
         Vec3d delta = target.subtract(position.add(initialDisplacement.x, 0.0, initialDisplacement.y));
         double deltaLength = delta.length();
         boolean sneaking = true;
         double displacement = MovementPredictor.getDisplacementFromInput(MinecraftClient.getInstance().player.getMovementSpeed() * 10.0F, sneaking);
         if (deltaLength < 0.01) {
            this.yaws = new LinkedList<>();
            return false;
         } else {
            if (deltaLength > 2.0 * displacement) {
               sneaking = false;
               displacement = MovementPredictor.getDisplacementFromInput(MinecraftClient.getInstance().player.getMovementSpeed() * 10.0F, sneaking);
               if (deltaLength > 2.0 * displacement) {
                  AutoP3.modMessage("Too far!");
                  this.reset();
                  return false;
               }
            }

            KeyBinding.unpressAll();
            double yaw = (float)Math.atan2(-delta.z, delta.x);
            double theta = Math.acos(deltaLength / (2.0 * displacement));
            this.yaws = new LinkedList<>();
            this.yaws.add(new Pair((float)(-Math.toDegrees(yaw + theta)) - 90.0F, sneaking));
            this.yaws.add(new Pair((float)(-Math.toDegrees(yaw - theta)) - 90.0F, sneaking));
            return false;
         }
      } else {
         this.reset();
         return false;
      }
   }

   @Override
   public Colour getColour() {
      return Colour.GREEN;
   }

   @Override
   public int getPriority() {
      return 100;
   }

   protected double getPrecision() {
      return 1.0E-4;
   }

   @Override
   public boolean tick(MutableInput mutableInput, PlayerInput input, AutoP3 autoP3) {
      if (this.yaws != null) {
         if (MinecraftClient.getInstance().player == null) {
            return true;
         } else if (this.yaws.isEmpty()) {
            Vec3d vel = MinecraftClient.getInstance().player.getVelocity();
            if (vel.x == 0.0 && vel.z == 0.0) {
               return true;
            } else if (vel.lengthSquared() > 0.09) {
               return false;
            } else {
               Vec3d boxCenter = this.getBox().getCenter();
               Vec3d target = new Vec3d(boxCenter.x, MinecraftClient.getInstance().player.getEntityPos().y, boxCenter.z);
               return MinecraftClient.getInstance().player.getEntityPos().squaredDistanceTo(target) <= this.getPrecision();
            }
         } else if ((Boolean)this.yaws.peek().getB() && !MinecraftClient.getInstance().player.getLastPlayerInput().sneak()) {
            mutableInput.shift(true);
            return false;
         } else {
            autoP3.setDesync(true);
            Pair<Float, Boolean> entry = this.yaws.poll();
            MinecraftClient.getInstance().player.setYaw((Float)entry.getA());
            mutableInput.shift((Boolean)entry.getB());
            mutableInput.forward(true);
            return false;
         }
      } else {
         return MinecraftClient.getInstance().player != null && !MinecraftClient.getInstance().player.isOnGround();
      }
   }

   @Override
   public boolean isStop() {
      return true;
   }

   @Override
   public void feedback() {
      AutoP3.modMessage("Aligning!");
   }
}
