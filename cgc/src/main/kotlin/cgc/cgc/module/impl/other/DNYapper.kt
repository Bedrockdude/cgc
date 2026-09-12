package cgc.cgc.module.impl.other

import cgc.cgc.data.Keybind
import cgc.cgc.mixin.accessor.AccessorAbstractContainerScreen
import cgc.cgc.module.CgcModule
import cgc.cgc.module.ModuleCategory
import cgc.cgc.module.setting.KeybindSetting
import cgc.cgc.module.setting.StringSetting
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.Locale
import java.util.concurrent.ThreadLocalRandom
import java.util.regex.Pattern

class DNYapper : CgcModule(
	id = "DNYapper",
	displayName = "DN Yapper",
	category = ModuleCategory.OTHER,
	description = "Messages hovered Party Finder parties with a random configured message.",
	defaultEnabled = false
) {
	private val yapKey = KeybindSetting("Yap Key", Keybind(action = this::yap))
	private val messages = StringSetting("Messages", DEFAULT_MESSAGES, allowBlank = false, maxLength = 1024)

	init {
		registerProperty(yapKey, messages)
	}

	override fun onEnable() {
		yapKey.register()
	}

	override fun onDisable() {
		yapKey.unregister()
	}

	private fun yap() {
		val client = Minecraft.getInstance()
		if (client.player == null || client.level == null || client.connection == null) {
			return
		}

		val screen = client.screen
		if (!isPartyFinder(screen)) {
			modMessage("${ChatFormatting.RED}Open Party Finder first.")
			return
		}

		val slot = getHoveredSlot(screen)
		if (slot == null || !slot.hasItem()) {
			modMessage("${ChatFormatting.RED}Hover a party head first.")
			return
		}

		val playerName = getPartyOwner(slot.item)
		if (playerName == null) {
			modMessage("${ChatFormatting.RED}Hovered item is not a Party Finder head.")
			return
		}

		val message = getRandomMessage()
		if (message == null) {
			modMessage("${ChatFormatting.RED}Add at least one message.")
			return
		}

		client.connection?.sendCommand("msg $playerName $message")
	}

	private fun isPartyFinder(screen: Screen?): Boolean =
		screen != null && screen.title.string.lowercase(Locale.ROOT).contains("party finder")

	private fun getHoveredSlot(screen: Screen?): Slot? {
		if (screen !is AbstractContainerScreen<*>) {
			return null
		}

		return (screen as AccessorAbstractContainerScreen).`cgc$getHoveredSlot`()
	}

	private fun getPartyOwner(stack: ItemStack): String? {
		if (!stack.`is`(Items.PLAYER_HEAD)) {
			return null
		}

		val matcher = PARTY_NAME.matcher(stack.hoverName.string.trim())
		return if (matcher.matches()) matcher.group(1) else null
	}

	private fun getRandomMessage(): String? {
		val options = parseMessages(messages.value)
		if (options.isEmpty()) {
			return null
		}

		return options[ThreadLocalRandom.current().nextInt(options.size)]
	}

	private fun parseMessages(raw: String?): List<String> {
		if (raw.isNullOrBlank()) {
			return emptyList()
		}

		return raw.split(Regex("\\R|\\|"))
			.map { it.trim() }
			.filter { it.isNotEmpty() }
	}

	private fun modMessage(text: String) {
		Minecraft.getInstance().player?.sendSystemMessage(
			Component.literal("${ChatFormatting.YELLOW}DN Yapper » ${ChatFormatting.RESET}$text")
		)
	}

	private companion object {
		private val PARTY_NAME: Pattern = Pattern.compile("^([A-Za-z0-9_]{1,16})'s Party$")
		private const val DEFAULT_MESSAGES = "Hey, do you still need one? | Can I join? | Got room?"
	}
}
