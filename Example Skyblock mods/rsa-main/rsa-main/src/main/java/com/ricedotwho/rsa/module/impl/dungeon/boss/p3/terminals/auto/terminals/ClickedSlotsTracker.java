package com.ricedotwho.rsa.module.impl.dungeon.boss.p3.terminals.auto.terminals;

import java.util.HashMap;
import net.minecraft.screen.slot.Slot;
import net.minecraft.item.Item;

public class ClickedSlotsTracker {
   public HashMap<Integer, Item> clickedSlots = new HashMap<>();

   public void clickSlot(Slot slot) {
      this.clickedSlots.put(slot.id, slot.getStack().getItem());
   }

   public boolean contains(Slot slot) {
      return this.clickedSlots.containsKey(slot.id) && this.clickedSlots.get(slot.id) == slot.getStack().getItem();
   }

   public void clear() {
      this.clickedSlots.clear();
   }
}
