package com.ricedotwho.rsa.module.impl.dungeon.boss.p3.terminals.auto.terminals;

import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.terminals.auto.AutoTerms;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;

public class Rubix extends Terminal {
   public static final Item[] COLOR_ORDER = new Item[]{
      Items.ORANGE_STAINED_GLASS_PANE, Items.YELLOW_STAINED_GLASS_PANE, Items.GREEN_STAINED_GLASS_PANE, Items.BLUE_STAINED_GLASS_PANE, Items.RED_STAINED_GLASS_PANE
   };

   protected Rubix(OpenScreenS2CPacket packet, ScreenHandler menu) {
      super(TerminalType.RUBIX, packet, menu);
   }

   @Override
   public TerminalState getNextState() {
      if (this.solution == null) {
         throw new IllegalStateException("Tried to get next state without solving!");
      } else {
         List<Terminal.HashInfo> infos = new ArrayList<>(this.getType().getSlotCount());
         SolutionClick solutionClick = this.solution.getNext();

         for (int i = 0; i < this.getType().getSlotCount(); i++) {
            Slot slot = this.terminalContainer.getSlot(i);
            Terminal.HashInfo hashInfo = new Terminal.HashInfo(slot.getStack());
            if (slot.id == solutionClick.index()) {
               int colorIndex = ((RubixSolutionClick)solutionClick).colorIndex();
               if (solutionClick.button() == 0) {
                  hashInfo.setItem(COLOR_ORDER[(colorIndex + 1) % COLOR_ORDER.length]);
                  hashInfo.setEnchanted(false);
                  hashInfo.setSize(1);
               } else {
                  hashInfo.setItem(COLOR_ORDER[(colorIndex - 1 + COLOR_ORDER.length) % COLOR_ORDER.length]);
                  hashInfo.setEnchanted(false);
                  hashInfo.setSize(1);
               }
            }

            infos.add(hashInfo);
         }

         return Terminal.getTerminalState(TerminalType.RUBIX, infos);
      }
   }

   @Override
   public TerminalState getCurrentState() {
      List<Terminal.HashInfo> infos = new ArrayList<>(this.getType().getSlotCount());

      for (int i = 0; i < this.getType().getSlotCount(); i++) {
         Slot slot = this.terminalContainer.getSlot(i);
         infos.add(new Terminal.HashInfo(slot.getStack()));
      }

      return Terminal.getTerminalState(TerminalType.RUBIX, infos);
   }

   @Override
   public void solve() {
      super.solve();
      List<Integer> rubixSlots = new ArrayList<>();

      for (Slot slot : this.terminalContainer.slots) {
         ItemStack stack = slot.getStack();
         if (!stack.isEmpty() && stack.getItem() != Items.BLACK_STAINED_GLASS_PANE && this.isRubixPane(stack.getItem())) {
            rubixSlots.add(slot.id);
         }
      }

      int minIndex = -1;
      int minTotal = Integer.MAX_VALUE;

      for (int targetIndex = 0; targetIndex < COLOR_ORDER.length; targetIndex++) {
         int totalClicks = 0;

         for (Integer slotx : rubixSlots) {
            ItemStack stack = this.terminalContainer.getSlot(slotx).getStack();
            int currentIndex = this.indexOf(COLOR_ORDER, stack.getItem());
            int clockwise = (targetIndex - currentIndex + COLOR_ORDER.length) % COLOR_ORDER.length;
            int counterClockwise = (currentIndex - targetIndex + COLOR_ORDER.length) % COLOR_ORDER.length;
            totalClicks += Math.min(clockwise, counterClockwise);
         }

         if (totalClicks < minTotal) {
            minTotal = totalClicks;
            minIndex = targetIndex;
         }
      }

      List<SolutionClick> solutionClicks = new ArrayList<>();

      for (Integer slotx : rubixSlots) {
         ItemStack stack = this.terminalContainer.getSlot(slotx).getStack();
         int currentIndex = this.indexOf(COLOR_ORDER, stack.getItem());
         int clockwise = (minIndex - currentIndex + COLOR_ORDER.length) % COLOR_ORDER.length;
         int counterClockwise = (currentIndex - minIndex + COLOR_ORDER.length) % COLOR_ORDER.length;
         if (clockwise <= counterClockwise) {
            for (int j = 0; j < clockwise; j++) {
               solutionClicks.add(new RubixSolutionClick(SlotActionType.PICKUP, slotx, 0, currentIndex));
            }
         } else {
            for (int j = 0; j < counterClockwise; j++) {
               solutionClicks.add(new RubixSolutionClick(SlotActionType.PICKUP, slotx, 1, currentIndex));
            }
         }
      }

      this.solution = new Solution(solutionClicks);
      this.solveState = SolveState.SOLVED;
   }

   private <T> int indexOf(T[] array, T val) {
      for (int i = 0; i < array.length; i++) {
         if (array[i] == val) {
            return i;
         }
      }

      throw new IndexOutOfBoundsException("Could not find color : " + ((Item)val).getName().getString());
   }

   private boolean isRubixPane(Item item) {
      return item == Items.BLUE_STAINED_GLASS_PANE
         || item == Items.RED_STAINED_GLASS_PANE
         || item == Items.ORANGE_STAINED_GLASS_PANE
         || item == Items.YELLOW_STAINED_GLASS_PANE
         || item == Items.GREEN_STAINED_GLASS_PANE;
   }

   @Override
   public boolean isEnabled() {
      return AutoTerms.getTerminals().get("Rubix");
   }

   protected static Rubix supply(OpenScreenS2CPacket packet, ScreenHandler menu) {
      return new Rubix(packet, menu);
   }
}
