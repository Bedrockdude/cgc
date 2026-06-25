package com.ricedotwho.rsa.component.impl.pathfinding.openset;

import com.ricedotwho.rsa.component.impl.pathfinding.PathNode;
import java.util.Arrays;

public final class BinaryHeapOpenSet implements IOpenSet {
   private static final int INITIAL_CAPACITY = 1024;
   private PathNode[] array;
   private int size = 0;
   private final float nodeCost;

   public BinaryHeapOpenSet(float nodeCost) {
      this(1024, nodeCost);
   }

   public BinaryHeapOpenSet(int size, float nodeCost) {
      this.array = new PathNode[size];
      this.nodeCost = nodeCost;
   }

   public int size() {
      return this.size;
   }

   @Override
   public final void insert(PathNode value) {
      if (this.size >= this.array.length - 1) {
         this.array = Arrays.copyOf(this.array, this.array.length << 1);
      }

      this.size++;
      value.heapPosition = this.size;
      this.array[this.size] = value;
      this.update(value);
   }

   @Override
   public final void update(PathNode val) {
      int index = val.heapPosition;
      int parentInd = index >>> 1;
      double cost = val.getCost(this.nodeCost);

      for (PathNode parentNode = this.array[parentInd]; index > 1 && parentNode.getCost(this.nodeCost) > cost; parentNode = this.array[parentInd]) {
         this.array[index] = parentNode;
         this.array[parentInd] = val;
         val.heapPosition = parentInd;
         parentNode.heapPosition = index;
         index = parentInd;
         parentInd >>>= 1;
      }
   }

   @Override
   public final boolean isEmpty() {
      return this.size == 0;
   }

   @Override
   public final PathNode removeLowest() {
      if (this.size == 0) {
         throw new IllegalStateException();
      } else {
         PathNode result = this.array[1];
         PathNode val = this.array[this.size];
         this.array[1] = val;
         val.heapPosition = 1;
         this.array[this.size] = null;
         this.size--;
         result.heapPosition = -1;
         if (this.size < 2) {
            return result;
         } else {
            int index = 1;
            int smallerChild = 2;
            double cost = val.getCost(this.nodeCost);

            do {
               PathNode smallerChildNode = this.array[smallerChild];
               double smallerChildCost = smallerChildNode.getCost(this.nodeCost);
               if (smallerChild < this.size) {
                  PathNode rightChildNode = this.array[smallerChild + 1];
                  double rightChildCost = rightChildNode.getCost(this.nodeCost);
                  if (smallerChildCost > rightChildCost) {
                     smallerChild++;
                     smallerChildCost = rightChildCost;
                     smallerChildNode = rightChildNode;
                  }
               }

               if (cost <= smallerChildCost) {
                  break;
               }

               this.array[index] = smallerChildNode;
               this.array[smallerChild] = val;
               val.heapPosition = smallerChild;
               smallerChildNode.heapPosition = index;
               index = smallerChild;
            } while ((smallerChild <<= 1) <= this.size);

            return result;
         }
      }
   }
}
