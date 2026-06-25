package com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.args;

import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.args.type.DelayArg;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.args.type.GroundArg;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.args.type.LeapArg;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.args.type.TermArg;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.args.type.TermCloseArg;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.args.type.TriggerArg;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

public enum RingArgType {
   TERM(TermArg::create, TermArg.class, List.of("term")),
   LEAP(LeapArg::create, LeapArg.class, List.of("leap")),
   GROUND(GroundArg::create, GroundArg.class, List.of("ground", "g")),
   TRIGGER(TriggerArg::create, TriggerArg.class, List.of("trigger", "click", "c")),
   DELAY(DelayArg::create, DelayArg.class, List.of("delay", "d")),
   TERM_CLOSE(TermCloseArg::create, TermCloseArg.class, List.of("termclose", "close", "tc"));

   private final Function<String, Argument<?>> factory;
   private final List<String> aliases;
   private Class<? extends Argument<?>> clazz;

   private RingArgType(Function<String, Argument<?>> factory, Class<? extends Argument<?>> clazz, List<String> aliases) {
      this.factory = factory;
      this.clazz = clazz;
      this.aliases = aliases;
   }

   public Argument create(String arg) {
      return this.factory.apply(arg);
   }

   public static RingArgType fromAliases(String string) {
      for (RingArgType type : values()) {
         if (type.getAliases().contains(string)) {
            return type;
         }
      }

      return null;
   }

   public static RingArgType byClass(Class<? extends Argument<?>> clazz) {
      return Arrays.stream(values()).filter(n -> n.getClazz().equals(clazz)).findAny().orElse(null);
   }

   public List<String> getAliases() {
      return this.aliases;
   }

   public Class<? extends Argument<?>> getClazz() {
      return this.clazz;
   }
}
