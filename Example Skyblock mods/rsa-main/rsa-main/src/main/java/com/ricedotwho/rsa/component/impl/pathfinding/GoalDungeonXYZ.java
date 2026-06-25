package com.ricedotwho.rsa.component.impl.pathfinding;

import com.ricedotwho.rsa.RSA;
import com.ricedotwho.rsm.component.impl.map.map.Room;
import com.ricedotwho.rsm.component.impl.map.utils.ScanUtils;
import com.ricedotwho.rsm.utils.EtherUtils;
import java.util.HashMap;
import java.util.List;
import net.minecraft.util.math.BlockPos;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

public class GoalDungeonXYZ implements Goal {
   public static final float ROOM_COST = 100000.0F;
   private static final float MAX = 1.0E8F;
   private final BlockPos endPos;
   private final HashMap<String, RoomCandidate> rooms;

   public GoalDungeonXYZ(BlockPos endPos, List<RoomCandidate> rooms) {
      this.endPos = endPos;
      this.rooms = new HashMap<>(rooms.size());

      for (int i = 0; i < rooms.size(); i++) {
         RoomCandidate candidate = rooms.get(i);
         this.rooms.put(candidate.getName(), candidate);
      }
   }

   public static GoalDungeonXYZ create(BlockPos endPos) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player == null) {
         return null;
      } else {
         Room startRoom = ScanUtils.getRoomFromPos(player.getBlockX(), player.getBlockZ());
         Room endRoom = ScanUtils.getRoomFromPos(endPos.getX(), endPos.getZ());
         if (startRoom != null && endRoom != null) {
            List<RoomCandidate> candidates = DungeonMapPathfinder.solve(startRoom, endRoom);
            if (candidates.isEmpty()) {
               RSA.chat("Failed to find path!");
               return null;
            } else {
               return new GoalDungeonXYZ(endPos, candidates);
            }
         } else {
            RSA.chat("Room is not loaded!");
            return null;
         }
      }
   }

   @Override
   public boolean test(int x, int y, int z) {
      return x == this.endPos.getX() && y == this.endPos.getY() && z == this.endPos.getZ();
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
            int endX;
            int endY;
            int endZ;
            if (bl) {
               endX = candidate.getDoorRoom().getX() + candidate.getNextDoorRoom().getX() >> 1;
               endY = y;
               endZ = candidate.getDoorRoom().getZ() + candidate.getNextDoorRoom().getZ() >> 1;
            } else {
               endX = this.endPos.getX();
               endY = this.endPos.getY();
               endZ = this.endPos.getZ();
            }

            int xDif = x - endX;
            int yDif = y - endY;
            int zDif = z - endZ;
            return xDif * xDif + yDif * yDif + zDif * zDif + candidate.getCost();
         }
      } else {
         return 1.0E8;
      }
   }

   @Override
   public boolean isPossible() {
      return EtherUtils.isValidEtherwarpPosition(this.endPos);
   }
}
