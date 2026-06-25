package com.ricedotwho.rsa.module.impl.dungeon.boss.p3.terminals.auto.terminals;

import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.terminals.auto.AutoTerms;
import com.ricedotwho.rsm.RSM;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.util.Formatting;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;

public class StartsWith extends Terminal {
   protected StartsWith(OpenScreenS2CPacket packet, ScreenHandler menu) {
      super(TerminalType.STARTSWITH, packet, menu);
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
               hashInfo.setEnchanted(true);
            }

            infos.add(hashInfo);
         }

         return Terminal.getTerminalState(TerminalType.STARTSWITH, infos);
      }
   }

   @Override
   public TerminalState getCurrentState() {
      List<Terminal.HashInfo> infos = new ArrayList<>(this.getType().getSlotCount());

      for (int i = 0; i < this.getType().getSlotCount(); i++) {
         Slot slot = this.terminalContainer.getSlot(i);
         infos.add(new Terminal.HashInfo(slot.getStack()));
      }

      return Terminal.getTerminalState(TerminalType.STARTSWITH, infos);
   }

   @Override
   public void solve() {
      super.solve();
      Pattern pattern = Pattern.compile("What starts with: '(\\w+)'?");
      Matcher matcher = pattern.matcher(this.getTitle());
      if (matcher.find()) {
         String matchLetter = matcher.group(1).toLowerCase();
         List<SolutionClick> solutionClicks = new ArrayList<>();

         for (Slot slot : this.terminalContainer.slots) {
            ItemStack stack = slot.getStack();
            if (!stack.isEmpty() && !((AutoTerms)RSM.getModule(AutoTerms.class)).getClickedSlotsTracker().contains(slot)) {
               String name = Formatting.strip(stack.getName().getString()).toLowerCase();
               if (name.startsWith(matchLetter)) {
                  solutionClicks.add(new SolutionClick(SlotActionType.CLONE, slot.id, 0));
               }
            }
         }

         this.solution = new Solution(solutionClicks);
         this.solveState = SolveState.SOLVED;
      }
   }

   @Override
   public boolean isEnabled() {
      return AutoTerms.getTerminals().get("Starts With");
   }

   protected static StartsWith supply(OpenScreenS2CPacket packet, ScreenHandler menu) {
      return new StartsWith(packet, menu);
   }
}
