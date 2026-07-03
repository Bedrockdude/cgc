package cgc.cgc.module.setting

import cgc.cgc.data.Keybind
import cgc.cgc.utils.ItemUtils
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.ChatFormatting
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.ItemStack
import java.util.Locale
import java.util.UUID

class HotbarSwapListSetting(
	name: String,
	private val setupAction: (HotbarSwapListSetting, Swap) -> Unit,
	supplier: (() -> Boolean)? = null,
	onEdit: (() -> Unit)? = null
) : Setting<MutableList<HotbarSwapListSetting.Swap>>(name, supplier, onEdit) {
	override var value: MutableList<Swap> = mutableListOf()

	init {
		defaultValue = mutableListOf()
	}

	fun addSwap(): Swap {
		val swap = Swap()
		value.add(swap)
		onEdit()
		return swap
	}

	fun removeSwap(swap: Swap) {
		value.removeAll { it.id == swap.id }
		onEdit()
	}

	fun openSetup(swap: Swap) {
		setupAction(this, swap)
	}

	override fun loadFromJson(obj: JsonObject) {
		val array = obj.getAsJsonArray("value") ?: obj.getAsJsonArray("swaps") ?: JsonArray()
		value = mutableListOf()
		for (element in array) {
			val swapObj = element.asJsonObject
			val swap = Swap(
				id = swapObj.get("id")?.asString?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
				trigger = HotbarSwapTrigger.fromConfig(swapObj.get("trigger")?.asString ?: swapObj.get("event")?.asString),
				swapType = HotbarSwapType.fromConfig(swapObj.get("swapType")?.asString ?: swapObj.get("swap_type")?.asString),
				autoClose = swapObj.get("autoClose")?.asBoolean ?: swapObj.get("auto_close")?.asBoolean ?: false,
				keybind = Keybind(swapObj.get("keybind")?.asString ?: "key.keyboard.unknown")
			)

			val pairs = swapObj.getAsJsonArray("pairs") ?: JsonArray()
			for (pairElement in pairs) {
				val pairObj = pairElement.asJsonObject
				val hotbar = pairObj.get("hotbar")?.asInt ?: continue
				val inventory = pairObj.get("inventory")?.asInt ?: continue
				if (hotbar in HOTBAR_RANGE && inventory in MAIN_INVENTORY_RANGE) {
					swap.pairs.add(SlotPair(hotbar, inventory, ItemSelector.fromJson(pairObj.getAsJsonObject("item"))))
				}
			}

			swap.sortPairs()
			value.add(swap)
		}
	}

	override fun saveToJson(obj: JsonObject) {
		obj.addProperty("name", name)
		obj.addProperty("type", type)
		val array = JsonArray()
		for (swap in value) {
			val swapObj = JsonObject()
			swapObj.addProperty("id", swap.id)
			swapObj.addProperty("trigger", swap.trigger.name)
			swapObj.addProperty("swapType", swap.swapType.name)
			swapObj.addProperty("autoClose", swap.autoClose)
			swapObj.addProperty("keybind", swap.keybind.keyName)

			val pairs = JsonArray()
			for (pair in swap.pairs) {
				val pairObj = JsonObject()
				pairObj.addProperty("hotbar", pair.hotbarSlot)
				pairObj.addProperty("inventory", pair.inventorySlot)
				pair.item?.let { pairObj.add("item", it.toJson()) }
				pairs.add(pairObj)
			}
			swapObj.add("pairs", pairs)
			array.add(swapObj)
		}
		obj.add("value", array)
	}

	override val type: String = "hotbar_swaps"

	override val displayValue: String
		get() = "${value.size} swaps"

	class Swap(
		var id: String = UUID.randomUUID().toString(),
		var trigger: HotbarSwapTrigger = HotbarSwapTrigger.KEYBIND,
		var swapType: HotbarSwapType = HotbarSwapType.SLOT,
		var autoClose: Boolean = false,
		val keybind: Keybind = Keybind(),
		val pairs: MutableList<SlotPair> = mutableListOf()
	) {
		fun addOrReplacePair(firstSlot: Int, secondSlot: Int, firstStack: ItemStack? = null): Boolean {
			val firstHotbar = firstSlot in HOTBAR_RANGE
			val secondHotbar = secondSlot in HOTBAR_RANGE
			val firstMain = firstSlot in MAIN_INVENTORY_RANGE
			val secondMain = secondSlot in MAIN_INVENTORY_RANGE
			if (firstHotbar == secondHotbar || firstMain == secondMain) {
				return false
			}

			val selectedItem = ItemSelector.fromStack(firstStack)
			if (swapType == HotbarSwapType.ITEM && selectedItem == null) {
				return false
			}
			if (swapType == HotbarSwapType.ITEM && !secondHotbar) {
				return false
			}

			val pair = if (firstHotbar) {
				SlotPair(firstSlot, secondSlot, selectedItem)
			} else {
				SlotPair(secondSlot, firstSlot, selectedItem)
			}

			pairs.removeAll { it.hotbarSlot == pair.hotbarSlot || it.inventorySlot == pair.inventorySlot }
			pairs.add(pair)
			sortPairs()
			return true
		}

		fun removePairContaining(slot: Int): Boolean =
			pairs.removeIf { it.hotbarSlot == slot || it.inventorySlot == slot }

		fun pairForSlot(slot: Int): SlotPair? =
			pairs.firstOrNull { it.hotbarSlot == slot || it.inventorySlot == slot }

		fun sortPairs() {
			pairs.sortWith(compareBy<SlotPair> { it.hotbarSlot }.thenBy { it.inventorySlot })
		}
	}

	data class SlotPair(
		var hotbarSlot: Int = 0,
		var inventorySlot: Int = 9,
		var item: ItemSelector? = null
	)

	data class ItemSelector(
		var itemId: String = "",
		var skyBlockId: String = "",
		var displayName: String = ""
	) {
		fun matches(stack: ItemStack): Boolean {
			if (stack.isEmpty) {
				return false
			}

			val stackSkyBlockId = ItemUtils.skyBlockId(stack)
			if (skyBlockId.isNotBlank() && stackSkyBlockId == skyBlockId) {
				return true
			}
			if (skyBlockId.isNotBlank()) {
				return false
			}

			val stackItemId = itemId(stack)
			if (itemId.isNotBlank() && itemId != stackItemId) {
				return false
			}

			val stackName = cleanName(stack.hoverName.string)
			return displayName.isBlank() || displayName == stackName
		}

		fun toJson(): JsonObject {
			val obj = JsonObject()
			obj.addProperty("itemId", itemId)
			obj.addProperty("skyBlockId", skyBlockId)
			obj.addProperty("displayName", displayName)
			return obj
		}

		fun label(): String =
			skyBlockId.ifBlank { displayName.ifBlank { itemId.ifBlank { "Unknown item" } } }

		companion object {
			fun fromStack(stack: ItemStack?): ItemSelector? {
				if (stack == null || stack.isEmpty) {
					return null
				}

				return ItemSelector(
					itemId = itemId(stack),
					skyBlockId = ItemUtils.skyBlockId(stack),
					displayName = cleanName(stack.hoverName.string)
				)
			}

			fun fromJson(obj: JsonObject?): ItemSelector? {
				if (obj == null) {
					return null
				}

				val selector = ItemSelector(
					itemId = obj.get("itemId")?.asString ?: obj.get("item_id")?.asString ?: "",
					skyBlockId = obj.get("skyBlockId")?.asString ?: obj.get("skyblockId")?.asString ?: obj.get("sky_block_id")?.asString ?: "",
					displayName = obj.get("displayName")?.asString ?: obj.get("display_name")?.asString ?: ""
				)
				return selector.takeIf {
					it.itemId.isNotBlank() || it.skyBlockId.isNotBlank() || it.displayName.isNotBlank()
				}
			}

			private fun itemId(stack: ItemStack): String =
				BuiltInRegistries.ITEM.getKey(stack.item).toString()

			private fun cleanName(name: String): String =
				(ChatFormatting.stripFormatting(name) ?: name).trim()
		}
	}

	companion object {
		val HOTBAR_RANGE = 0..8
		val MAIN_INVENTORY_RANGE = 9..35

		fun friendlyKeyName(keyName: String): String {
			if (keyName == "key.keyboard.unknown") return "None"
			return keyName
				.removePrefix("key.keyboard.")
				.removePrefix("key.mouse.")
				.replace("mouse.", "Mouse ")
				.replace("_", " ")
				.replaceFirstChar { it.titlecase(Locale.ROOT) }
		}
	}
}

