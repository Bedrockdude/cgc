package com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.args.type;

import com.google.gson.JsonObject;
import com.ricedotwho.rsa.RSA;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.args.Argument;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.args.RingArgType;
import com.ricedotwho.rsm.component.impl.map.handler.Dungeon;
import com.ricedotwho.rsm.utils.NumberUtils;
import net.minecraft.util.math.MathHelper;

public class LeapArg extends Argument<Boolean> {
   private final int players;
   private boolean override = false;

   public LeapArg(int players) {
      super(RingArgType.LEAP);
      this.players = MathHelper.clamp(players, 0, 5);
   }

   @Override
   public boolean check() {
      RSA.chat("override: %s, leap: %s, needed %s", this.override, Dungeon.getPlayersLeapt(), this.players);
      if (this.override) {
         this.override = false;
         return true;
      } else {
         return Dungeon.getPlayersLeapt() >= this.players;
      }
   }

   public void consume(Boolean bl) {
      this.override = true;
   }

   @Override
   public void reset() {
      this.override = false;
   }

   public static LeapArg create(String arg) {
      return new LeapArg(NumberUtils.isInteger(arg) ? Integer.parseInt(arg) : 1);
   }

   @Override
   public void serialize(JsonObject json) {
      json.addProperty(this.getType().name(), this.players);
   }

   @Override
   public String stringValue() {
      return "leap " + this.players;
   }
}
