package com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.args.type;

import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.args.Argument;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.args.RingArgType;

public class TermCloseArg extends Argument<Boolean> {
   private boolean closed = false;

   public TermCloseArg() {
      super(RingArgType.TERM);
   }

   @Override
   public boolean check() {
      return this.closed;
   }

   public void consume(Boolean event) {
      this.closed = true;
   }

   @Override
   public void reset() {
      this.closed = false;
   }

   @Override
   public String stringValue() {
      return "term close";
   }

   public static TermCloseArg create(String ignored) {
      return new TermCloseArg();
   }
}
