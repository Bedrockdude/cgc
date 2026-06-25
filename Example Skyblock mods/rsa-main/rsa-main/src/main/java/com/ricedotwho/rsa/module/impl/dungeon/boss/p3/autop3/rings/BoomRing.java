package com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.rings;

import com.google.gson.JsonObject;
import com.ricedotwho.rsa.component.impl.managers.PacketOrderManager;
import com.ricedotwho.rsa.component.impl.managers.SwapManager;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.AutoP3;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.RingType;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.args.ArgumentManager;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.subactions.SubActionManager;
import com.ricedotwho.rsm.data.Colour;
import com.ricedotwho.rsm.data.MutableInput;
import com.ricedotwho.rsm.data.Pos;
import com.ricedotwho.rsm.utils.FileUtils;
import com.ricedotwho.rsm.utils.RotationUtils;
import java.util.Map;
import net.minecraft.util.PlayerInput;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;

public class BoomRing extends Ring {
   private final Pos target;

   public BoomRing(Pos min, Pos max, Pos target, ArgumentManager manager, SubActionManager actions) {
      super(min, max, RingType.BOOM.getRenderSizeOffset(), manager, actions);
      this.target = target;
   }

   public BoomRing(Pos min, Pos max, ArgumentManager manager, SubActionManager actions, Map<String, Object> ignored) {
      super(min, max, RingType.BOOM.getRenderSizeOffset(), manager, actions);
      if (MinecraftClient.getInstance().crosshairTarget instanceof BlockHitResult blockHitResult && blockHitResult.getType() != Type.MISS) {
         Vec3d eyePos = mc.player.getEntityPos().add(0.0, mc.player.getEyeHeight(mc.player.getPose()), 0.0);
         Vec3d dir = blockHitResult.getPos().subtract(eyePos).normalize().multiply(0.001F);
         this.target = new Pos(blockHitResult.getPos());
         this.target.selfAdd(dir.x, dir.y, dir.z);
      } else {
         this.target = null;
      }
   }

   @Override
   public RingType getType() {
      return RingType.BOOM;
   }

   @Override
   public boolean run() {
      if (!SwapManager.reserveSwap("INFINITE_SUPERBOOM_TNT", "SUPERBOOM_TNT")) {
         return false;
      } else {
         Vec3d eyePos = mc.player.getEntityPos().add(0.0, mc.player.getEyeHeight(mc.player.getPose()), 0.0);
         Vec3d targetVec = this.target.asVec3();
         boolean swap = SwapManager.isDesynced();
         PacketOrderManager.register(
            PacketOrderManager.STATE.ITEM_USE,
            () -> {
               BlockPos blockPos = BlockPos.ofFloored(targetVec);
               BlockState blockState = MinecraftClient.getInstance().world.getBlockState(blockPos);
               if (blockState.getBlock() != Blocks.AIR) {
                  VoxelShape voxelShape = blockState.getOutlineShape(MinecraftClient.getInstance().world, blockPos);
                  if (!voxelShape.isEmpty()) {
                     Box blockAABB = voxelShape.getBoundingBox();
                     Vec3d center = new Vec3d(
                        (blockAABB.minX + blockAABB.maxX) * 0.5 + blockPos.getX(),
                        (blockAABB.minY + blockAABB.maxY) * 0.5 + blockPos.getY(),
                        (blockAABB.minZ + blockAABB.maxZ) * 0.5 + blockPos.getZ()
                     );
                     BlockHitResult result = RotationUtils.collisionRayTrace(blockPos, blockAABB, eyePos, center);
                     if (result == null) {
                        AutoP3.modMessage("Failed to find block hit result!");
                     } else {
                        SwapManager.sendBlockC08(result, swap, false);
                     }
                  }
               }
            }
         );
         return true;
      }
   }

   @Override
   public Colour getColour() {
      return Colour.RED;
   }

   @Override
   public int getPriority() {
      return 60;
   }

   @Override
   public boolean tick(MutableInput mutableInput, PlayerInput input, AutoP3 autoP3) {
      return true;
   }

   @Override
   public JsonObject serialize() {
      JsonObject obj = super.serialize();
      obj.add("target", FileUtils.getGson().toJsonTree(this.target));
      return obj;
   }

   @Override
   public void feedback() {
      AutoP3.modMessage("Booming");
   }
}
