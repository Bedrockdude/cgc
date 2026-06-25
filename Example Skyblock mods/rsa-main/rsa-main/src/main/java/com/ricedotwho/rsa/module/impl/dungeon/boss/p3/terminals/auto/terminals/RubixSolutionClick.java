package com.ricedotwho.rsa.module.impl.dungeon.boss.p3.terminals.auto.terminals;

import net.minecraft.screen.slot.SlotActionType;

public class RubixSolutionClick extends SolutionClick {
   private final int colorIndex;

   public RubixSolutionClick(SlotActionType type, int index, int button, int colorIndex) {
      super(type, index, button);
      this.colorIndex = colorIndex;
   }

   public int colorIndex() {
      return this.colorIndex;
   }
}
