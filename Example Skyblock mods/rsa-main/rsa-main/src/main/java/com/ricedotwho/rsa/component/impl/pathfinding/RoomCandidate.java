package com.ricedotwho.rsa.component.impl.pathfinding;

import com.ricedotwho.rsm.component.impl.map.map.Room;
import com.ricedotwho.rsm.component.impl.map.map.UniqueRoom;

public class RoomCandidate {
   private final UniqueRoom uniqueRoom;
   private final int index;
   private final float cost;
   private final Room doorRoom;
   private final Room nextDoorRoom;

   public RoomCandidate(UniqueRoom uniqueRoom, Room doorRoom, Room nextDoorRoom, int index) {
      this.uniqueRoom = uniqueRoom;
      this.doorRoom = doorRoom;
      this.index = index;
      this.cost = index * 100000.0F;
      this.nextDoorRoom = nextDoorRoom;
   }

   public String getName() {
      return this.uniqueRoom.getName();
   }

   public UniqueRoom getUniqueRoom() {
      return this.uniqueRoom;
   }

   public int getIndex() {
      return this.index;
   }

   public float getCost() {
      return this.cost;
   }

   public Room getDoorRoom() {
      return this.doorRoom;
   }

   public Room getNextDoorRoom() {
      return this.nextDoorRoom;
   }
}
