package com.ricedotwho.rsa.module.impl.dungeon.boss.p3.terminals.auto.terminals;

import net.minecraft.screen.slot.SlotActionType;

public class SolutionClick {
   private final SlotActionType type;
   private final int index;
   private final int button;

   public SolutionClick(SlotActionType type, int index, int button) {
      this.type = type;
      this.index = index;
      this.button = button;
   }

   public SlotActionType type() {
      return this.type;
   }

   public int index() {
      return this.index;
   }

   public int button() {
      return this.button;
   }
}
