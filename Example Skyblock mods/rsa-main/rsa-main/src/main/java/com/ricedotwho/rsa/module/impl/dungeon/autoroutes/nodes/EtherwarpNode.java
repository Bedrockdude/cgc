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
import com.ricedotwho.rsm.utils.EtherUtils;
import com.ricedotwho.rsm.utils.FileUtils;
import com.ricedotwho.rsm.utils.render.render3d.type.Line;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EntityPose;
import net.minecraft.client.network.ClientPlayerEntity;

public class EtherwarpNode extends Node {
   protected final Pos localTarget;
   protected Pos realTargetPos;

   public EtherwarpNode(Pos localPos, Pos localTargetPos, AwaitManager awaits, boolean start) {
      super(localPos, awaits, start);
      this.localTarget = localTargetPos;
      this.realTargetPos = null;
   }

   @Override
   public void calculate(UniqueRoom room) {
      super.calculate(room);
      this.realTargetPos = RoomUtils.getRealPosition(this.localTarget, room.getMainRoom());
   }

   @Override
   public boolean run(Pos playerPos) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player == null) {
         return this.cancel();
      } else {
         KeyBinding.unpressAll();
         if (!SwapManager.reserveSwap(Items.DIAMOND_SHOVEL)) {
            return this.cancel();
         } else if (!MinecraftClient.getInstance().player.getLastPlayerInput().sneak()) {
            return this.cancel();
         } else {
            Pos playerCopy = playerPos.add(0.0, 1.54F, 0.0);
            Pos targetDirection = this.realTargetPos.subtract(playerCopy);
            Pos targetDeltaCopy = targetDirection.copy();
            boolean swap = SwapManager.isDesynced();
            PacketOrderManager.register(
               PacketOrderManager.STATE.ITEM_USE,
               () -> {
                  if ((!swap || SwapManager.checkClientItem(Items.DIAMOND_SHOVEL)) && (swap || SwapManager.checkServerItem(Items.DIAMOND_SHOVEL))) {
                     float[] angles = EtherUtils.getYawAndPitch(targetDeltaCopy.x, targetDeltaCopy.y, targetDeltaCopy.z);
                     if (!SwapManager.sendAirC08(angles[0], angles[1], swap, false)) {
                        RSA.chat("Failed to send ether C08!");
                     }
                  } else {
                     RSA.chat(
                        "Big fuck up! : "
                           + swap
                           + ", "
                           + MinecraftClient.getInstance().player.getInventory().getStack(SwapManager.getServerSlot()).getItem()
                     );
                  }
               }
            );
            targetDirection.normalize();
            BlockPos etherPos = this.realTargetPos.add(targetDirection.multiply(0.001F)).asBlockPos();
            playerPos.x = etherPos.getX() + 0.5;
            playerPos.y = etherPos.getY() + 1.05;
            playerPos.z = etherPos.getZ() + 0.5;
            return true;
         }
      }
   }

   @Override
   public void render(boolean depth) {
      Vec3d playerRealPos = this.getRealPos().asVec3();
      Colour colour = this.getColour();
      Renderer3D.addTask(new Ring(playerRealPos, depth, this.getRadius(), colour));
      Renderer3D.addTask(new Line(playerRealPos, this.realTargetPos.asVec3(), colour, colour, true));
   }

   @Override
   public int getPriority() {
      return 5;
   }

   @Override
   public String getName() {
      return "etherwarp";
   }

   @Override
   public Colour getColour() {
      return this.isStart() ? AutoRoutes.getStartColour().getValue() : AutoRoutes.getEtherwarpColour().getValue();
   }

   @Override
   public JsonObject serialize() {
      JsonObject json = super.serialize();
      json.add("localTarget", FileUtils.getGson().toJsonTree(this.localTarget));
      return json;
   }

   public static EtherwarpNode supply(UniqueRoom fullRoom, ClientPlayerEntity player, AwaitManager awaits, boolean start) {
      Vec3d target = EtherUtils.rayTraceBlock(
         61,
         player.getYaw(),
         player.getPitch(),
         player.getEntityPos().add(0.0, AutoRoutes.getUse1_8Height().getValue() ? 1.54F : player.getEyeHeight(EntityPose.CROUCHING), 0.0)
      );
      if (target == null) {
         return null;
      } else {
         Room mainRoom = fullRoom.getMainRoom();
         Pos playerRelative = RoomUtils.getRelativePosition(new Pos(player.getEntityPos()), mainRoom);
         Pos targetRelative = RoomUtils.getRelativePosition(new Pos(target), mainRoom);
         return new EtherwarpNode(playerRelative, targetRelative, awaits, start);
      }
   }

   public Pos getRealTargetPos() {
      return this.realTargetPos;
   }
}
