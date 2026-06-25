package com.ricedotwho.rsa.module.impl.dungeon.autoroutes.nodes;

import com.google.gson.JsonObject;
import com.ricedotwho.rsa.RSA;
import com.ricedotwho.rsa.component.impl.managers.PacketOrderManager;
import com.ricedotwho.rsa.component.impl.managers.SwapManager;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.AutoRoutes;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.AwaitManager;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.Node;
import com.ricedotwho.rsa.utils.render3d.type.Ring;
import com.ricedotwho.rsm.component.impl.Renderer3D;
import com.ricedotwho.rsm.component.impl.map.map.Room;
import com.ricedotwho.rsm.component.impl.map.map.UniqueRoom;
import com.ricedotwho.rsm.component.impl.map.utils.RoomUtils;
import com.ricedotwho.rsm.data.Colour;
import com.ricedotwho.rsm.data.Pos;
import com.ricedotwho.rsm.utils.FileUtils;
import com.ricedotwho.rsm.utils.RotationUtils;
import com.ricedotwho.rsm.utils.render.render3d.type.FilledOutlineBox;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.block.BlockState;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.hit.HitResult.Type;

public class BoomNode extends Node {
   private final Pos target;
   private Pos realTargetPosition;
   private Box renderAABB;

   public BoomNode(Pos localPos, Pos target, AwaitManager awaits, boolean start) {
      super(localPos, awaits, start);
      this.target = target;
      this.realTargetPosition = null;
      this.renderAABB = null;
   }

   @Override
   public void calculate(UniqueRoom room) {
      super.calculate(room);
      this.realTargetPosition = RoomUtils.getRealPosition(this.target, room.getMainRoom());
      this.renderAABB = new Box(
         this.realTargetPosition.x - 0.1F,
         this.realTargetPosition.y - 0.1F,
         this.realTargetPosition.z - 0.1F,
         this.realTargetPosition.x + 0.1F,
         this.realTargetPosition.y + 0.1F,
         this.realTargetPosition.z + 0.1F
      );
   }

   @Override
   public boolean run(Pos playerPos) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null && MinecraftClient.getInstance().world != null) {
         KeyBinding.unpressAll();
         if (!SwapManager.reserveSwap("INFINITE_SUPERBOOM_TNT", "SUPERBOOM_TNT")) {
            return this.cancel();
         } else {
            Vec3d eyePos = MinecraftClient.getInstance().player.getEntityPos().add(0.0, 1.54F, 0.0);
            Vec3d targetVec = this.realTargetPosition.asVec3();
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
                           RSA.chat("Failed to find block hit result!");
                        } else {
                           SwapManager.sendBlockC08(result, swap, false);
                        }
                     }
                  }
               }
            );
            return false;
         }
      } else {
         return this.cancel();
      }
   }

   @Override
   public void render(boolean depth) {
      Colour c = AutoRoutes.getBoomColour().getValue();
      Renderer3D.addTask(
         new Ring(new Vec3d(this.getRealPos().x, this.getRealPos().y + 0.2F, this.getRealPos().z), depth, this.getRadius(), this.getColour())
      );
      Renderer3D.addTask(new FilledOutlineBox(this.renderAABB, c.brighter(), c.darker(), true));
   }

   @Override
   public int getPriority() {
      return 20;
   }

   @Override
   public String getName() {
      return "boom";
   }

   @Override
   public Colour getColour() {
      return this.isStart() ? AutoRoutes.getStartColour().getValue() : AutoRoutes.getBoomColour().getValue();
   }

   @Override
   public JsonObject serialize() {
      JsonObject json = super.serialize();
      json.add("target", FileUtils.getGson().toJsonTree(this.target));
      return json;
   }

   public static BoomNode supply(UniqueRoom fullRoom, ClientPlayerEntity player, AwaitManager awaits, boolean start) {
      Room mainRoom = fullRoom.getMainRoom();
      Pos playerRelative = RoomUtils.getRelativePosition(new Pos(player.getEntityPos()), mainRoom);
      if (MinecraftClient.getInstance().crosshairTarget instanceof BlockHitResult blockHitResult && blockHitResult.getType() != Type.MISS) {
         Vec3d eyePos = player.getEntityPos().add(0.0, 1.54F, 0.0);
         Vec3d dir = blockHitResult.getPos().subtract(eyePos).normalize().multiply(0.001F);
         Pos pos = new Pos(blockHitResult.getPos());
         pos.selfAdd(dir.x, dir.y, dir.z);
         return new BoomNode(playerRelative, RoomUtils.getRelativePosition(pos, mainRoom), awaits, start);
      } else {
         return null;
      }
   }
}
