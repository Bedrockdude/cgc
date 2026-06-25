package com.ricedotwho.rsa.component.impl.pathfinding;

import java.util.HashMap;

public class CachedPath extends Path {
   HashMap<Integer, PathNode> cache = new HashMap<>();

   public CachedPath(PathNode endNode) {
      super(null, null, endNode, null);
      this.updateCache();
   }

   public void updateCache() {
      this.cache.clear();

      for (PathNode node = this.getEndNode(); node != null; node = node.getParent()) {
         this.cache.put(node.getIndex(), node);
      }
   }

   public PathNode getByIndex(int index) {
      return this.cache.get(index);
   }
}
