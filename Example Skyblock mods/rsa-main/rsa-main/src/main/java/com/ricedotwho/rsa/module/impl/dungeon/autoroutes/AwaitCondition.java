package com.ricedotwho.rsa.module.impl.dungeon.autoroutes;

import com.google.gson.JsonObject;

public abstract class AwaitCondition<T> {
   private final AwaitType type;

   // Polled while the node is active to determine whether this await has completed.
   public abstract boolean test(Node node);

   // Receives external progress signals (e.g. clicks/secrets) from packet/input hooks.
   protected abstract void consume(T value);

   // Called once when entering a node that owns this await.
   public abstract void onEnter();

   // Clears all await-local state.
   public abstract void reset();

   public abstract void serialize(JsonObject json);

   public AwaitCondition(AwaitType type) {
      this.type = type;
   }

   public AwaitType getType() {
      return this.type;
   }
}
