package com.ricedotwho.rsa.module.impl.dungeon.autoroutes;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.awaits.AwaitClick;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.awaits.AwaitEWRaytrace;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.awaits.AwaitSecrets;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.nodes.AotvNode;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.nodes.BatNode;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.nodes.BoomNode;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.nodes.BreakNode;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.nodes.EtherwarpNode;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.nodes.UseNode;
import com.ricedotwho.rsm.data.Pos;
import com.ricedotwho.rsm.utils.FileUtils;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

public class NodeAdapter implements JsonDeserializer<Node>, JsonSerializer<Node> {
   private static final Type posType = (new TypeToken<Pos>() {}).getType();
   private static final Gson gson = FileUtils.getGson();

   public Node deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
      JsonObject obj = json.getAsJsonObject();
      String type = obj.get("type").getAsString().toUpperCase();
      Pos localPos = (Pos)gson.fromJson(obj.get("localPos").getAsJsonObject(), posType);
      boolean start = obj.has("start") && obj.get("start").getAsBoolean();
      AwaitManager awaits = null;
      if (obj.has("awaits")) {
         awaits = this.deserializeAwait(obj.get("awaits"));
      }
      return (Node)(switch (type) {
         case "ETHERWARP" -> new EtherwarpNode(localPos, (Pos)gson.fromJson(obj.get("localTarget").getAsJsonObject(), posType), awaits, start);
         case "BAT" -> new BatNode(localPos, obj.get("yaw").getAsFloat(), obj.get("pitch").getAsFloat(), awaits, start);
         case "BOOM" -> new BoomNode(localPos, (Pos)gson.fromJson(obj.get("target").getAsJsonObject(), posType), awaits, start);
         case "AOTV" -> new AotvNode(localPos, (Pos)gson.fromJson(obj.get("rotationVec").getAsJsonObject(), posType), awaits, start);
         case "BREAK" -> new BreakNode(
            localPos, (List<Pos>)gson.fromJson(obj.getAsJsonArray("blocks"), (new TypeToken<ArrayList<Pos>>() {}).getType()), awaits, start
         );
         case "USE" -> new UseNode(
            localPos,
            (Pos)gson.fromJson(obj.get("rotationVec").getAsJsonObject(), posType),
            obj.get("itemID").getAsString(),
            obj.get("sneak").getAsBoolean(),
            awaits,
            start
         );
         default -> throw new IllegalStateException("Unexpected value: " + type);
      });
   }

   public JsonElement serialize(Node src, Type typeOfSrc, JsonSerializationContext context) {
      return src.serialize();
   }

   public AwaitManager deserializeAwait(JsonElement json) throws JsonParseException {
      JsonObject obj = json.getAsJsonObject();
      HashMap<AwaitType, AwaitCondition<?>> map = new HashMap<>();

      for (Entry<String, JsonElement> entry : obj.entrySet()) {
         AwaitType type = AwaitType.byName(entry.getKey());

         AwaitCondition<?> condition = (AwaitCondition<?>)(switch (type) {
            case CLICK -> new AwaitClick();
            case SECRETS -> new AwaitSecrets(entry.getValue().getAsInt());
            case ETHERWARP_TRACE -> new AwaitEWRaytrace();
         });
         map.put(type, condition);
      }

      return new AwaitManager(map);
   }
}
