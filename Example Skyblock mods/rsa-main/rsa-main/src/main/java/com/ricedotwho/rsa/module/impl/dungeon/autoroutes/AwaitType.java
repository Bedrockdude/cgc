package com.ricedotwho.rsa.module.impl.dungeon.autoroutes;

import com.google.common.reflect.TypeToken;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.awaits.AwaitClick;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.awaits.AwaitEWRaytrace;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.awaits.AwaitSecrets;
import java.lang.reflect.Type;
import java.util.Arrays;

public enum AwaitType {
   CLICK(AwaitClick.class, "awaitClick", (new TypeToken<AwaitClick>() {}).getType()),
   SECRETS(AwaitSecrets.class, "awaitSecrets", (new TypeToken<AwaitSecrets>() {}).getType()),
   ETHERWARP_TRACE(AwaitEWRaytrace.class, "awaitEWRaytrace", (new TypeToken<AwaitEWRaytrace>() {}).getType());

   private final Class<? extends AwaitCondition<?>> clazz;
   private final Type type;
   private final String name;

   private AwaitType(Class<? extends AwaitCondition<?>> s, String name, Type type) {
      this.clazz = s;
      this.type = type;
      this.name = name;
   }

   public static AwaitType byClass(Class<? extends AwaitCondition<?>> clazz) {
      return Arrays.stream(values()).filter(n -> n.getClazz().equals(clazz)).findAny().orElse(null);
   }

   public static AwaitType byName(String name) {
      return Arrays.stream(values()).filter(n -> n.getName().equalsIgnoreCase(name)).findAny().orElse(null);
   }

   public Class<? extends AwaitCondition<?>> getClazz() {
      return this.clazz;
   }

   public Type getType() {
      return this.type;
   }

   public String getName() {
      return this.name;
   }
}
