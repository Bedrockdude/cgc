package com.ricedotwho.rsa.module.impl.dungeon.boss.p3.terminals.auto.terminals;

import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.terminals.auto.InvWalk;
import com.ricedotwho.rsm.component.impl.Terminals;
import com.ricedotwho.rsm.utils.Utils;
import java.util.HashMap;
import java.util.List;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

public class TerminalRenderer {
   private ScreenHandler terminalContainer = null;
   private final HashMap<Integer, ItemStack> overrides = new HashMap<>();
   private boolean overridesUpdated = false;

   public void renderItems(DrawContext guiGraphics, Terminal terminal) {
      if (this.terminalContainer != null && !this.terminalContainer.slots.isEmpty()) {
         int slotCount = Utils.getGuiSlotCount(this.terminalContainer.getType());
         boolean bl = (Boolean)InvWalk.getUseOverrides().getValue() && (terminal instanceof StartsWith || terminal instanceof Colors);
         if (bl && terminal.isSolved()) {
            this.tryUpdateOverrides(terminal);
         }

         for (int i = 0; i < slotCount && i < this.terminalContainer.slots.size(); i++) {
            Slot slot = (Slot)this.terminalContainer.slots.get(i);
            ItemStack stack = bl && this.overrides.containsKey(slot.id) ? this.overrides.get(slot.id) : slot.getStack();
            int x = i % 9 * 16;
            int y = (int)(Math.floor(i / 9.0F) * 16.0);
            renderSlot(guiGraphics, x, y, stack);
         }
      }
   }

   private void tryUpdateOverrides(Terminal terminal) {
      if (!this.overridesUpdated) {
         this.overrides.clear();
         List<Slot> slots = this.terminalContainer.slots;
         int slotCount = Utils.getGuiSlotCount(this.terminalContainer.getType());

         for (int i = 0; i < slotCount && i < slots.size(); i++) {
            Slot slot = slots.get(i);
            ItemStack stack = slot.getStack();
            if (!stack.isEmpty() && stack.getItem() != Items.BLACK_STAINED_GLASS_PANE) {
               Item item = terminal.isSolved() && terminal.getSolution().containsIndex(i) ? Items.RED_STAINED_GLASS_PANE : Items.LIME_STAINED_GLASS_PANE;
               this.overrides.put(slot.id, item.getDefaultStack().copyWithCount(stack.getCount()));
            }
         }

         this.overridesUpdated = true;
      }
   }

   private static void renderSlot(DrawContext guiGraphics, int x, int y, ItemStack stack) {
      if (!stack.isEmpty()) {
         int k = x + y * 176;
         guiGraphics.drawItem(stack, x, y, k);
         renderItemCount(guiGraphics, MinecraftClient.getInstance().textRenderer, stack, x, y);
      }
   }

   private static void renderItemCount(DrawContext guiGraphics, TextRenderer font, ItemStack itemStack, int i, int j) {
      if (itemStack.getCount() != 1) {
         String string2 = String.valueOf(itemStack.getCount());
         guiGraphics.drawText(font, string2, i + 19 - 2 - font.getWidth(string2), j + 6 + 3, -1, true);
      }
   }

   public void newWindow(ScreenHandler menu) {
      this.overridesUpdated = false;
      this.terminalContainer = menu;
   }

   public void close() {
      this.terminalContainer = null;
   }

   public void renderSolver(float gap) {
      if (Terminals.isInTerminal()) {
         Terminals.getCurrent().render(0.0F, 0.0F, gap, true);
      }
   }
}
