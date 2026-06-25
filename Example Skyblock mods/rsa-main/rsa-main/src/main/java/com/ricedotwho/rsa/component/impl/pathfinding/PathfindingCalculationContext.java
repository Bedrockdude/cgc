package com.ricedotwho.rsa.component.impl.pathfinding;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.Mutable;

public record PathfindingCalculationContext(Mutable startBlock, int threadCount, float yawStep, float pitchStep, float newNodeCost, float heuristicThreshold) {
   public static PathfindingCalculationContext simple(BlockPos startBlock, int threadCount) {
      return new PathfindingCalculationContext(startBlock.mutableCopy(), threadCount, 2.0F, 2.0F, 100.0F, 0.5F);
   }

   public Mutable getMutableStart() {
      return this.startBlock;
   }
}
