package com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3;

import com.mojang.datafixers.util.Function5;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.args.ArgumentManager;
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
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.subactions.SubActionManager;
import com.ricedotwho.rsm.data.Pos;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.HitResult.Type;

public enum RingType {
   ALIGN("align", AlignRing::new, 0.0F, Set.of(), null),
   FAST_ALIGN("fastalign", FastAlign::new, 0.01F, Set.of(), null),
   STOP("stop", StopRing::new, 0.02F, Set.of(), null),
   WALK("walk", WalkRing::new, 0.03F, Set.of(), null),
   JUMP("jump", JumpRing::new, 0.04F, Set.of(), null),
   BONZO("bonzo", BonzoRing::new, 0.05F, Set.of(), null),
   FAST_BONZO("fastbonzo", FastBonzoRing::new, 0.06F, Set.of(), null),
   EDGE("edge", EdgeRing::new, 0.06F, Set.of(), null),
   MOVEMENT("movement", MovementRing::new, 0.07F, Set.of("route"), null),
   LOOK("look", LookRing::new, 0.08F, Set.of(), null),
   BOOM("boom", BoomRing::new, 0.009F, Set.of(), Type.BLOCK),
   LEAP("leap", LeapRing::new, 0.01F, Set.of(), null),
   USE("use", UseRing::new, 0.011F, Set.of(), null),
   CHAT("chat", ChatRing::new, 0.012F, Set.of("message"), null),
   COMMAND("command", CommandRing::new, 0.013F, Set.of("command"), null),
   BLINK("blink", BlinkRing::new, 0.014F, Set.of(), null);

   private final String name;
   private final Function5<Pos, Pos, ArgumentManager, SubActionManager, Map<String, Object>, Ring> factory;
   private final float renderSizeOffset;
   private final Set<String> required;
   private final Type hitResult;

   private RingType(
      String s,
      Function5<Pos, Pos, ArgumentManager, SubActionManager, Map<String, Object>, Ring> factory,
      float renderSizeOffset,
      Set<String> required,
      Type hitResult
   ) {
      this.name = s;
      this.renderSizeOffset = renderSizeOffset;
      this.factory = factory;
      this.required = required;
      this.hitResult = hitResult;
   }

   public Ring supply(Pos min, Pos max, ArgumentManager manager, SubActionManager actions, Map<String, Object> extraData) {
      return this.factory != null && MinecraftClient.getInstance().player != null ? (Ring)this.factory.apply(min, max, manager, actions, extraData) : null;
   }

   public static RingType byName(String name) {
      return Arrays.stream(values()).filter(n -> n.getName().equalsIgnoreCase(name)).findAny().orElse(null);
   }

   public String getName() {
      return this.name;
   }

   public float getRenderSizeOffset() {
      return this.renderSizeOffset;
   }

   public Set<String> getRequired() {
      return this.required;
   }

   public Type getHitResult() {
      return this.hitResult;
   }
}
