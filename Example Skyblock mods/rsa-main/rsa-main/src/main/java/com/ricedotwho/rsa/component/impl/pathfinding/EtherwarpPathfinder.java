package com.ricedotwho.rsa.component.impl.pathfinding;

import com.ricedotwho.rsa.RSA;
import com.ricedotwho.rsa.component.impl.pathfinding.openset.BinaryHeapOpenSet;
import com.ricedotwho.rsm.data.Pair;
import com.ricedotwho.rsm.utils.EtherUtils;
import java.util.HashMap;
import java.util.HashSet;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.apache.logging.log4j.util.TriConsumer;

public class EtherwarpPathfinder {
   public static final double MIN_IMPROVEMENT = 1.0;
   private final Goal goal;
   private final PathfindingCalculationContext context;
   private boolean solved = false;
   private Path path;
   private PathNode bestNode;
   private CachedPath bestCachedPath;
   private final BinaryHeapOpenSet nodes;
   private final HashSet<Integer> processing;
   private final HashMap<Long, PathNode> cache;

   public EtherwarpPathfinder(PathfindingCalculationContext context, Goal goal) {
      this.goal = goal;
      this.context = context;
      this.cache = new HashMap<>();
      this.nodes = new BinaryHeapOpenSet(context.newNodeCost());
      this.processing = new HashSet<>();
   }

   public Path calculate() {
      if (this.solved) {
         return this.path;
      } else if (!this.goal.isPossible()) {
         RSA.chat("Goal is impossible!");
         return null;
      } else {
         long time = System.currentTimeMillis();
         PathNode startNode = new PathNode(this.context.startBlock(), null, this.goal);
         startNode.setYaw(Float.MAX_VALUE);
         startNode.setPitch(Float.MAX_VALUE);
         this.nodes.insert(startNode);
         this.bestNode = startNode;

         for (int i = 0; i < this.context.threadCount() - 1; i++) {
            Thread thread = new Thread(this::run);
            thread.start();
         }

         this.run();
         RSA.chat("Found path! Took " + (System.currentTimeMillis() - time) + "ms!");
         this.path = new Path(this.context.startBlock(), startNode, this.bestNode, this.goal);
         this.solved = true;
         return this.path;
      }
   }

   private void run() {
      while (!this.isComplete()) {
         this.checkNode(this.getLowest());
      }
   }

   private void checkNode(PathNode checkNode) {
      if (checkNode != null) {
         double moveCost = checkNode.getMoveCost(this.context.newNodeCost());
         if (this.goal.test(checkNode.getPos())) {
            RSA.chat("Found valid route length " + checkNode.getIndex());
            if (!this.isComplete() || moveCost < this.getBestNodeMoveCost()) {
               this.setBestNode(checkNode);
            }
         }

         if (this.isComplete() && moveCost >= this.getBestNodeMoveCost()) {
            this.finishNode(checkNode);
         } else if (this.isComplete() && checkNode.getHeuristicCost() >= this.getBestHeuristicByIndex(checkNode.getIndex()) * this.context.heuristicThreshold()
            )
          {
            this.finishNode(checkNode);
         } else {
            double newCost = moveCost + this.context.newNodeCost();
            this.consumeRaycastBlocks(
               checkNode,
               (neighborNode, yaw, pitch) -> {
                  if (!neighborNode.hasBeenScanned() || neighborNode.getMoveCost(this.context.newNodeCost()) - newCost > 1.0) {
                     neighborNode.updateParent(checkNode);
                     neighborNode.setYaw(yaw);
                     neighborNode.setPitch(pitch);
                     if (neighborNode.isOpen()) {
                        this.updateNodes(neighborNode);
                     } else {
                        this.insertNodes(neighborNode);
                     }

                     if (!this.isComplete()
                        && this.getBestNodeHeuristic() - neighborNode.getHeuristicCost() > 1.0
                        && neighborNode.getMoveCost(this.context.newNodeCost()) < this.getBestNodeMoveCost()) {
                        this.setBestNode(neighborNode);
                     }
                  }
               }
            );
            this.finishNode(checkNode);
         }
      }
   }

