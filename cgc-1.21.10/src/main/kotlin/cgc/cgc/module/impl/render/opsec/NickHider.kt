package cgc.cgc.module.impl.render.opsec

import cgc.cgc.module.SubModule
import cgc.cgc.module.setting.StringSetting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentContents
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.contents.PlainTextContents
import net.minecraft.util.FormattedCharSequence
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern

class NickHider(module: OpSec) : SubModule<OpSec>(module, "Nick Hider", true) {
	private val fakeName = StringSetting("Name", "", allowBlank = true, maxLength = 64)

	init {
		instance = this
		registerProperty(fakeName)
	}

	private fun active(): Boolean =
		module.enabled && enabled

	private fun playerName(): String? =
		Minecraft.getInstance().player?.name?.string

	private fun replacement(): String =
		fakeName.value

	companion object {
		private var instance: NickHider? = null

		@JvmStatic
		fun modifyString(text: String?): String? {
			val hider = instance ?: return text
			if (!hider.active() || text.isNullOrBlank()) {
				return text
			}

			val playerName = hider.playerName()
			if (playerName.isNullOrEmpty() || !text.contains(playerName)) {
				return text
			}

			return text.replace(playerName, hider.replacement())
		}

		@JvmStatic
		fun modifyComponent(component: Component?): Component? {
			val hider = instance ?: return component
			if (!hider.active() || component == null) {
				return component
			}

			val playerName = hider.playerName()
			if (playerName.isNullOrEmpty() || !component.string.contains(playerName)) {
				return component
			}

			return rebuildComponent(component, playerName, hider.replacement())
		}

		@JvmStatic
		fun modifyCharSeq(seq: FormattedCharSequence?): FormattedCharSequence? {
			val hider = instance ?: return seq
			if (!hider.active() || seq == null) {
				return seq
			}

			val text = StringBuilder()
			seq.accept { _, _, codePoint ->
				text.append(codePoint.toChar())
				true
			}

			val playerName = hider.playerName()
			if (playerName.isNullOrEmpty() || !text.toString().contains(playerName)) {
				return seq
			}

			val rebuilt = Component.literal("")
			val currentStyle = AtomicReference<Style>()
			val buffer = StringBuilder()

			seq.accept { _, style, codePoint ->
				if (style !== currentStyle.get()) {
					flushStyledBuffer(rebuilt, buffer, currentStyle.get())
					currentStyle.set(style)
				}
				buffer.append(codePoint.toChar())
				true
			}

			flushStyledBuffer(rebuilt, buffer, currentStyle.get())
			return (modifyComponent(rebuilt) ?: rebuilt).visualOrderText
		}

		private fun rebuildComponent(component: Component, playerName: String, replacement: String): MutableComponent {
			val contents: ComponentContents = component.contents
			val rebuilt = if (contents is PlainTextContents && contents.text().contains(playerName)) {
				injectReplacement(contents.text(), playerName, replacement, component.style)
			} else {
				val copy = component.copy()
				copy.siblings.clear()
				copy
			}

			if (rebuilt.style.isEmpty) {
				rebuilt.style = component.style
			}

			for (sibling in component.siblings) {
				rebuilt.append(rebuildComponent(sibling, playerName, replacement))
			}

			return rebuilt
		}

		private fun injectReplacement(text: String, target: String, replacement: String, style: Style): MutableComponent {
			val root = Component.literal("")
			val parts = text.split(Pattern.quote(target).toRegex(), limit = 2)

			if (parts[0].isNotEmpty()) {
				root.append(Component.literal(parts[0]).withStyle(style))
			}

			root.append(Component.literal(replacement).withStyle(style))

			if (parts.size > 1 && parts[1].isNotEmpty()) {
				val remaining = parts[1]
				if (remaining.contains(target)) {
					root.append(injectReplacement(remaining, target, replacement, style))
				} else {
					root.append(Component.literal(remaining).withStyle(style))
				}
			}

			return root
		}

		private fun flushStyledBuffer(root: MutableComponent, buffer: StringBuilder, style: Style?) {
			if (buffer.isEmpty()) {
				return
			}

			val component = Component.literal(buffer.toString())
			if (style != null) {
				component.withStyle(style)
			}
			root.append(component)
			buffer.setLength(0)
		}
	}
}
