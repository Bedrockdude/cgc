package com.ricedotwho.rsa.module.impl.dungeon.autoroutes.nodes;

import com.google.gson.JsonObject;
import com.ricedotwho.rsa.RSA;
import com.ricedotwho.rsa.component.impl.managers.PacketOrderManager;
import com.ricedotwho.rsa.component.impl.managers.SwapManager;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.AutoRoutes;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.AwaitManager;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.Node;
import com.ricedotwho.rsa.utils.render3d.type.Ring;
import com.ricedotwho.rsm.RSM;
import com.ricedotwho.rsm.component.impl.Renderer3D;
import com.ricedotwho.rsm.component.impl.map.map.Room;
import com.ricedotwho.rsm.component.impl.map.map.UniqueRoom;
import com.ricedotwho.rsm.component.impl.map.utils.RoomUtils;
import com.ricedotwho.rsm.data.Colour;
import com.ricedotwho.rsm.data.Pos;
import com.ricedotwho.rsm.utils.Accessor;
import com.ricedotwho.rsm.utils.EtherUtils;
import com.ricedotwho.rsm.utils.FileUtils;
import com.ricedotwho.rsm.utils.ItemUtils;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

public class AotvNode extends Node implements Accessor {
   private final Pos rotationVec;
   private Pos realRotationVector;

   public AotvNode(Pos localPos, Pos localRotationVector, AwaitManager awaits, boolean start) {
      super(localPos, awaits, start);
      this.rotationVec = localRotationVector;
      this.realRotationVector = null;
   }

   @Override
   public void calculate(UniqueRoom room) {
      super.calculate(room);
      this.realRotationVector = RoomUtils.rotateRealFixed(this.rotationVec, room.getRotation());
   }

   @Override
   public boolean run(Pos playerPos) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player == null) {
         return this.cancel();
      } else {
         KeyBinding.unpressAll();
         AutoRoutes autoRoutes = (AutoRoutes)RSM.getModule(AutoRoutes.class);
         autoRoutes.setForceSneak(true);
         if (!SwapManager.reserveSwap(Items.DIAMOND_SHOVEL)) {
            return this.cancel();
         } else if (MinecraftClient.getInstance().player.getLastPlayerInput().sneak()) {
            return this.cancel();
         } else {
            float[] angles = EtherUtils.getYawAndPitch(this.realRotationVector.x, this.realRotationVector.y, this.realRotationVector.z);
            boolean swap = SwapManager.isDesynced();
            PacketOrderManager.register(
               PacketOrderManager.STATE.ITEM_USE,
               () -> {
                  if ((!swap || SwapManager.checkClientItem(Items.DIAMOND_SHOVEL)) && (swap || SwapManager.checkServerItem(Items.DIAMOND_SHOVEL))) {
                     if (!SwapManager.sendAirC08(angles[0], angles[1], swap, false)) {
                        RSA.chat("Failed to send ether C08!");
                     } else {
                        autoRoutes.setForceSneak(false);
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
            int slot = SwapManager.getItemSlot(Items.DIAMOND_SHOVEL);
            if (slot == -1) {
               return false;
            } else {
               Pos prediction = EtherUtils.predictTeleport(
                  8 + ItemUtils.getTunerDistance(mc.player.getInventory().getStack(slot)), playerPos, angles[0], angles[1]
               );
               if (prediction == null) {
                  return false;
               } else {
                  playerPos.set(prediction);
                  return true;
               }
            }
         }
      }
   }

   @Override
   public void render(boolean depth) {
      Vec3d playerRealPos = this.getRealPos().asVec3();
      Renderer3D.addTask(new Ring(playerRealPos.add(0.0, 0.1, 0.0), depth, this.getRadius(), this.getColour()));
   }

   @Override
   public int getPriority() {
      return 8;
   }

   @Override
   public String getName() {
      return "aotv";
   }

   @Override
   public Colour getColour() {
      return this.isStart() ? AutoRoutes.getStartColour().getValue() : AutoRoutes.getAotvColour().getValue();
   }

   @Override
   public JsonObject serialize() {
      JsonObject json = super.serialize();
      json.add("rotationVec", FileUtils.getGson().toJsonTree(this.rotationVec));
      return json;
   }

   public static AotvNode supply(UniqueRoom fullRoom, ClientPlayerEntity player, AwaitManager awaits, boolean start) {
      Room mainRoom = fullRoom.getMainRoom();
      Pos playerRelative = RoomUtils.getRelativePosition(new Pos(player.getEntityPos()), mainRoom);
      Pos targetRelative = RoomUtils.rotateRelativeFixed(new Pos(player.getRotationVec(1.0F)), fullRoom.getRotation());
      return new AotvNode(playerRelative, targetRelative, awaits, start);
   }
}
