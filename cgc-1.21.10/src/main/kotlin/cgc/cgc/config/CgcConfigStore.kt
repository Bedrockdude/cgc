package cgc.cgc.config

import cgc.cgc.module.CgcModule
import cgc.cgc.module.CgcModules
import cgc.cgc.module.setting.Setting
import cgc.cgc.module.setting.group.GroupSetting
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

object CgcConfigStore {
	private val gson = GsonBuilder().setPrettyPrinting().create()
	private val root: Path
		get() = FabricLoader.getInstance().configDir.resolve("cgc")

	fun loadAll() {
		loadModSettings()
		CgcModules.manager.all().forEach(::loadModule)
	}

	fun saveAll() {
		saveModSettings()
		CgcModules.manager.all().forEach(::saveModule)
	}

	fun loadModSettings() {
		val file = root.resolve("mod.json")
		if (!file.exists()) return

		runCatching {
			val obj = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).asJsonObject
			loadSettings(obj.getAsJsonArray("settings"), CgcSettings.settings)
		}
	}

	fun saveModSettings() {
		val obj = JsonObject()
		obj.add("settings", saveSettings(CgcSettings.settings))
		write(root.resolve("mod.json"), obj)
	}

	fun loadModule(module: CgcModule) {
		val file = root.resolve("modules").resolve("${module.id}.json")
		if (!file.exists()) return

		runCatching {
			val obj = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).asJsonObject
			obj.get("toggled")?.asBoolean?.let { module.setEnabled(it) }

			val groups = obj.getAsJsonArray("settings") ?: JsonArray()
			for (groupElement in groups) {
				val groupObj = groupElement.asJsonObject
				val groupName = groupObj.get("name")?.asString ?: continue
				val groupSetting = module.getSettingFromName(groupName) as? GroupSetting<*> ?: continue

				groupSetting.register()
				groupObj.get("toggled")?.asBoolean?.let { groupSetting.value.setEnabled(it) }
				groupSetting.value.onModuleToggled(module.enabled)
				loadSettings(groupObj.getAsJsonArray("settings"), groupSetting.value.settings)
			}

			module.onLoaded()
		}
	}

	fun saveModule(module: CgcModule) {
		val obj = JsonObject()
		obj.addProperty("toggled", module.enabled)

		val groups = JsonArray()
		for (group in module.settings) {
			val groupObj = JsonObject()
			groupObj.addProperty("name", group.name)
			groupObj.addProperty("toggled", group.value.enabled)
			groupObj.add("settings", saveSettings(group.value.settings))
			groups.add(groupObj)
		}
		obj.add("settings", groups)

		write(root.resolve("modules").resolve("${module.id}.json"), obj)
	}

	private fun loadSettings(json: JsonArray?, settings: List<Setting<*>>) {
		if (json == null) return
		val byName = settings.associateBy { it.name.lowercase(Locale.ROOT) }

		for (element in json) {
			runCatching {
				val obj = element.asJsonObject
				val name = obj.get("name")?.asString?.lowercase(Locale.ROOT) ?: return@runCatching
				val setting = byName[name] ?: return@runCatching
				setting.loadFromJson(obj)
				setting.register()
			}
		}
	}

	private fun saveSettings(settings: List<Setting<*>>): JsonArray {
		val array = JsonArray()
		for (setting in settings) {
			if (!setting.savesToConfig()) continue
			val obj = JsonObject()
			setting.saveToJson(obj)
			array.add(obj)
		}
		return array
	}

	private fun write(file: Path, obj: JsonObject) {
		file.parent?.createDirectories()
		Files.writeString(file, gson.toJson(obj), StandardCharsets.UTF_8)
	}
}
