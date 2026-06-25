package com.ricedotwho.rsa.module.impl.dungeon.autoroutes;

import com.mojang.datafixers.util.Function4;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.nodes.AotvNode;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.nodes.BatNode;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.nodes.BoomNode;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.nodes.BreakNode;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.nodes.EtherwarpNode;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.nodes.UseNode;
import com.ricedotwho.rsm.component.impl.map.map.UniqueRoom;
import java.util.Arrays;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

public enum NodeType {
   ETHERWARP("ew", EtherwarpNode::supply),
   BOOM("boom", BoomNode::supply),
   BAT("bat", BatNode::supply),
   AOTV("aotv", AotvNode::supply),
   BREAK("break", BreakNode::supply),
   USE("use", UseNode::supply);

   private final String name;
   private final Function4<UniqueRoom, ClientPlayerEntity, AwaitManager, Boolean, Node> factory;

   private NodeType(String s, Function4<UniqueRoom, ClientPlayerEntity, AwaitManager, Boolean, Node> factory) {
      this.name = s;
      this.factory = factory;
   }

   public Node supply(UniqueRoom fullRoom, AwaitManager awaits, boolean start) {
      return this.factory != null && MinecraftClient.getInstance().player != null
         ? (Node)this.factory.apply(fullRoom, MinecraftClient.getInstance().player, awaits, start)
         : null;
   }

   public static NodeType byName(String name) {
      return Arrays.stream(values()).filter(n -> n.getName().equalsIgnoreCase(name)).findAny().orElse(null);
   }

   public String getName() {
      return this.name;
   }
}