enum class HotbarSwapType(val displayName: String) {
	SLOT("Normal"),
	ITEM("Item"),
	DEV_ONLY("Dev Only");

	companion object {
		fun fromConfig(value: String?): HotbarSwapType {
			val normalized = value
				?.trim()
				?.replace(" ", "_")
				?.replace("-", "_")
				?.uppercase(Locale.ROOT)
				?: return SLOT
			return entries.firstOrNull { it.name == normalized || it.displayName.equals(value, ignoreCase = true) } ?: SLOT
		}
	}
}

enum class HotbarSwapTrigger(val displayName: String) {
	TRAP("Trap"),
	P1("p1"),
	P2("p2"),
	S1("s1"),
	S2("s2"),
	S3("s3"),
	S4("s4"),
	P4("p4"),
	P5("p5"),
	START_ROOM("Starting room"),
	KEYBIND("Keybind");

	companion object {
		fun fromConfig(value: String?): HotbarSwapTrigger {
			val normalized = value
				?.trim()
				?.replace(" ", "_")
				?.replace("-", "_")
				?.uppercase(Locale.ROOT)
				?: return KEYBIND
			return entries.firstOrNull { it.name == normalized || it.displayName.equals(value, ignoreCase = true) } ?: KEYBIND
		}
	}
}
