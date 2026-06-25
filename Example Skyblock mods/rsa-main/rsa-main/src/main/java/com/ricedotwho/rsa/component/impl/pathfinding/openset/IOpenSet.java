package com.ricedotwho.rsa.component.impl.pathfinding.openset;

import com.ricedotwho.rsa.component.impl.pathfinding.PathNode;

public interface IOpenSet {
   void insert(PathNode node);

   boolean isEmpty();

   PathNode removeLowest();

   void update(PathNode node);
}
