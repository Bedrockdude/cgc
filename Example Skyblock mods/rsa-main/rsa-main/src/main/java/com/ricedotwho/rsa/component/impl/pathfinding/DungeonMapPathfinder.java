package com.ricedotwho.rsa.component.impl.pathfinding;

import com.ricedotwho.rsa.RSA;
import com.ricedotwho.rsm.component.impl.map.handler.DungeonInfo;
import com.ricedotwho.rsm.component.impl.map.map.Door;
import com.ricedotwho.rsm.component.impl.map.map.Room;
import com.ricedotwho.rsm.component.impl.map.map.UniqueRoom;
import com.ricedotwho.rsm.data.Pair;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import net.minecraft.util.math.MathHelper;

public class DungeonMapPathfinder {
   private DungeonMapPathfinder() {
   }

   public static List<RoomCandidate> solve(Room start, Room end) {
      PriorityQueue<DungeonMapPathfinder.RoomNode> queue = new PriorityQueue<>();
      Set<DungeonMapPathfinder.RoomNode> closedSet = new HashSet<>();
      Pair<Integer, Integer> endPos = end.getArrayPosition();
      DungeonMapPathfinder.RoomNode startNode = new DungeonMapPathfinder.RoomNode(
         start, null, (Integer)endPos.getFirst() >> 1, (Integer)endPos.getSecond() >> 1
      );
      queue.add(startNode);

      while (!queue.isEmpty()) {
         DungeonMapPathfinder.RoomNode current = queue.poll();
         if (current.room == end) {
            return reconstructPath(current);
         }

         closedSet.add(current);

         for (DungeonMapPathfinder.RoomNode neighbor : current.getNeighbors()) {
            if (!closedSet.contains(neighbor) && !queue.contains(neighbor)) {
               queue.add(neighbor);
            }
         }
      }

      return Collections.emptyList();
   }

   private static List<RoomCandidate> reconstructPath(DungeonMapPathfinder.RoomNode roomNode) {
      List<RoomCandidate> path = new ArrayList<>();
      UniqueRoom lastUnique = null;
      Room lastRoom = null;

      for (int lastIndex = roomNode.index; roomNode != null; roomNode = roomNode.parent) {
         UniqueRoom currentUnique = roomNode.getRoom().getUniqueRoom();
         if (currentUnique == null) {
            RSA.chat("Failed to find room path! Not loaded!");
            break;
         }

         Room nextDoorRoom = null;
         if (lastUnique != currentUnique) {
            nextDoorRoom = lastRoom;
         }

         if (nextDoorRoom != null || roomNode.index == lastIndex) {
            path.add(new RoomCandidate(roomNode.getRoom().getUniqueRoom(), roomNode.getRoom(), nextDoorRoom, lastIndex - roomNode.index));
         }

         lastRoom = roomNode.room;
         lastUnique = lastRoom.getUniqueRoom();
      }

      Collections.reverse(path);
      return path;
   }

   private static class RoomNode implements Comparable<DungeonMapPathfinder.RoomNode> {
      private static final int ROOM_COST = 1;
      private final Room room;
      private DungeonMapPathfinder.RoomNode parent;
      private int index;
      private final int endX;
      private final int endZ;
      private final int x;
      private final int z;

      public RoomNode(Room room, DungeonMapPathfinder.RoomNode parent, int endX, int endZ) {
         this.room = room;
         this.parent = parent;
         this.index = parent == null ? 0 : parent.index + 1;
         this.endX = endX;
         this.endZ = endZ;
         Pair<Integer, Integer> arrayPos = this.room.getArrayPosition();
         this.x = (Integer)arrayPos.getFirst() >> 1;
         this.z = (Integer)arrayPos.getSecond() >> 1;
      }

      public int getMoveCost() {
         return this.index;
      }

      public int getCost() {
         return this.getMoveCost() + this.heuristic();
      }

      public int heuristic() {
         return MathHelper.abs(this.x - this.endX) + MathHelper.abs(this.z - this.endZ);
      }

      public List<DungeonMapPathfinder.RoomNode> getNeighbors() {
         List<DungeonMapPathfinder.RoomNode> neighbors = new ArrayList<>();
         int[][] directions = new int[][]{{0, 1}, {1, 0}, {0, -1}, {-1, 0}};

         for (int[] d : directions) {
            int newX = this.x + d[0];
            int newZ = this.z + d[1];
            int doorX = this.x * 2 + d[0];
            int doorZ = this.z * 2 + d[1];
            if (newX >= 0
               && newZ >= 0
               && newX < 6
               && newZ < 6
               && doorX >= 0
               && doorZ >= 0
               && doorX < 11
               && doorZ < 11
               && DungeonInfo.getDungeonList()[newX * 2 + newZ * 22] instanceof Room newRoom
               && (newRoom.getUniqueRoom() == this.room.getUniqueRoom()
                  || DungeonInfo.getDungeonList()[doorX + doorZ * 11] instanceof Door)) {
               neighbors.add(new DungeonMapPathfinder.RoomNode(newRoom, this, this.endX, this.endZ));
            }
         }

         return neighbors;
      }

      public int compareTo(DungeonMapPathfinder.RoomNode other) {
         return Integer.compare(this.getCost(), other.getCost());
      }

      @Override
      public boolean equals(Object obj) {
         return obj instanceof DungeonMapPathfinder.RoomNode other ? other.room == this.room : false;
      }

      @Override
      public int hashCode() {
         return this.room.getCore();
      }

      public Room getRoom() {
         return this.room;
      }

      public DungeonMapPathfinder.RoomNode getParent() {
         return this.parent;
      }

      public int getIndex() {
         return this.index;
      }

      public int getEndX() {
         return this.endX;
      }

      public int getEndZ() {
         return this.endZ;
      }

      public int getX() {
         return this.x;
      }

      public int getZ() {
         return this.z;
      }
   }
}
