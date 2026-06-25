package com.ricedotwho.rsa.module.impl.dungeon.puzzle;

import com.ricedotwho.rsm.module.api.Category;
import com.ricedotwho.rsm.module.api.ModuleInfo;
import com.ricedotwho.rsm.module.impl.dungeon.puzzle.TicTacToe;
import com.ricedotwho.rsm.ui.clickgui.settings.Setting;
import com.ricedotwho.rsm.ui.clickgui.settings.group.GroupSetting;

@ModuleInfo(aliases = "Puzzles", id = "Puzzles", category = Category.DUNGEONS, isOverwrite = true)
public class Puzzles extends com.ricedotwho.rsm.module.impl.dungeon.puzzle.Puzzles {
   private final GroupSetting<TicTacToe> ticTacToe = new GroupSetting("TTT", new AutoTTT(this));
   private final GroupSetting<com.ricedotwho.rsm.module.impl.dungeon.puzzle.IceFill> iceFill = new GroupSetting("Ice Fill", new IceFill(this));

   public Puzzles() {
      this.registerProperty(new Setting[]{this.ticTacToe, this.iceFill});
   }

   public GroupSetting<TicTacToe> getTicTacToe() {
      return this.ticTacToe;
   }

   public GroupSetting<com.ricedotwho.rsm.module.impl.dungeon.puzzle.IceFill> getIceFill() {
      return this.iceFill;
   }
}
