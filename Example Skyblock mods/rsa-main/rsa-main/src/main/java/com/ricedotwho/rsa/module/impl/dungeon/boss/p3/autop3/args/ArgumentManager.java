package com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.args;

import com.google.gson.JsonObject;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

public record ArgumentManager(HashMap<RingArgType, Argument<?>> args) {
   public ArgumentManager() {
      this(new HashMap<>());
   }

   public boolean check() {
      for (Argument<?> arg : this.args.values()) {
         if (!arg.check()) {
            return true;
         }
      }

      return false;
   }

   public Collection<Argument<?>> getArgs() {
      return this.args.values();
   }

   public void addArg(Argument<?> argument) {
      this.args.put(argument.getType(), argument);
   }

   public <T> void consume(Class<? extends Argument<T>> clazz, T value) {
      Argument<T> argument = this.getArg((Class<Argument<T>>)clazz);
      if (argument != null) {
         argument.consume(value);
      }
   }

   public <T extends Argument<?>> T getArg(Class<T> clazz) {
      Argument<?> arg = this.args.get(RingArgType.byClass(clazz));
      return arg == null ? null : clazz.cast(arg);
   }

   public JsonObject serialize() {
      JsonObject obj = new JsonObject();

      for (Argument<?> arg : this.args.values()) {
         arg.serialize(obj);
      }

      return obj;
   }

   public boolean has(RingArgType type) {
      return this.args.containsKey(type);
   }

   public void reset() {
      this.getArgs().forEach(Argument::reset);
   }

   public String getList(String before) {
      if (this.args.isEmpty()) {
         return "";
      } else {
         StringBuilder sb = new StringBuilder();
         sb.append(before);
         if (!before.isBlank()) {
            sb.append(", ");
         } else {
            sb.append("(");
         }

         List<Argument<?>> args = this.getArgs().stream().toList();

         for (int i = 0; i < args.size(); i++) {
            Argument<?> arg = args.get(i);
            boolean last = i == args.size() - 1;
            sb.append(arg.stringValue());
            if (!last) {
               sb.append(", ");
            }
         }

         sb.append(")");
         return sb.toString();
      }
   }
}
