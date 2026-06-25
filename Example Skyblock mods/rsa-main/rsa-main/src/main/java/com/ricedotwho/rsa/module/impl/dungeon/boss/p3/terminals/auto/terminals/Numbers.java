package com.ricedotwho.rsa.module.impl.dungeon.boss.p3.terminals.auto.terminals;

import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.terminals.auto.AutoTerms;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;

public class Numbers extends Terminal {
   protected Numbers(OpenScreenS2CPacket packet, ScreenHandler menu) {
      super(TerminalType.NUMBERS, packet, menu);
   }

   @Override
   public TerminalState getNextState() {
      if (this.solution == null) {
         throw new IllegalStateException("Tried to get next state without solving!");
      } else {
         List<Terminal.HashInfo> infos = new ArrayList<>(this.getType().getSlotCount());
         int changedIndex = this.solution.getNext().index();

         for (int i = 0; i < this.getType().getSlotCount(); i++) {
            Slot slot = this.terminalContainer.getSlot(i);
            Terminal.HashInfo hashInfo = new Terminal.HashInfo(slot.getStack());
            if (slot.id == changedIndex) {
               hashInfo.setItem(Items.LIME_STAINED_GLASS_PANE);
            }

            infos.add(hashInfo);
         }

         return Terminal.getTerminalState(TerminalType.NUMBERS, infos);
      }
   }

   @Override
   public TerminalState getCurrentState() {
      List<Terminal.HashInfo> infos = new ArrayList<>(this.getType().getSlotCount());

      for (int i = 0; i < this.getType().getSlotCount(); i++) {
         Slot slot = this.terminalContainer.getSlot(i);
         infos.add(new Terminal.HashInfo(slot.getStack()));
      }

      return Terminal.getTerminalState(TerminalType.NUMBERS, infos);
   }

   @Override
   public void solve() {
      super.solve();
      List<SolutionClick> sortedSlots = this.terminalContainer
         .slots
         .stream()
         .filter(slot -> slot.getStack().getItem() == Items.RED_STAINED_GLASS_PANE)
         .sorted(Comparator.comparingInt(slot -> slot.getStack().getCount()))
         .map(slot -> new SolutionClick(SlotActionType.CLONE, slot.id, 0))
         .toList();
      this.solution = new Solution(sortedSlots);
      this.solveState = SolveState.SOLVED;
   }

   @Override
   public boolean isEnabled() {
      return AutoTerms.getTerminals().get("Numbers");
   }

   protected static Numbers supply(OpenScreenS2CPacket packet, ScreenHandler menu) {
      return new Numbers(packet, menu);
   }
}
