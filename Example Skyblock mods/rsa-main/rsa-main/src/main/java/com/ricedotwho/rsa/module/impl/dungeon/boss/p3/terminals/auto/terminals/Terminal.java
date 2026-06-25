package com.ricedotwho.rsa.module.impl.dungeon.boss.p3.terminals.auto.terminals;

import com.ricedotwho.rsa.RSA;
import java.util.List;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;
import net.minecraft.component.DataComponentTypes;

public abstract class Terminal {
   private final TerminalType type;
   protected SolveState solveState;
   private final String title;
   private final int windowID;
   protected final ScreenHandler terminalContainer;
   protected Solution solution;

   protected Terminal(TerminalType type, OpenScreenS2CPacket packet, ScreenHandler terminalContainer) {
      this.type = type;
      this.windowID = packet.getSyncId();
      this.title = packet.getName().getString();
      this.solveState = SolveState.NOT_LOADED;
      this.terminalContainer = terminalContainer;
   }

   public void loadSlot(ScreenHandlerSlotUpdateS2CPacket packet) {
      if (packet.getSyncId() != this.getWindowID()) {
         RSA.chat("Window ID slot load mismatch! -> term : " + this.getWindowID() + " packet : " + packet.getSyncId());
      } else if (packet.getSlot() > this.type.getSlotCount()) {
         if (this.solveState == SolveState.NOT_LOADED) {
            this.solveState = SolveState.LOADED;
         }
      }
   }

   public abstract TerminalState getNextState();

   public abstract TerminalState getCurrentState();

   protected static TerminalState getTerminalState(TerminalType type, List<Terminal.HashInfo> stacks) {
      int hash = 1;

      for (int i = 0; i < stacks.size(); i++) {
         Terminal.HashInfo stack = stacks.get(i);
         hash = 31 * hash + stack.getItem();
         hash = 31 * hash + stack.getSize();
         hash = 31 * hash + (stack.isEnchanted() ? 1 : 0);
      }

      return new TerminalState(type, hash);
   }

   public boolean shouldSolve() {
      return this.solveState != SolveState.NOT_LOADED;
   }

   public boolean isSolved() {
      return this.solution != null && this.solveState != SolveState.NOT_LOADED;
   }

   public void solve() {
      if (this.solveState == SolveState.NOT_LOADED) {
         throw new IllegalStateException("Tried to solve incomplete terminal!");
      }
   }

   public static Terminal fromPacket(OpenScreenS2CPacket packet, ScreenHandler menu) {
      ScreenHandlerType<?> menuType = packet.getScreenHandlerType();
      return menuType != ScreenHandlerType.GENERIC_9X4 && menuType != ScreenHandlerType.GENERIC_9X5 && menuType != ScreenHandlerType.GENERIC_9X6
         ? null
         : findTerminalClass(packet, menu);
   }

   private static Terminal findTerminalClass(OpenScreenS2CPacket packet, ScreenHandler menu) {
      TerminalType terminalType = TerminalType.getType(packet.getName().getString());
      return terminalType == null ? null : terminalType.supply(packet, menu);
   }

   public abstract boolean isEnabled();

   public TerminalType getType() {
      return this.type;
   }

   public SolveState getSolveState() {
      return this.solveState;
   }

   public String getTitle() {
      return this.title;
   }

   public int getWindowID() {
      return this.windowID;
   }

   public Solution getSolution() {
      return this.solution;
   }

   protected static class HashInfo {
      private boolean enchanted;
      private int item;
      private int size;

      protected HashInfo(ItemStack stack) {
         this.enchanted = stack.isEnchantable() || Boolean.TRUE.equals(stack.get(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE));
         this.item = stack.getItem().hashCode();
         this.size = stack.getCount();
      }

      protected void setEnchanted(boolean bl) {
         this.enchanted = bl;
      }

      protected void setItem(Item item) {
         this.item = item.hashCode();
      }

      protected void setSize(int size) {
         this.size = size;
      }

      public boolean isEnchanted() {
         return this.enchanted;
      }

      public int getItem() {
         return this.item;
      }

      public int getSize() {
         return this.size;
      }
   }
}
