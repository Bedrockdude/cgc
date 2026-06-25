package com.ricedotwho.rsa.module.impl.dungeon.boss.p3.terminals.auto.terminals;

import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.terminals.auto.AutoTerms;
import com.ricedotwho.rsm.RSM;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.util.Formatting;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;

public class Colors extends Terminal {
   private static final Map<String, String> COLOR_REPLACEMENTS = Map.of(
      "light gray",
      "silver",
      "wool",
      "white",
      "bone",
      "white",
      "ink",
      "black",
      "lapis",
      "blue",
      "cocoa",
      "brown",
      "dandelion",
      "yellow",
      "rose",
      "red",
      "cactus",
      "green"
   );

   protected Colors(OpenScreenS2CPacket packet, ScreenHandler menu) {
      super(TerminalType.COLORS, packet, menu);
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

         return Terminal.getTerminalState(TerminalType.COLORS, infos);
      }
   }

   @Override
   public TerminalState getCurrentState() {
      List<Terminal.HashInfo> infos = new ArrayList<>(this.getType().getSlotCount());

      for (int i = 0; i < this.getType().getSlotCount(); i++) {
         Slot slot = this.terminalContainer.getSlot(i);
         infos.add(new Terminal.HashInfo(slot.getStack()));
      }

      return Terminal.getTerminalState(TerminalType.COLORS, infos);
   }

   @Override
   public void solve() {
      super.solve();
      Pattern pattern = Pattern.compile("Select all the (.+) items!");
      Matcher matcher = pattern.matcher(this.getTitle());
      if (matcher.find()) {
         String color = matcher.group(1).toLowerCase();
         List<SolutionClick> solutionClicks = new ArrayList<>();

         for (Slot slot : this.terminalContainer.slots) {
            ItemStack stack = slot.getStack();
            if (!stack.isEmpty() && !((AutoTerms)RSM.getModule(AutoTerms.class)).getClickedSlotsTracker().contains(slot)) {
               String fixedName = this.fixColorItemName(Formatting.strip(stack.getName().getString()).toLowerCase());
               if (fixedName.startsWith(color)) {
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
      return AutoTerms.getTerminals().get("Colours");
   }

   private String fixColorItemName(String itemName) {
      for (Entry<String, String> entry : COLOR_REPLACEMENTS.entrySet()) {
         String from = entry.getKey();
         String to = entry.getValue();
         if (itemName.startsWith(from)) {
            itemName = to + itemName.substring(from.length());
         }
      }

      return itemName;
   }

   protected static Colors supply(OpenScreenS2CPacket packet, ScreenHandler menu) {
      return new Colors(packet, menu);
   }
}
