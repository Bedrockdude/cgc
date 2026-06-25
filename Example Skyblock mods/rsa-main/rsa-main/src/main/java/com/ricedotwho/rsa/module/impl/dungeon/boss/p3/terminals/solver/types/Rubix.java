package com.ricedotwho.rsa.module.impl.dungeon.boss.p3.terminals.solver.types;

import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.terminals.solver.TerminalSolver;
import com.ricedotwho.rsm.component.impl.Terminals;
import com.ricedotwho.rsm.module.impl.dungeon.boss.p3.terminal.TermSol;
import java.math.BigDecimal;

public class Rubix extends com.ricedotwho.rsm.module.impl.dungeon.boss.p3.terminal.types.Rubix {
   public Rubix(String title) {
      super(title);
   }

   protected boolean canClick(int slot, int button) {
      TermSol sol = this.getBySlot(slot);
      if (sol != null && this.solution.contains(sol) && !(Boolean)TerminalSolver.getBlockAll().getValue()) {
         if (TerminalSolver.getMode().is("Queue")) {
            return this.getHoveredSlot() == slot;
         } else {
            long now = System.currentTimeMillis();
            if (now - Terminals.getOpenedAt() >= ((BigDecimal)TerminalSolver.getFirstDelay().getValue()).longValue()
               && now - Terminals.getClickedAt() >= ((BigDecimal)TerminalSolver.getClickDelay().getValue()).longValue()) {
               if (TerminalSolver.getMode().is("Zero Ping")) {
                  if (now - Terminals.getClickedAt() < ((BigDecimal)TerminalSolver.getClickDelay().getValue()).longValue()) {
                     return false;
                  }
               } else if (this.isClicked()) {
                  return false;
               }

               return this.getHoveredSlot() == slot;
            } else {
               return false;
            }
         }
      } else {
         return false;
      }
   }

   public void clickSlot(int slot, int button) {
      if (this.canClick(slot, button)) {
         if (TerminalSolver.getMode().getIndex() != 0) {
            TermSol sol = this.getBySlot(slot);
            int realClicks = sol.getClicks() > 2 ? sol.getClicks() - 5 : sol.getClicks();
            if ((Boolean)TerminalSolver.getAnyRubix().getValue()) {
               if (realClicks < 0) {
                  sol.setClicks(sol.getClicks() + 1);
                  button = 1;
               } else {
                  sol.setClicks(sol.getClicks() - 1);
                  button = 2;
               }
            } else if (button == 1) {
               if (realClicks > 0) {
                  return;
               }

               sol.setClicks(sol.getClicks() + 1);
            } else {
               if (realClicks < 0) {
                  return;
               }

               sol.setClicks(sol.getClicks() - 1);
            }

            this.onZeroPingClick(slot, button, sol);
         }

         if (TerminalSolver.getMode().is("Queue")) {
            this.onQueueClick();
         } else {
            this.clicked = true;
            this.click(slot, button);
         }
      }
   }
}
