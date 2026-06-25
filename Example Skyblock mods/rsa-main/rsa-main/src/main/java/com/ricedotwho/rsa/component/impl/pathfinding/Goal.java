package com.ricedotwho.rsa.component.impl.pathfinding;

import net.minecraft.util.math.BlockPos;

public interface Goal {
   boolean test(int x, int y, int z);

   default boolean test(BlockPos pos) {
      return this.test(pos.getX(), pos.getY(), pos.getZ());
   }

   double heuristic(int x, int y, int z);

   default double heuristic(BlockPos pos) {
      return this.heuristic(pos.getX(), pos.getY(), pos.getZ());
   }

   default boolean isPossible() {
      return true;
   }
}
