package com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.args.Argument;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.args.ArgumentManager;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.args.RingArgType;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.args.type.DelayArg;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.args.type.GroundArg;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.args.type.LeapArg;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.args.type.TermArg;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.args.type.TermCloseArg;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.args.type.TriggerArg;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.rings.AlignRing;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.rings.BlinkRing;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.rings.BonzoRing;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.rings.BoomRing;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.rings.ChatRing;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.rings.CommandRing;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.rings.EdgeRing;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.rings.FastAlign;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.rings.FastBonzoRing;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.rings.JumpRing;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.rings.LeapRing;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.rings.LookRing;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.rings.MovementRing;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.rings.Ring;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.rings.StopRing;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.rings.UseRing;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.rings.WalkRing;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.subactions.SubAction;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.subactions.SubActionManager;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.subactions.SubActionType;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.subactions.type.EdgeAction;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.subactions.type.JumpAction;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.subactions.type.LookAction;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.subactions.type.StopAction;
import com.ricedotwho.rsm.data.Pos;
import com.ricedotwho.rsm.utils.FileUtils;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map.Entry;
import org.apache.commons.lang3.EnumUtils;

public class RingAdapter implements JsonDeserializer<Ring>, JsonSerializer<Ring> {
   private static final Type posType = (new TypeToken<Pos>() {}).getType();
   private static final Gson gson = FileUtils.getGson();

   public Ring deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
      JsonObject obj = json.getAsJsonObject();
      RingType type = (RingType)EnumUtils.getEnum(RingType.class, obj.get("type").getAsString().toUpperCase());
      Pos min = (Pos)gson.fromJson(obj.get("min").getAsJsonObject(), posType);
      Pos max = (Pos)gson.fromJson(obj.get("max").getAsJsonObject(), posType);
      ArgumentManager args = null;
      if (obj.has("args")) {
         args = this.deserializeArguments(obj.get("args"));
      }

      SubActionManager sub = null;
      if (obj.has("sub")) {
         sub = this.deserializeSubActions(obj.get("sub"));
      }
      return (Ring)(switch (type) {
         case null -> throw new IllegalStateException("Unexpected value: " + obj.get("type"));
         case BONZO -> new BonzoRing(min, max, obj.get("yaw").getAsFloat(), obj.get("pitch").getAsFloat(), args, sub);
         case FAST_BONZO -> new FastBonzoRing(min, max, obj.get("yaw").getAsFloat(), obj.get("pitch").getAsFloat(), args, sub);
         case BLINK -> new BlinkRing(min, max, obj.get("route").getAsString(), args, sub, obj.get("size").getAsInt());
         case JUMP -> new JumpRing(min, max, args, sub);
         case LOOK -> new LookRing(min, max, obj.get("yaw").getAsFloat(), obj.get("pitch").getAsFloat(), args, sub);
         case STOP -> new StopRing(min, max, args, sub);
         case WALK -> new WalkRing(min, max, obj.get("yaw").getAsFloat(), args, sub);
         case ALIGN -> new AlignRing(min, max, args, sub);
         case FAST_ALIGN -> new FastAlign(min, max, args, sub);
         case EDGE -> new EdgeRing(min, max, args, sub);
         case MOVEMENT -> new MovementRing(min, max, obj.get("route").getAsString(), args, sub);
         case BOOM -> new BoomRing(min, max, (Pos)gson.fromJson(obj.get("target").getAsJsonObject(), posType), args, sub);
         case LEAP -> new LeapRing(min, max, args, sub);
         case USE -> new UseRing(min, max, obj.get("item").getAsString(), obj.get("yaw").getAsFloat(), obj.get("pitch").getAsFloat(), args, sub);
         case CHAT -> new ChatRing(min, max, obj.get("message").getAsString(), args, sub);
         case COMMAND -> new CommandRing(min, max, obj.get("command").getAsString(), args, sub);
      });
   }

   public JsonElement serialize(Ring src, Type typeOfSrc, JsonSerializationContext context) {
      return src.serialize();
   }

   public ArgumentManager deserializeArguments(JsonElement json) throws JsonParseException {
      JsonObject obj = json.getAsJsonObject();
      HashMap<RingArgType, Argument<?>> map = new HashMap<>();

      for (Entry<String, JsonElement> entry : obj.entrySet()) {
         RingArgType type = (RingArgType)EnumUtils.getEnum(RingArgType.class, entry.getKey());

         Argument<?> condition = (Argument<?>)(switch (type) {
            case GROUND -> new GroundArg();
            case LEAP -> new LeapArg(entry.getValue().getAsInt());
            case TERM -> new TermArg();
            case TRIGGER -> new TriggerArg();
            case DELAY -> new DelayArg(entry.getValue().getAsLong());
            case TERM_CLOSE -> new TermCloseArg();
         });
         map.put(type, condition);
      }

      return new ArgumentManager(map);
   }

   public SubActionManager deserializeSubActions(JsonElement json) throws JsonParseException {
      JsonObject obj = json.getAsJsonObject();
      HashMap<SubActionType, SubAction> map = new HashMap<>();

      for (Entry<String, JsonElement> entry : obj.entrySet()) {
         SubActionType type = (SubActionType)EnumUtils.getEnum(SubActionType.class, entry.getKey());

         SubAction action = (SubAction)(switch (type) {
            case LOOK -> {
               JsonObject o = entry.getValue().getAsJsonObject();
               yield new LookAction(o.get("yaw").getAsFloat(), o.get("pitch").getAsFloat());
            }
            case JUMP -> new JumpAction();
            case EDGE -> new EdgeAction();
            case STOP -> new StopAction();
         });
         map.put(type, action);
      }

      return new SubActionManager(map);
   }
}
