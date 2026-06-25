package com.ricedotwho.rsa.module.impl.dungeon.autoroutes.nodes;

import com.google.gson.JsonObject;
import com.ricedotwho.rsa.component.impl.managers.PacketOrderManager;
import com.ricedotwho.rsa.component.impl.managers.SwapManager;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.AutoRoutes;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.AwaitManager;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.Node;
import com.ricedotwho.rsa.utils.render3d.type.Ring;
import com.ricedotwho.rsm.component.impl.Renderer3D;
import com.ricedotwho.rsm.component.impl.map.Map;
import com.ricedotwho.rsm.component.impl.map.map.Room;
import com.ricedotwho.rsm.component.impl.map.map.UniqueRoom;
import com.ricedotwho.rsm.component.impl.map.utils.RoomUtils;
import com.ricedotwho.rsm.data.Colour;
import com.ricedotwho.rsm.data.Pos;
import com.ricedotwho.rsm.utils.ItemUtils;
import com.ricedotwho.rsm.utils.Utils;
import net.minecraft.entity.passive.BatEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.network.ClientPlayerEntity;

public class BatNode extends Node {
   private final float yaw;
   private final float pitch;

   public BatNode(Pos localPos, float yaw, float pitch, AwaitManager awaits, boolean start) {
      super(localPos, awaits, start);
      this.yaw = yaw;
      this.pitch = pitch;
   }

   @Override
   public boolean run(Pos playerPos) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null && MinecraftClient.getInstance().world != null && Map.getCurrentRoom() != null && Map.getCurrentRoom().getUniqueRoom() != null) {
         KeyBinding.unpressAll();
         if (!SwapManager.reserveSwap(BatNode::isWitherBlade) && !SwapManager.reserveSwap(Items.ALLIUM)) {
            return this.cancel();
         } else if (!this.hasBatNear(playerPos, MinecraftClient.getInstance().world)) {
            return this.cancel();
         } else {
            boolean swap = SwapManager.isDesynced();
            PacketOrderManager.register(PacketOrderManager.STATE.ITEM_USE, () -> SwapManager.sendAirC08(this.yaw, this.pitch, swap, false));
            return false;
         }
      } else {
         return this.cancel();
      }
   }

   private boolean hasBatNear(Pos player, ClientWorld level) {
      Vec3d playerPos = player.asVec3();
      Box aabb = new Box(playerPos, playerPos).expand(10.0, 10.0, 10.0);
      return level.getNonSpectatingEntities(BatEntity.class, aabb).stream().anyMatch(bat -> bat.squaredDistanceTo(playerPos) < 100.0);
   }

   private static boolean isWitherBlade(ItemStack itemStack) {
      if (itemStack == null) {
         return false;
      } else {
         String sbId = ItemUtils.getID(itemStack);
         return sbId.isEmpty()
            ? false
            : Utils.equalsOneOf(sbId, new Object[]{"NECRON_BLADE", "SCYLLA", "HYPERION", "VALKYRIE", "ASTRAEA"})
               && ItemUtils.getCustomData(itemStack).getListOrEmpty("ability_scroll").size() == 3;
      }
   }

   @Override
   public void render(boolean depth) {
      Renderer3D.addTask(
         new Ring(new Vec3d(this.getRealPos().x, this.getRealPos().y + 0.3F, this.getRealPos().z), depth, this.getRadius(), this.getColour())
      );
   }

   @Override
   public int getPriority() {
      return 16;
   }

   @Override
   public String getName() {
      return "bat";
   }

   @Override
   public Colour getColour() {
      return this.isStart() ? AutoRoutes.getStartColour().getValue() : AutoRoutes.getBatColour().getValue();
   }

   @Override
   public JsonObject serialize() {
      JsonObject json = super.serialize();
      json.addProperty("yaw", this.yaw);
      json.addProperty("pitch", this.pitch);
      return json;
   }

   public static BatNode supply(UniqueRoom fullRoom, ClientPlayerEntity player, AwaitManager awaits, boolean start) {
      Room mainRoom = fullRoom.getMainRoom();
      Pos playerRelative = RoomUtils.getRelativePosition(new Pos(player.getEntityPos()), mainRoom);
      return new BatNode(playerRelative, 0.0F, 90.0F, awaits, start);
   }
}
