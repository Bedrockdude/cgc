package com.ricedotwho.rsa.module.impl.dungeon.autoroutes;

import com.google.gson.JsonObject;
import com.google.gson.annotations.Expose;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;

public class AwaitManager {
   @Expose
   private final HashMap<AwaitType, AwaitCondition<?>> awaits;

   public AwaitManager(HashMap<AwaitType, AwaitCondition<?>> awaits) {
      this.awaits = awaits;
   }

   public AwaitManager(Collection<AwaitCondition<?>> awaits) {
      if (awaits.isEmpty()) {
         this.awaits = null;
      } else {
         this.awaits = new HashMap<>();

         for (AwaitCondition<?> await : awaits) {
            this.awaits.putIfAbsent(await.getType(), await);
         }
      }
   }

   public AwaitManager(AwaitCondition<?>... conditions) {
      if (conditions != null && conditions.length >= 1) {
         this.awaits = new HashMap<>();

         for (AwaitCondition<?> await : conditions) {
            this.awaits.putIfAbsent(await.getType(), await);
         }
      } else {
         this.awaits = null;
      }
   }

   public void onEnterNode() {
      this.getAwaits().forEach(AwaitCondition::onEnter);
   }

   protected void resetAwaits() {
      this.getAwaits().forEach(AwaitCondition::reset);
   }

   public boolean shouldAwait(Node node) {
      return this.hasAwaits() && this.awaits.values().stream().anyMatch(await -> !await.test(node));
   }

   public Collection<AwaitCondition<?>> getAwaits() {
      return (Collection<AwaitCondition<?>>)(this.awaits == null ? Collections.emptyList() : this.awaits.values());
   }

   public boolean hasAwaits() {
      return this.awaits != null && !this.awaits.isEmpty();
   }

   public boolean hasAwait(AwaitType type) {
      return this.awaits.containsKey(type);
   }

   public <T> void consume(Class<? extends AwaitCondition<T>> clzz, T value) {
      AwaitCondition<T> condition = this.getAwait((Class<AwaitCondition<T>>)clzz);
      if (condition != null) {
         condition.consume(value);
      }
   }

   public <T extends AwaitCondition<?>> T getAwait(Class<T> clazz) {
      AwaitCondition<?> await = this.awaits.get(AwaitType.byClass(clazz));
      return await == null ? null : clazz.cast(await);
   }

   public JsonObject serialize() {
      JsonObject obj = new JsonObject();

      for (AwaitCondition<?> await : this.awaits.values()) {
         await.serialize(obj);
      }

      return obj;
   }
}
