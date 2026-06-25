package com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3;

import java.util.Arrays;

public enum CenterType {
   POS("pos"),
   ANGLES("angles"),
   ALL("all"),
   YAW("yaw"),
   PITCH("pitch");

   private final String name;

   private CenterType(String name) {
      this.name = name;
   }

   public static CenterType fromName(String name) {
      return Arrays.stream(values()).filter(c -> c.getName().equals(name)).findFirst().orElse(null);
   }

   public String getName() {
      return this.name;
   }
}
