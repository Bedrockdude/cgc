package cgc.cgc.module.impl.render.opsec

import cgc.cgc.module.SubModule
import cgc.cgc.module.setting.StringSetting
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentContents
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.contents.PlainTextContents
import net.minecraft.util.FormattedCharSequence
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern

class NickHider(module: OpSec) : SubModule<OpSec>(module, "Nick Hider", true) {
	private val fakeName = StringSetting(
		"Name",
		"",
		allowBlank = true,
		maxLength = 8192,
		pasteButton = true
	)
	private var cachedReplacementInput: String? = null
	private var cachedReplacement: Component = Component.empty()

	init {
		instance = this
		registerProperty(fakeName)
	}

	private fun active(): Boolean =
		module.enabled && enabled

	private fun playerName(): String? =
		Minecraft.getInstance().player?.name?.string

	private fun replacement(): Component {
		val input = fakeName.value
		if (input != cachedReplacementInput) {
			cachedReplacementInput = input
			cachedReplacement = parseCustomName(input)
		}
		return cachedReplacement
	}

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

			return text.replace(playerName, hider.replacement().string)
		}

		/**
		 * Returns a formatted replacement for a plain font string, or null when the
		 * string does not need changing. The font mixin uses the nullable result to
		 * switch to Minecraft's styled text rendering overload only when necessary.
		 */
		@JvmStatic
		fun modifyStringAsCharSeq(text: String?): FormattedCharSequence? {
			val hider = instance ?: return null
			if (!hider.active() || text.isNullOrBlank()) {
				return null
			}

			val playerName = hider.playerName()
			if (playerName.isNullOrEmpty() || !text.contains(playerName)) {
				return null
			}

			// Converting through visualOrderText first preserves legacy section-sign
			// formatting in ordinary String draw calls before the name is replaced.
			return modifyCharSeq(Component.literal(text).visualOrderText)
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
				text.appendCodePoint(codePoint)
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
				buffer.appendCodePoint(codePoint)
				true
			}

			flushStyledBuffer(rebuilt, buffer, currentStyle.get())
			return rebuildComponent(rebuilt, playerName, hider.replacement()).visualOrderText
		}

		internal fun parseCustomName(input: String): Component {
			val parsed = runCatching {
				ComponentSerialization.CODEC
					.parse(JsonOps.INSTANCE, JsonParser.parseString(input))
					.result()
					.orElse(null)
			}.getOrNull()

			return parsed ?: Component.literal(input)
		}

		private fun rebuildComponent(component: Component, playerName: String, replacement: Component): MutableComponent {
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

		private fun injectReplacement(text: String, target: String, replacement: Component, style: Style): MutableComponent {
			val root = Component.literal("")
			val parts = text.split(Pattern.quote(target).toRegex(), limit = 2)

			if (parts[0].isNotEmpty()) {
				root.append(Component.literal(parts[0]).withStyle(style))
			}

			root.append(Component.empty().withStyle(style).append(replacement.copy()))

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
