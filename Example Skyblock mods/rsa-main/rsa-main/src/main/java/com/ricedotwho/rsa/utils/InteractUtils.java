package com.ricedotwho.rsa.utils;

import com.ricedotwho.rsa.component.impl.managers.PacketOrderManager;
import com.ricedotwho.rsa.component.impl.managers.SwapManager;
import com.ricedotwho.rsm.data.Pos;
import com.ricedotwho.rsm.utils.Accessor;
import com.ricedotwho.rsm.utils.MathUtils;
import com.ricedotwho.rsm.utils.RotationUtils;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResult;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.BlockState;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.ActionResult.Success;
import net.minecraft.util.ActionResult.SwingSource;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;

public final class InteractUtils implements Accessor {
   public static final double BLOCK_RANGE = 25.0;
   public static final double ENTITY_RANGE = 4.0;

   public static boolean interactOnEntity(Entity entity) {
      if (mc.player == null) {
         return false;
      } else {
         Vec3d eyePos = mc.player.getEntityPos().add(0.0, mc.player.getStandingEyeHeight(), 0.0);
         Vec3d location = MathUtils.clamp(entity.getBoundingBox(), eyePos).subtract(entity.getX(), entity.getY(), entity.getZ());
         return interactOnEntity(entity, location);
      }
   }

