package com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.args.type;

import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.args.Argument;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.args.RingArgType;
import com.ricedotwho.rsm.event.impl.game.TerminalEvent.Open;

public class TermArg extends Argument<Open> {
   private boolean inTerm = false;

   public TermArg() {
      super(RingArgType.TERM);
   }

   @Override
   public boolean check() {
      return this.inTerm;
   }

   public void consume(Open event) {
      this.inTerm = true;
   }

   @Override
   public void reset() {
      this.inTerm = false;
   }

   @Override
   public String stringValue() {
      return "term";
   }

   public static TermArg create(String ignored) {
      return new TermArg();
   }
}
