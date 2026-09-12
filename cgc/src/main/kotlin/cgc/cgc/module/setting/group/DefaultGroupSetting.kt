package cgc.cgc.module.setting.group

import cgc.cgc.module.CgcModule
import cgc.cgc.module.SubModule

class DefaultGroupSetting(
	name: String,
	module: CgcModule,
	supplier: (() -> Boolean)? = null
) : GroupSetting<DefaultGroupSetting.DefaultSubModule>(name, DefaultSubModule(module, name), supplier) {
	class DefaultSubModule(module: CgcModule, name: String) : SubModule<CgcModule>(module, name)
}
