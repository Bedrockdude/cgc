package com.ricedotwho.rsa.component.impl.pathfinding;

import com.ricedotwho.rsa.RSA;
import com.ricedotwho.rsm.component.impl.map.map.Room;
import com.ricedotwho.rsm.component.impl.map.map.UniqueRoom;
import com.ricedotwho.rsm.component.impl.map.utils.ScanUtils;
import java.util.HashMap;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.client.network.ClientPlayerEntity;

public class GoalDungeonRoom implements Goal {
   private static final float MAX = 1.0E8F;
   private final UniqueRoom endRoom;
   private final HashMap<String, RoomCandidate> rooms;

   public GoalDungeonRoom(UniqueRoom endRoom, List<RoomCandidate> rooms) {
      this.endRoom = endRoom;
      this.rooms = new HashMap<>(rooms.size());

      for (int i = 0; i < rooms.size(); i++) {
         RoomCandidate candidate = rooms.get(i);
         this.rooms.put(candidate.getName(), candidate);
      }
   }

   public static GoalDungeonRoom create(UniqueRoom endRoom) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player == null) {
         return null;
      } else {
         Room startRoom = ScanUtils.getRoomFromPos(player.getBlockX(), player.getBlockZ());
         if (startRoom != null && endRoom != null) {
            List<RoomCandidate> candidates = DungeonMapPathfinder.solve(startRoom, (Room)endRoom.getTiles().getFirst());
            if (candidates.isEmpty()) {
               RSA.chat("Failed to find path!");
               return null;
            } else {
               return new GoalDungeonRoom(endRoom, candidates);
            }
         } else {
            RSA.chat("Room is not loaded!");
            return null;
         }
      }
   }

   @Override
   public boolean test(int x, int y, int z) {
      Room current = ScanUtils.getRoomFromPos(x, z);
      if (current != null && current.getUniqueRoom() != null) {
         return current.getUniqueRoom() != this.endRoom
            ? false
            : current.getRoofHeight() > y && MathHelper.abs(current.getX() - x) <= 14.0F && MathHelper.abs(current.getZ() - z) <= 14.0F;
      } else {
         return false;
      }
   }

   @Override
   public double heuristic(int x, int y, int z) {
      Room room = ScanUtils.getRoomFromPos(x, z);
      if (room != null && room.getUniqueRoom() != null && room.getUniqueRoom().getMainRoom() != null) {
         RoomCandidate candidate = this.rooms.get(room.getData().name());
         if (candidate == null) {
            return 1.0E8;
         } else {
            boolean bl = candidate.getNextDoorRoom() != null;
            if (bl) {
               int endX = candidate.getDoorRoom().getX() + candidate.getNextDoorRoom().getX() >> 1;
               int endZ = candidate.getDoorRoom().getZ() + candidate.getNextDoorRoom().getZ() >> 1;
               int xDif = x - endX;
               int zDif = z - endZ;
               return xDif * xDif + zDif * zDif + candidate.getCost();
            } else {
               return 0.0;
            }
         }
      } else {
         return 1.0E8;
      }
   }

   @Override
   public boolean isPossible() {
      return this.endRoom != null;
   }
}
