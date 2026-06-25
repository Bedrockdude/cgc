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
import com.ricedotwho.rsm.utils.EtherUtils;
import com.ricedotwho.rsm.utils.FileUtils;
import com.ricedotwho.rsm.utils.ItemUtils;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EntityPose;
import net.minecraft.client.network.ClientPlayerEntity;

public class UseNode extends Node {
   private final Pos rotationVec;
   private final String itemID;
   private boolean sneak;
   private Pos realRotationVector;

   public UseNode(Pos localPos, Pos localRotationVector, String itemID, boolean sneak, AwaitManager awaits, boolean start) {
      super(localPos, awaits, start);
      this.rotationVec = localRotationVector;
      this.itemID = itemID;
      this.sneak = sneak;
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
         autoRoutes.setForceSneak(!this.sneak);
         if (!SwapManager.reserveSwap(this.itemID)) {
            return this.cancel();
         } else if (MinecraftClient.getInstance().player.getLastPlayerInput().sneak() != this.sneak) {
            return this.cancel();
         } else {
            boolean swap = SwapManager.isDesynced();
            PacketOrderManager.register(
               PacketOrderManager.STATE.ITEM_USE,
               () -> {
                  if ((!swap || SwapManager.checkClientItem(this.itemID)) && (swap || SwapManager.checkServerItem(this.itemID))) {
                     float[] angles = EtherUtils.getYawAndPitch(this.realRotationVector.x, this.realRotationVector.y, this.realRotationVector.z);
                     if (!SwapManager.sendAirC08(angles[0], angles[1], swap, false)) {
                        RSA.chat("Failed to send use C08!");
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
            playerPos.selfAdd(0.0, player.getEyeHeight(EntityPose.STANDING), 0.0).selfAdd(this.realRotationVector.multiply(12.0));
            autoRoutes.setForceSneak(!this.sneak);
            return true;
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
      return "use";
   }

   @Override
   public Colour getColour() {
      return this.isStart() ? AutoRoutes.getStartColour().getValue() : AutoRoutes.getUseColour().getValue();
   }

   @Override
   public JsonObject serialize() {
      JsonObject json = super.serialize();
      json.add("rotationVec", FileUtils.getGson().toJsonTree(this.rotationVec));
      json.addProperty("itemID", this.itemID);
      json.addProperty("sneak", this.sneak);
      return json;
   }

   public static UseNode supply(UniqueRoom fullRoom, ClientPlayerEntity player, AwaitManager awaits, boolean start) {
      Room mainRoom = fullRoom.getMainRoom();
      Pos playerRelative = RoomUtils.getRelativePosition(new Pos(player.getEntityPos()), mainRoom);
      Pos targetRelative = RoomUtils.rotateRelativeFixed(new Pos(player.getRotationVec(1.0F)), fullRoom.getRotation());
      String itemID = ItemUtils.getID(MinecraftClient.getInstance().player.getInventory().getSelectedStack());
      return itemID.isBlank() ? null : new UseNode(playerRelative, targetRelative, itemID, false, awaits, start);
   }

   public void setSneak(boolean sneak) {
      this.sneak = sneak;
   }
}
