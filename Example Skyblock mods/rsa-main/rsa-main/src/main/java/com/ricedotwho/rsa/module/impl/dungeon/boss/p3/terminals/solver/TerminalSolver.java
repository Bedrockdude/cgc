package com.ricedotwho.rsa.module.impl.dungeon.boss.p3.terminals.solver;

import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.terminals.solver.types.Rubix;
import com.ricedotwho.rsm.data.TerminalType;
import com.ricedotwho.rsm.module.api.Category;
import com.ricedotwho.rsm.module.api.ModuleInfo;
import com.ricedotwho.rsm.module.impl.dungeon.boss.p3.terminal.types.Term;
import com.ricedotwho.rsm.ui.clickgui.settings.Setting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.BooleanSetting;
import java.math.BigDecimal;

@ModuleInfo(aliases = "Terminal Solver", id = "TerminalSolver", category = Category.DUNGEONS, isOverwrite = true)
public class TerminalSolver extends com.ricedotwho.rsm.module.impl.dungeon.boss.p3.terminal.TerminalSolver {
   private static final BooleanSetting anyRubix = new BooleanSetting("Any Click Rubix", false);
   private final BooleanSetting offTickSlots = new BooleanSetting("Off Tick Slots", false);

   public TerminalSolver() {
      this.registerProperty(new Setting[]{anyRubix, this.offTickSlots});
      getClickDelay().setMin(BigDecimal.ZERO);
   }

   public Term create(TerminalType type, String title) {
      return (Term)(type == TerminalType.RUBIX ? new Rubix(title) : type.create(title));
   }

   public BooleanSetting getOffTickSlots() {
      return this.offTickSlots;
   }

   public static BooleanSetting getAnyRubix() {
      return anyRubix;
   }
}