   public void cancel() {
      this.solved = true;
   }

   private synchronized void updateNodes(PathNode node) {
      this.nodes.update(node);
   }

   private synchronized void insertNodes(PathNode node) {
      this.nodes.insert(node);
   }

   private synchronized double getBestHeuristicByIndex(int index) {
      PathNode node = this.bestCachedPath.getByIndex(index);
      return node == null ? Double.MAX_VALUE : node.getHeuristicCost();
   }

   private synchronized PathNode getLowest() {
      if (this.nodes.isEmpty()) {
         return null;
      } else {
         PathNode lowest = this.nodes.removeLowest();
         this.processing.add(lowest.hashCode());
         return lowest;
      }
   }

   private synchronized boolean isDone() {
      return this.nodes.isEmpty() && this.processing.isEmpty();
   }

   private synchronized void finishNode(PathNode node) {
      if (this.processing.contains(node.hashCode())) {
         this.processing.remove(node.hashCode());
      }
   }

   private synchronized boolean isComplete() {
      return this.solved;
   }

   private synchronized double getBestNodeMoveCost() {
      return this.bestNode.getMoveCost(this.context.newNodeCost());
   }

   private synchronized double getBestNodeHeuristic() {
      return this.bestNode.getHeuristicCost();
   }

   private synchronized void setBestNode(PathNode node) {
      if (this.solved) {
         if (node.getMoveCost(this.context.newNodeCost()) < this.bestNode.getMoveCost(this.context.newNodeCost())) {
            this.bestNode = node;
            this.bestCachedPath = new CachedPath(node);
         }
      } else if (this.goal.test(node.getPos())) {
         this.bestNode = node;
         this.bestCachedPath = new CachedPath(node);
         this.solved = true;
      } else {
         if (this.bestNode.getHeuristicCost() - node.getHeuristicCost() > 1.0
            && this.bestNode.getMoveCost(this.context.newNodeCost()) < this.getBestNodeMoveCost()) {
            this.bestNode = node;
         }
      }
   }

   public synchronized PathNode getNodeAt(BlockPos pos, long hashcode, PathNode parent) {
      PathNode node = this.cache.get(hashcode);
      if (node == null) {
         node = new PathNode(pos, parent, this.goal);
         this.cache.put(hashcode, node);
      }

      return node;
   }

   private void consumeRaycastBlocks(PathNode parent, TriConsumer<PathNode, Float, Float> consumer) {
      HashSet<Integer> blockPosCache = new HashSet<>();
      Vec3d eyePos = new Vec3d(parent.getPos().getX() + 0.5, parent.getPos().getY() + 1.05 + 1.54F, parent.getPos().getZ() + 0.5);
      float pitch = -90.0F;

      while (pitch <= 90.0F) {
         float pitchRadians = (float)Math.toRadians(pitch);
         float yawStepAtThisPitch = this.context.yawStep() / Math.max(0.01F, (float)Math.cos(pitchRadians));

         for (float yaw = 0.0F; yaw < 360.0F; yaw += yawStepAtThisPitch) {
            Pair<BlockPos, Boolean> prediction = EtherUtils.getEtherPosFromOrigin(eyePos, yaw, pitch, 61);
            BlockPos etherPos = prediction.getFirst();
            if (Boolean.TRUE.equals(prediction.getSecond()) && etherPos != null) {
               int hash = PathNode.hashCode(etherPos);
               if (blockPosCache.add(hash)) {
                  PathNode node = this.getNodeAt(etherPos, hash, parent);
                  consumer.accept(node, yaw, pitch);
               }
            }
         }

         pitch += this.context.pitchStep();
      }
   }
}
