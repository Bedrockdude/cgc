package com.ricedotwho.rsa.module.impl;

import com.ricedotwho.rsm.module.Module;
import com.ricedotwho.rsm.module.api.Category;
import com.ricedotwho.rsm.module.api.ModuleInfo;
import com.ricedotwho.rsm.ui.clickgui.settings.Setting;

@ModuleInfo(aliases = "Template", id = "Template", category = Category.OTHER)
public class Template extends Module {
   public Template() {
      this.registerProperty(new Setting[0]);
   }

   public void onEnable() {
   }

   public void onDisable() {
   }

   public void reset() {
   }
}
