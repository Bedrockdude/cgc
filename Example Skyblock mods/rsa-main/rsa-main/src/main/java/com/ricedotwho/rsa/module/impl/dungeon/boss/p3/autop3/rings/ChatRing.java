package com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.rings;

import com.google.gson.JsonObject;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.AutoP3;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.RingType;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.args.ArgumentManager;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.subactions.SubActionManager;
import com.ricedotwho.rsm.data.Colour;
import com.ricedotwho.rsm.data.MutableInput;
import com.ricedotwho.rsm.data.Pos;
import com.ricedotwho.rsm.utils.Accessor;
import java.util.Map;
import net.minecraft.util.PlayerInput;

public class ChatRing extends Ring implements Accessor {
   private final String message;

   @Override
   public RingType getType() {
      return RingType.CHAT;
   }

   public ChatRing(Pos min, Pos max, ArgumentManager manage, SubActionManager actions, Map<String, Object> extra) {
      this(min, max, (String)extra.get("message"), manage, actions);
   }

   public ChatRing(Pos min, Pos max, String message, ArgumentManager manage, SubActionManager actions) {
      super(min, max, RingType.CHAT.getRenderSizeOffset(), manage, actions);
      this.message = message;
   }

   @Override
   public boolean run() {
      mc.getNetworkHandler().sendChatMessage(this.message);
      return true;
   }

   @Override
   public Colour getColour() {
      return Colour.YELLOW;
   }

   @Override
   public int getPriority() {
      return 50;
   }

   @Override
   public boolean tick(MutableInput mutableInput, PlayerInput input, AutoP3 autoP3) {
      return true;
   }

   @Override
   public JsonObject serialize() {
      JsonObject obj = super.serialize();
      obj.addProperty("message", this.message);
      return obj;
   }

   @Override
   public void feedback() {
      AutoP3.modMessage("Chatting");
   }
}
