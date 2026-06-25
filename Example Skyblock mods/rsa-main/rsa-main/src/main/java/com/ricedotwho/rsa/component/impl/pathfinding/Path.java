package com.ricedotwho.rsa.component.impl.pathfinding;

import com.mojang.datafixers.util.Function5;
import java.util.function.Consumer;
import net.minecraft.util.math.BlockPos;

public class Path {
   private final BlockPos start;
   private final PathNode startNode;
   private final PathNode endNode;
   private final Goal goal;

   public Path(BlockPos start, PathNode startNode, PathNode endNode, Goal goal) {
      this.start = start;
      this.startNode = startNode;
      this.endNode = endNode;
      this.goal = goal;
   }

   public BlockPos getStart() {
      return this.start;
   }

   public PathNode getStartNode() {
      return this.startNode;
   }

   public PathNode getEndNode() {
      return this.endNode;
   }

   public int length() {
      int count = 0;

      for (PathNode node = this.endNode; node.getParent() != null; node = node.getParent()) {
         count++;
      }

      return count;
   }

   public <T> int consumeNodes(Consumer<T> consumer, Function5<BlockPos, Float, Float, Boolean, Integer, T> provider, int sequenceStart) {
      PathNode node = this.getEndNode();
      PathNode last = null;

      for (boolean isLast = true; node != null; node = node.getParent()) {
         if (last != null) {
            consumer.accept((T)provider.apply(node.getPos(), last.getYaw(), last.getPitch(), isLast, sequenceStart++));
            isLast = false;
         }

         last = node;
      }

      return sequenceStart;
   }

   public Goal getGoal() {
      return this.goal;
   }
}
