package com.ricedotwho.rsa.component.impl.pathfinding;

import com.ricedotwho.rsm.utils.EtherUtils;
import net.minecraft.util.math.BlockPos;

public class GoalXYZ implements Goal {
   private final BlockPos endPos;

   public GoalXYZ(BlockPos endPos) {
      this.endPos = endPos;
   }

   @Override
   public boolean test(int x, int y, int z) {
      return x == this.endPos.getX() && y == this.endPos.getY() && z == this.endPos.getZ();
   }

   @Override
   public double heuristic(int x, int y, int z) {
      int xDif = x - this.endPos.getX();
      int yDif = y - this.endPos.getY();
      int zDif = z - this.endPos.getZ();
      return xDif * xDif + yDif * yDif + zDif * zDif;
   }

   @Override
   public boolean isPossible() {
      return EtherUtils.isValidEtherwarpPosition(this.endPos);
   }
}
