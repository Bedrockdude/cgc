package cgc.cgc.utils

import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.ItemLore
import java.util.regex.Pattern

object ItemUtils {
	private val DB_CHARGE_PATTERN = Pattern.compile("Charges: (\\d+)/(\\d+)")

	fun customData(item: ItemStack): CompoundTag =
		item.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()

	fun skyBlockId(item: ItemStack): String =
		customData(item).getString("id").orElse("")

	fun tunerDistance(item: ItemStack): Int =
		customData(item).getInt("tuned_transmission").orElse(0)

	fun isEtherwarp(item: ItemStack): Boolean =
		customData(item).getInt("ethermerge").orElse(0) == 1 || skyBlockId(item) == "ETHERWARP_CONDUIT"

	fun isTeleportItem(item: ItemStack): Boolean =
		when (skyBlockId(item)) {
			"ASPECT_OF_THE_END",
			"ASPECT_OF_THE_VOID",
			"ETHERWARP_CONDUIT",
			"ASPECT_OF_THE_LEECH_1",
			"ASPECT_OF_THE_LEECH_2",
			"ASPECT_OF_THE_LEECH_3",
			"NECRON_BLADE",
			"SCYLLA",
			"HYPERION",
			"VALKYRIE",
			"ASTRAEA" -> true
			else -> false
		}

	fun dungeonBreakerCharges(item: ItemStack): Pair<Int, Int> {
		val lore = item.getOrDefault(DataComponents.LORE, ItemLore.EMPTY)
		for (line in lore.lines()) {
			val text = line.string
			val matcher = DB_CHARGE_PATTERN.matcher(text)
			if (matcher.find()) {
				return matcher.group(1).toInt() to matcher.group(2).toInt()
			}
		}
		return 20 to 20
	}
}
