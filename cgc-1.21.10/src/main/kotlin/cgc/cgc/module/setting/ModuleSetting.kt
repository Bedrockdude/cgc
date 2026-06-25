package cgc.cgc.module.setting

import com.google.gson.JsonObject

abstract class Setting<T>(
	val name: String,
	supplier: (() -> Boolean)? = null,
	private val onEditAction: (() -> Unit)? = null
) {
	private var registered = false
	private val supplier: () -> Boolean = supplier ?: { true }

	var shown: Boolean = this.supplier()
		private set

	abstract var value: T
	open var defaultValue: T? = null
		protected set

	open val displayValue: String
		get() = value.toString()

	open val description: String = ""

	abstract fun loadFromJson(obj: JsonObject)

	abstract fun saveToJson(obj: JsonObject)

	abstract val type: String

	open fun savesToConfig(): Boolean = true

	fun register() {
		registered = true
	}

	fun unregister() {
		registered = false
	}

	fun isRegistered(): Boolean = registered

	fun updateShown() {
		shown = supplier()
	}

	fun isVisible(): Boolean {
		updateShown()
		return shown
	}

	fun onEdit() {
		onEditAction?.invoke()
	}
}

typealias ModuleSetting<T> = Setting<T>
