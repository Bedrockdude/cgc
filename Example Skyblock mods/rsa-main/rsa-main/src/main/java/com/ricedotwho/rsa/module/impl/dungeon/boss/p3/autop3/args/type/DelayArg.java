package com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.args.type;

import com.google.gson.JsonObject;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.args.Argument;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.args.RingArgType;

public class DelayArg extends Argument<Object> {
   private final long delay;
   private long ran = 0L;

   public DelayArg(long delay) {
      super(RingArgType.DELAY);
      this.delay = delay;
   }

   @Override
   public boolean check() {
      long ago = System.currentTimeMillis() - this.ran;
      if (this.ran != 0L && ago <= this.delay + 200L) {
         return ago >= this.delay;
      } else {
         this.ran = System.currentTimeMillis();
         return false;
      }
   }

   @Override
   public void consume(Object event) {
   }

   @Override
   public void reset() {
      this.ran = 0L;
   }

   public static DelayArg create(String obj) {
      return new DelayArg(Long.parseLong(obj));
   }

   @Override
   public void serialize(JsonObject json) {
      json.addProperty(this.getType().name(), this.delay);
   }

   @Override
   public String stringValue() {
      return "delay " + this.delay;
   }
}
