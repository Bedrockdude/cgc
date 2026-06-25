package com.ricedotwho.rsa.module.impl.dungeon.autoroutes.awaits;

import com.google.gson.JsonObject;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.AwaitCondition;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.AwaitType;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.Node;

public class AwaitClick extends AwaitCondition<Boolean> {
   private boolean clicked = false;

   public AwaitClick() {
      super(AwaitType.CLICK);
   }

   @Override
   public boolean test(Node node) {
      return this.clicked;
   }

   @Override
   public void onEnter() {
      this.clicked = false;
   }

   @Override
   public void reset() {
   }

   protected void consume(Boolean bl) {
      this.clicked = bl;
   }

   @Override
   public void serialize(JsonObject json) {
      json.addProperty(this.getType().getName(), true);
   }
}
