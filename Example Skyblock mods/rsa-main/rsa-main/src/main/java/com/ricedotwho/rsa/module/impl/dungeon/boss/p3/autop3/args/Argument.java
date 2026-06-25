package com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.args;

import com.google.gson.JsonObject;
import com.ricedotwho.rsm.utils.Accessor;

public abstract class Argument<T> implements Accessor {
   private final RingArgType type;

   public abstract boolean check();

   public abstract void consume(T value);

   public abstract void reset();

   public void serialize(JsonObject json) {
      json.addProperty(this.getType().name(), true);
   }

   public abstract String stringValue();

   public Argument(RingArgType type) {
      this.type = type;
   }

   public RingArgType getType() {
      return this.type;
   }
}
