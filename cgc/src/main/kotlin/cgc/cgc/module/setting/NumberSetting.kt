package cgc.cgc.module.setting

import com.google.gson.JsonObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.roundToInt

class NumberSetting(
	name: String,
	min: Double,
	max: Double,
	defaultValue: Double,
	increment: Double,
	val unit: String = "",
	onEdit: (() -> Unit)? = null,
	supplier: (() -> Boolean)? = null,
	private val displayAsPercent: Boolean = false,
	private val percentScale: Double = 100.0
) : Setting<BigDecimal>(name, supplier, onEdit) {
	var min: BigDecimal = BigDecimal.valueOf(min)
	var max: BigDecimal = BigDecimal.valueOf(max)
	var increment: BigDecimal = BigDecimal.valueOf(increment)
	override var value: BigDecimal = BigDecimal.valueOf(defaultValue).coerceIn(this.min, this.max)

	init {
		require(max >= min) { "max must be greater than or equal to min" }
		require(increment > 0.0) { "increment must be positive" }
		this.defaultValue = value
	}

	fun setValue(value: Double) {
		this.value = BigDecimal.valueOf(value).coerceIn(min, max)
		onEdit()
	}

	fun setValue(value: String) {
		this.value = BigDecimal(value).coerceIn(min, max)
		onEdit()
	}

	fun valueAsString(): String =
		value.stripTrailingZeros()
			.setScale(minOf(2, maxOf(0, value.stripTrailingZeros().scale())), RoundingMode.HALF_UP)
			.toPlainString()

	fun increase() {
		setValue(value.add(increment).toDouble())
	}

	fun decrease() {
		setValue(value.subtract(increment).toDouble())
	}

	override fun loadFromJson(obj: JsonObject) {
		setValue(obj.get("value").asString)
	}

	override fun saveToJson(obj: JsonObject) {
		obj.addProperty("name", name)
		obj.addProperty("type", type)
		obj.addProperty("value", value.toPlainString())
	}

	override val type: String = "number"

	override val displayValue: String
		get() {
			if (displayAsPercent) return "${(value.toDouble() * percentScale).roundToInt()}%"
			if (unit.isNotBlank()) return "${valueAsString()}$unit"
			if (value.remainder(BigDecimal.ONE) == BigDecimal.ZERO) return value.toInt().toString()
			return String.format(Locale.ROOT, "%.2f", value.toDouble()).trimEnd('0').trimEnd('.')
		}
}

private fun BigDecimal.coerceIn(min: BigDecimal, max: BigDecimal): BigDecimal =
	this.max(min).min(max)
