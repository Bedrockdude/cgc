package cgc.cgc.module.setting

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import net.fabricmc.loader.api.FabricLoader
import java.lang.reflect.Type
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

class SaveSetting<T>(
	name: String,
	val path: String,
	defaultFile: String,
	private val factory: () -> T,
	private val valueType: Type,
	private val gson: Gson = GsonBuilder().setPrettyPrinting().create(),
	val allowEdits: Boolean = false,
	private val action: (() -> Unit)? = null,
	supplier: (() -> Boolean)? = null
) : Setting<T>(name, supplier, null) {
	val defaultFile: String
	val ext: String
	var fileName: String
		private set
	var file: Path
		private set

	override var value: T = factory()

	init {
		val split = defaultFile.substringBeforeLast('.', defaultFile)
		this.defaultFile = split
		this.ext = defaultFile.substringAfterLast('.', "json")
		this.fileName = split
		this.file = resolveFile()
		this.defaultValue = value
	}

	fun setFileName(fileName: String) {
		if (!allowEdits) return
		this.fileName = fileName
		updateFile()
	}

	fun updateFile() {
		file = resolveFile()
	}

	override fun loadFromJson(obj: JsonObject) {
		fileName = obj.get("file")?.asString?.ifBlank { defaultFile } ?: defaultFile
		updateFile()
		load()
	}

	override fun saveToJson(obj: JsonObject) {
		useDefaultFileNameIfBlank()
		obj.addProperty("name", name)
		obj.addProperty("type", type)
		obj.addProperty("file", fileName)
		save()
	}

	fun save() {
		useDefaultFileNameIfBlank()
		updateFile()
		file.parent?.createDirectories()
		Files.newBufferedWriter(file, StandardCharsets.UTF_8).use { writer ->
			gson.toJson(value, valueType, writer)
		}
	}

	fun load() {
		useDefaultFileNameIfBlank()
		updateFile()
		if (!file.exists()) {
			value = factory()
			save()
			action?.invoke()
			return
		}

		Files.newBufferedReader(file, StandardCharsets.UTF_8).use { reader ->
			value = gson.fromJson(reader, valueType) ?: factory()
		}
		action?.invoke()
	}

	override val type: String = "save"

	override val displayValue: String
		get() = "${fileName.ifBlank { defaultFile }}.$ext"

	private fun useDefaultFileNameIfBlank() {
		if (fileName.isBlank()) {
			fileName = defaultFile
		}
	}

	private fun resolveFile(): Path =
		FabricLoader.getInstance()
			.configDir
			.resolve("cgc")
			.resolve(path)
			.resolve("${fileName.ifBlank { defaultFile }}.$ext")
			.normalize()
}