   public static boolean interactOnEntity(Entity entity, Vec3d location) {
      if (mc.player != null && mc.world != null && mc.interactionManager != null) {
         for (Hand interactionHand : Hand.values()) {
            ItemStack itemStack = mc.player.getStackInHand(interactionHand);
            if (!itemStack.isItemEnabled(mc.world.getEnabledFeatures())) {
               return false;
            }

            ActionResult interactionResult = mc.interactionManager.interactEntityAtLocation(mc.player, entity, new EntityHitResult(entity, location), interactionHand);
            if (!interactionResult.isAccepted()) {
               interactionResult = mc.interactionManager.interactEntity(mc.player, entity, interactionHand);
            }

            if (interactionResult instanceof Success success) {
               if (success.swingSource() == SwingSource.CLIENT) {
                  mc.player.swingHand(interactionHand);
               }

               return true;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   public static boolean interactOnBlock(BlockPos pos, boolean swing) {
      if (mc.player != null && mc.world != null) {
         Vec3d eyePos = mc.player.getEntityPos().add(0.0, mc.player.getStandingEyeHeight(), 0.0);
         return interactOnBlock(pos, eyePos, swing);
      } else {
         return false;
      }
   }

   public static boolean interactOnBlock(BlockPos pos, Vec3d eyePos, boolean swing) {
      if (mc.world == null) {
         return false;
      } else {
         BlockState blockState = mc.world.getBlockState(pos);
         Box blockAABB = blockState.getOutlineShape(mc.world, pos).getBoundingBox();
         Vec3d center = new Vec3d(
            (blockAABB.minX + blockAABB.maxX) * 0.5 + pos.getX(),
            (blockAABB.minY + blockAABB.maxY) * 0.5 + pos.getY(),
            (blockAABB.minZ + blockAABB.maxZ) * 0.5 + pos.getZ()
         );
         BlockHitResult result = RotationUtils.collisionRayTrace(pos, blockAABB, eyePos, center);
         if (result == null) {
            return false;
         } else {
            SwapManager.sendBlockC08(result.getPos(), result.getSide(), swing, true);
            return true;
         }
      }
   }

   public static boolean interactOnBlock(BlockPos pos, Vec3d eyePos, Vec3d hit, boolean swing) {
      if (mc.world == null) {
         return false;
      } else {
         BlockState blockState = mc.world.getBlockState(pos);
         Box blockAABB = blockState.getOutlineShape(mc.world, pos).getBoundingBox();
         BlockHitResult result = RotationUtils.collisionRayTrace(pos, blockAABB, eyePos, hit);
         if (result == null) {
            return false;
         } else {
            SwapManager.sendBlockC08(result.getPos(), result.getSide(), swing, true);
            return true;
         }
      }
   }

   public static boolean attackEntity(Entity entity) {
      if (mc.player != null && mc.world != null && mc.interactionManager != null) {
         ItemStack itemStack = mc.player.getStackInHand(Hand.MAIN_HAND);
         if (!itemStack.isItemEnabled(mc.world.getEnabledFeatures())) {
            return false;
         } else {
            mc.interactionManager.attackEntity(mc.player, entity);
            mc.player.swingHand(Hand.MAIN_HAND);
            return true;
         }
      } else {
         return false;
      }
   }

   public static void breakBlock(Pos pos, boolean remove, boolean sync) {
      if (!(faceDistance(pos.asVec3(), mc.player.getEntityPos().add(0.0, mc.player.getEyeHeight(mc.player.getPose()), 0.0)) > 25.0)) {
         Direction dir = closestFace(pos.asVec3(), mc.player.getEyePos());
         PacketOrderManager.register(PacketOrderManager.STATE.ATTACK, () -> {
            BlockPos bp = pos.asBlockPos();
            SwapManager.sendC07(bp, Action.START_DESTROY_BLOCK, dir, true, sync);
            if (remove) {
               mc.world.setBlockState(bp, Blocks.AIR.getDefaultState(), 0);
            }
         });
      }
   }

   public static double faceDistance(Vec3d pos, Vec3d player) {
      double minDist = Double.MAX_VALUE;

      for (Direction face : Direction.values()) {
         double offsetX = 0.0;
         double offsetY = 0.0;
         double offsetZ = 0.0;
         switch (face) {
            case DOWN:
               offsetY = -0.5;
               break;
            case UP:
               offsetY = 0.5;
               break;
            case NORTH:
               offsetZ = -0.5;
               break;
            case SOUTH:
               offsetZ = 0.5;
               break;
            case WEST:
               offsetX = -0.5;
               break;
            case EAST:
               offsetX = 0.5;
         }

         Vec3d faceVec = pos.add(0.5 + offsetX, 0.5 + offsetY, 0.5 + offsetZ);
         double dist = player.squaredDistanceTo(faceVec);
         if (dist < minDist) {
            minDist = dist;
         }
      }

      return minDist;
   }

   public static Vec3d getFaceVec(Direction direction, Vec3d pos) {
      double offsetX = 0.0;
      double offsetY = 0.0;
      double offsetZ = 0.0;
      switch (direction) {
         case DOWN:
            offsetY = -0.5;
            break;
         case UP:
            offsetY = 0.5;
            break;
         case NORTH:
            offsetZ = -0.5;
            break;
         case SOUTH:
            offsetZ = 0.5;
            break;
         case WEST:
            offsetX = -0.5;
            break;
         case EAST:
            offsetX = 0.5;
      }

      return pos.add(0.5 + offsetX, 0.5 + offsetY, 0.5 + offsetZ);
   }

   public static Direction closestFace(Vec3d pos, Vec3d player) {
      double minDist = Double.MAX_VALUE;
      Direction closest = Direction.UP;

      for (Direction face : Direction.values()) {
         double offsetX = 0.0;
         double offsetY = 0.0;
         double offsetZ = 0.0;
         switch (face) {
            case DOWN:
               offsetY = -0.5;
               break;
            case UP:
               offsetY = 0.5;
               break;
            case NORTH:
               offsetZ = -0.5;
               break;
            case SOUTH:
               offsetZ = 0.5;
               break;
            case WEST:
               offsetX = -0.5;
               break;
            case EAST:
               offsetX = 0.5;
         }

         Vec3d faceVec = pos.add(0.5 + offsetX, 0.5 + offsetY, 0.5 + offsetZ);
         double dist = player.squaredDistanceTo(faceVec);
         if (dist < minDist) {
            minDist = dist;
            closest = face;
         }
      }

      return closest;
   }

   private InteractUtils() {
      throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
   }
}
