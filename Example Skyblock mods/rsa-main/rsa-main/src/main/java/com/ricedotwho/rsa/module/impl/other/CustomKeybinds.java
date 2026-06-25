package com.ricedotwho.rsa.module.impl.other;

import com.ricedotwho.rsm.data.Keybind;
import com.ricedotwho.rsm.module.Module;
import com.ricedotwho.rsm.module.api.Category;
import com.ricedotwho.rsm.module.api.ModuleInfo;
import com.ricedotwho.rsm.ui.clickgui.settings.Setting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.KeybindSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.StringSetting;

@ModuleInfo(aliases = "Keybind Shortcuts", id = "CustomKeybinds", category = Category.OTHER)
public class CustomKeybinds extends Module {
   private final StringSetting string1 = new StringSetting("Command 1", "");
   private final StringSetting string2 = new StringSetting("Command 2", "");
   private final StringSetting string3 = new StringSetting("Command 3", "");
   private final StringSetting string4 = new StringSetting("Command 4", "");
   private final StringSetting string5 = new StringSetting("Command 5", "");
   private final StringSetting string6 = new StringSetting("Command 6", "");
   private final StringSetting string7 = new StringSetting("Command 7", "");
   private final StringSetting string8 = new StringSetting("Command 8", "");
   private final StringSetting string9 = new StringSetting("Command 9", "");
   private final StringSetting string10 = new StringSetting("Command 10", "");
   private final StringSetting string11 = new StringSetting("Command 11", "");
   private final StringSetting string12 = new StringSetting("Command 12", "");
   private final StringSetting string13 = new StringSetting("Command 13", "");
   private final StringSetting string14 = new StringSetting("Command 14", "");
   private final StringSetting string15 = new StringSetting("Command 15", "");
   private final KeybindSetting keybind1 = new KeybindSetting("Keybind 1", new Keybind(-1, false, () -> this.sendCmd((String)this.string1.getValue())));
   private final KeybindSetting keybind2 = new KeybindSetting("Keybind 2", new Keybind(-1, false, () -> this.sendCmd((String)this.string2.getValue())));
   private final KeybindSetting keybind3 = new KeybindSetting("Keybind 3", new Keybind(-1, false, () -> this.sendCmd((String)this.string3.getValue())));
   private final KeybindSetting keybind4 = new KeybindSetting("Keybind 4", new Keybind(-1, false, () -> this.sendCmd((String)this.string4.getValue())));
   private final KeybindSetting keybind5 = new KeybindSetting("Keybind 5", new Keybind(-1, false, () -> this.sendCmd((String)this.string5.getValue())));
   private final KeybindSetting keybind6 = new KeybindSetting("Keybind 6", new Keybind(-1, false, () -> this.sendCmd((String)this.string6.getValue())));
   private final KeybindSetting keybind7 = new KeybindSetting("Keybind 7", new Keybind(-1, false, () -> this.sendCmd((String)this.string7.getValue())));
   private final KeybindSetting keybind8 = new KeybindSetting("Keybind 8", new Keybind(-1, false, () -> this.sendCmd((String)this.string8.getValue())));
   private final KeybindSetting keybind9 = new KeybindSetting("Keybind 9", new Keybind(-1, false, () -> this.sendCmd((String)this.string9.getValue())));
   private final KeybindSetting keybind10 = new KeybindSetting("Keybind 10", new Keybind(-1, false, () -> this.sendCmd((String)this.string10.getValue())));
   private final KeybindSetting keybind11 = new KeybindSetting("Keybind 11", new Keybind(-1, false, () -> this.sendCmd((String)this.string11.getValue())));
   private final KeybindSetting keybind12 = new KeybindSetting("Keybind 12", new Keybind(-1, false, () -> this.sendCmd((String)this.string12.getValue())));
   private final KeybindSetting keybind13 = new KeybindSetting("Keybind 13", new Keybind(-1, false, () -> this.sendCmd((String)this.string13.getValue())));
   private final KeybindSetting keybind14 = new KeybindSetting("Keybind 14", new Keybind(-1, false, () -> this.sendCmd((String)this.string14.getValue())));
   private final KeybindSetting keybind15 = new KeybindSetting("Keybind 15", new Keybind(-1, false, () -> this.sendCmd((String)this.string15.getValue())));

   public CustomKeybinds() {
      this.registerProperty(
         new Setting[]{
            this.keybind1,
            this.string1,
            this.keybind2,
            this.string2,
            this.keybind3,
            this.string3,
            this.keybind4,
            this.string4,
            this.keybind5,
            this.string5,
            this.keybind6,
            this.string6,
            this.keybind7,
            this.string7,
            this.keybind8,
            this.string8,
            this.keybind9,
            this.string9,
            this.keybind10,
            this.string10,
            this.keybind11,
            this.string11,
            this.keybind12,
            this.string12,
            this.keybind13,
            this.string13,
            this.keybind14,
            this.string14,
            this.keybind15,
            this.string15
         }
      );
   }

   private void sendCmd(String cmd) {
      if (mc.player != null && mc.getNetworkHandler() != null) {
         mc.getNetworkHandler().sendChatCommand(cmd);
      }
   }

   public StringSetting getString1() {
      return this.string1;
   }

   public StringSetting getString2() {
      return this.string2;
   }

   public StringSetting getString3() {
      return this.string3;
   }

   public StringSetting getString4() {
      return this.string4;
   }

   public StringSetting getString5() {
      return this.string5;
   }

   public StringSetting getString6() {
      return this.string6;
   }

   public StringSetting getString7() {
      return this.string7;
   }

   public StringSetting getString8() {
      return this.string8;
   }

   public StringSetting getString9() {
      return this.string9;
   }

   public StringSetting getString10() {
      return this.string10;
   }

   public StringSetting getString11() {
      return this.string11;
   }

   public StringSetting getString12() {
      return this.string12;
   }

   public StringSetting getString13() {
      return this.string13;
   }

   public StringSetting getString14() {
      return this.string14;
   }

   public StringSetting getString15() {
      return this.string15;
   }

   public KeybindSetting getKeybind1() {
      return this.keybind1;
   }

   public KeybindSetting getKeybind2() {
      return this.keybind2;
   }

   public KeybindSetting getKeybind3() {
      return this.keybind3;
   }

   public KeybindSetting getKeybind4() {
      return this.keybind4;
   }

   public KeybindSetting getKeybind5() {
      return this.keybind5;
   }

   public KeybindSetting getKeybind6() {
      return this.keybind6;
   }

   public KeybindSetting getKeybind7() {
      return this.keybind7;
   }

   public KeybindSetting getKeybind8() {
      return this.keybind8;
   }

   public KeybindSetting getKeybind9() {
      return this.keybind9;
   }

   public KeybindSetting getKeybind10() {
      return this.keybind10;
   }

   public KeybindSetting getKeybind11() {
      return this.keybind11;
   }

   public KeybindSetting getKeybind12() {
      return this.keybind12;
   }

   public KeybindSetting getKeybind13() {
      return this.keybind13;
   }

   public KeybindSetting getKeybind14() {
      return this.keybind14;
   }

   public KeybindSetting getKeybind15() {
      return this.keybind15;
   }
}
