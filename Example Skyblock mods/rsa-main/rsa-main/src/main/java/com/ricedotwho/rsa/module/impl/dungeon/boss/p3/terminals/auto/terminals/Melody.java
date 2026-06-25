package com.ricedotwho.rsa.module.impl.dungeon.boss.p3.terminals.auto.terminals;

import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.terminals.auto.AutoTerms;
import com.ricedotwho.rsm.RSM;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;

public class Melody extends Terminal {
   private LinkedList<SolutionClick> queue = new LinkedList<>();

   protected Melody(OpenScreenS2CPacket packet, ScreenHandler menu) {
      super(TerminalType.MELODY, packet, menu);
   }

   @Override
   public TerminalState getNextState() {
      return this.getCurrentState();
   }

   @Override
   public TerminalState getCurrentState() {
      List<Terminal.HashInfo> infos = new ArrayList<>(this.getType().getSlotCount());

      for (int i = 0; i < this.getType().getSlotCount(); i++) {
         Slot slot = this.terminalContainer.getSlot(i);
         infos.add(new Terminal.HashInfo(slot.getStack()));
      }

      return Terminal.getTerminalState(TerminalType.MELODY, infos);
   }

   public boolean onTickStart(AutoTerms autoTerms) {
      if (this.queue.isEmpty()) {
         return false;
      } else {
         SolutionClick click = this.queue.removeFirst();
         autoTerms.sendWindowClick(click);
         return true;
      }
   }

   @Override
   public void solve() {
      super.solve();
      this.solution = new Solution(Collections.emptyList());
      this.solveState = SolveState.SOLVED;
   }

   @Override
   public void loadSlot(ScreenHandlerSlotUpdateS2CPacket packet) {
      super.loadSlot(packet);
      int slot = packet.getSlot();
      if (packet.getSyncId() == this.getWindowID()) {
         if (slot >= 10 && slot < this.getType().getSlotCount()) {
            ItemStack stack = packet.getStack();
            if (stack.getItem() == Items.LIME_STAINED_GLASS_PANE) {
               if (((Slot)this.terminalContainer.slots.get(slot % 9)).getStack().getItem() == Items.MAGENTA_STAINED_GLASS_PANE) {
                  int buttonIndex = (slot / 9 - 1) * 9 + 16;
                  int mod = slot % 9;
                  this.queue.clear();
                  AutoTerms module = (AutoTerms)RSM.getModule(AutoTerms.class);
                  boolean skip = (Boolean)module.getMelodySkip().getValue()
                     && (mod == 1 || mod == 5)
                     && (!(Boolean)module.getMelodySkipFirst().getValue() || buttonIndex > 18);
                  if (!skip) {
                     this.queue.add(new SolutionClick(SlotActionType.CLONE, buttonIndex, 0));
                  } else {
                     while (buttonIndex <= 43) {
                        this.queue.add(new SolutionClick(SlotActionType.CLONE, buttonIndex, 0));
                        buttonIndex += 9;
                     }
                  }
               }
            }
         }
      }
   }

   @Override
   public boolean isEnabled() {
      return AutoTerms.getTerminals().get("Melody");
   }

   protected static Melody supply(OpenScreenS2CPacket packet, ScreenHandler menu) {
      return new Melody(packet, menu);
   }
}
