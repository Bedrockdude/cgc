package com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3;

import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;

public class MovementPredictor {
   public static double getTickVelocityFromInput(int tickIndex, double walkSpeed) {
      return Math.pow(0.546000082, tickIndex) * 0.098 * walkSpeed;
   }

   public static double getDisplacementFromInput(double walkSpeed, boolean sneaking) {
      if (sneaking) {
         walkSpeed *= 0.3;
      }

      int movementTicks = getInputMovementTicks(walkSpeed);
      return 0.098 * walkSpeed * (1.0 - Math.pow(0.546000082, movementTicks)) / 0.45399991799999995;
   }

   public static double squaredAfterTick(double fwd, double right, double dFwd, double dRight) {
      double nf = (fwd + dFwd) * 0.546000082;
      double nr = (right + dRight) * 0.546000082;
      return nf * nf + nr * nr;
   }

   public static int getMovementTicks(float dx, float dy) {
      return (int)Math.ceil(Math.log(0.003 / MathHelper.sqrt(dx * dx + dy + dy)) / Math.log(0.546000082));
   }

   public static double getDisplacementMagnitude(Vec2f velocity) {
      double magnitude = velocity.length();
      int movementTicks = (int)Math.ceil(Math.log(0.003 / magnitude) / Math.log(0.546000082));
      return movementTicks <= 0 ? magnitude : magnitude * (1.0 - Math.pow(0.546000082, movementTicks)) / 0.45399991799999995;
   }

   public static Vec2f getDisplacementVector(Vec2f velocity) {
      float magnitude = velocity.length();
      if (magnitude < 1.0E-6) {
         return Vec2f.ZERO;
      } else {
         float displacement = (float)getDisplacementMagnitude(velocity);
         float scale = displacement / magnitude;
         return velocity.multiply(scale);
      }
   }

   private static int getInputMovementTicks(double velocity) {
      return (int)Math.ceil(Math.log(0.003 / (0.098 * velocity)) / Math.log(0.546000082));
   }

   public static Vec2f rotateVec(Vec2f vec, float yaw) {
      double yawRad = Math.toRadians(yaw);
      double cos = Math.cos(yawRad);
      double sin = Math.sin(yawRad);
      float newX = (float)(vec.x * cos - vec.y * sin);
      float newY = (float)(vec.x * sin + vec.y * cos);
      return new Vec2f(newX, newY);
   }

   public static Vec3d rotateVec(Vec3d vec, float yaw) {
      double yawRad = Math.toRadians(yaw);
      double cos = Math.cos(yawRad);
      double sin = Math.sin(yawRad);
      double newX = vec.x * cos - vec.z * sin;
      double newZ = vec.x * sin + vec.z * cos;
      return new Vec3d(newX, vec.y, newZ);
   }
}
