package cgc.cgc.utils

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

object ChatUtils {
	fun chat(message: String) {
		Minecraft.getInstance().player?.sendSystemMessage(Component.literal(message))
	}

	fun actionBar(message: String) {
		Minecraft.getInstance().player?.sendOverlayMessage(Component.literal(message))
	}

	fun chat(component: Component) {
		Minecraft.getInstance().player?.sendSystemMessage(component)
	}

	fun chatClean(message: String) {
		chat(
			Component.empty()
				.append(Component.literal("[").withStyle(net.minecraft.ChatFormatting.GOLD))
				.append(Component.literal("byebyebalding").withStyle(net.minecraft.ChatFormatting.DARK_GRAY))
				.append(Component.literal("] ").withStyle(net.minecraft.ChatFormatting.GOLD))
				.append(Component.literal(message).withStyle(net.minecraft.ChatFormatting.WHITE))
		)
	}
}
