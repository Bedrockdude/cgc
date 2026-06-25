package com.ricedotwho.rsa.module.impl.dungeon.autoroutes.nodes;

import com.ricedotwho.rsa.RSA;
import com.ricedotwho.rsa.component.impl.managers.PacketOrderManager;
import com.ricedotwho.rsa.component.impl.managers.SwapManager;
import com.ricedotwho.rsa.module.impl.dungeon.DynamicRoutes;
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
import com.ricedotwho.rsm.utils.render.render3d.type.Line;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

public class DynamicEtherwarpNode extends Node {
   private final float yaw;
   private final float pitch;
   private final boolean await;
   private final int priority;
   private Vec3d target;

   public DynamicEtherwarpNode(Pos localPos, float yaw, float pitch, boolean await, int priority, AwaitManager awaits, boolean start) {
      super(localPos, null, false);
      this.yaw = yaw;
      this.pitch = pitch;
      this.await = await;
      this.priority = priority;
   }

   public DynamicEtherwarpNode(Pos localPos, float yaw, float pitch, boolean await, int priority) {
      this(localPos, yaw, pitch, await, priority, null, false);
   }

   @Override
   public boolean shouldAwait() {
      return this.await;
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
            boolean swap = SwapManager.isDesynced();
            PacketOrderManager.register(
               PacketOrderManager.STATE.ITEM_USE,
               () -> {
                  if ((!swap || SwapManager.checkClientItem(Items.DIAMOND_SHOVEL)) && (swap || SwapManager.checkServerItem(Items.DIAMOND_SHOVEL))) {
                     if (!SwapManager.sendAirC08(this.yaw, this.pitch, swap, false)) {
                        RSA.chat("Failed to send dyn ether C08!");
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
            BlockPos etherPos = EtherUtils.fastGetEtherFromOrigin(playerCopy.asVec3(), this.yaw, this.pitch, 61);
            if (etherPos == null) {
               return false;
            } else {
               playerPos.x = etherPos.getX() + 0.5;
               playerPos.y = etherPos.getY() + 1.05;
               playerPos.z = etherPos.getZ() + 0.5;
               return true;
            }
         }
      }
   }

   @Override
   public void calculate(UniqueRoom room) {
      this.realPos = this.localPos;
      Vec3d origin = this.localPos.add(0.0, 1.5899999618530274, 0.0).asVec3();
      this.target = EtherUtils.rayTraceBlock(61, this.yaw, this.pitch, origin);
      if (this.target == null) {
         BlockPos pos = EtherUtils.fastGetEtherFromOrigin(origin, this.yaw, this.pitch, 61);
         if (pos == null) {
            this.target = Vec3d.ZERO;
            return;
         }

         this.target = pos.toCenterPos();
      }
   }

   @Override
   public int getPriority() {
      return this.priority;
   }

   @Override
   public String getName() {
      return "dynamicEther";
   }

   @Override
   public void render(boolean depth) {
      Vec3d position = this.getRealPos().asVec3();
      Colour colour = this.getColour();
      Renderer3D.addTask(new Ring(position, depth, this.getRadius(), colour));
      Renderer3D.addTask(new Line(position, this.target, colour, colour, true));
   }

   @Override
   public Colour getColour() {
      return DynamicRoutes.getNodeColor().getValue();
   }

   public static DynamicEtherwarpNode fromBlockPos(BlockPos pos, float yaw, float pitch, boolean await, int priority) {
      Pos nodePos = new Pos(pos.toBottomCenterPos()).selfAdd(0.0, 1.0, 0.0);
      return new DynamicEtherwarpNode(nodePos, yaw, pitch, await, Integer.MAX_VALUE - priority);
   }

   public static DynamicEtherwarpNode supply(UniqueRoom fullRoom, ClientPlayerEntity player) {
      Room mainRoom = fullRoom.getMainRoom();
      Pos playerRelative = RoomUtils.getRelativePosition(new Pos(player.getEntityPos()), mainRoom);
      return new DynamicEtherwarpNode(playerRelative, player.getYaw(), player.getPitch(), false, 0);
   }
}
