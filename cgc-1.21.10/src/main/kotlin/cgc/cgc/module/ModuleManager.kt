package cgc.cgc.module

class ModuleManager {
	private val modulesById = linkedMapOf<String, CgcModule>()

	fun register(vararg modules: CgcModule) {
		for (module in modules) {
			require(module.id.isNotBlank()) { "Module id cannot be blank" }
			require(!modulesById.containsKey(module.id)) { "Duplicate module id: ${module.id}" }
			modulesById[module.id] = module
		}
	}

	fun all(): List<CgcModule> =
		modulesById.values.sortedBy { it.displayName.lowercase() }

	fun byCategory(category: ModuleCategory): List<CgcModule> =
		all().filter { it.category == category }

	fun get(id: String): CgcModule? =
		modulesById[id]
}
