package cgc.cgc.module.impl.dungeon

import cgc.cgc.data.Colour
import cgc.cgc.mixin.accessor.AccessorAbstractContainerScreen
import cgc.cgc.module.CgcModule
import cgc.cgc.module.ModuleCategory
import cgc.cgc.module.PacketSendModule
import cgc.cgc.module.SubModule
import cgc.cgc.module.WorldLoadModule
import cgc.cgc.module.setting.BooleanSetting
import cgc.cgc.module.setting.ColourSetting
import cgc.cgc.module.setting.ModeSetting
import cgc.cgc.module.setting.MultiBoolSetting
import cgc.cgc.module.setting.NumberSetting
import cgc.cgc.module.setting.StringSetting
import cgc.cgc.module.setting.group.GroupSetting
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.protocol.Packet

class TerminalSolver : CgcModule(
	id = "TerminalSolver",
	displayName = "Terminal solver",
	category = ModuleCategory.DUNGEONS,
	description = "Provides terminal-solver options used by CGC terminal modules.",
	defaultEnabled = false,
	visibleInGui = false
), PacketSendModule, WorldLoadModule {
	private val terminalsSetting = MultiBoolSetting(
		"Terminals",
		listOf("Melody", "Order", "Panes", "Rubix", "Select", "Starts With"),
		listOf("Melody", "Order", "Panes", "Rubix", "Select", "Starts With")
	)
	private val blockAllSetting = BooleanSetting("Block All Clicks", false)
	private val modeSetting = ModeSetting("Mode", "Hide Clicked", listOf("Normal", "Hide Clicked", "Zero Ping", "Queue"))
	private val canClickSetting = BooleanSetting("Can Click", false)
	private val lockRubixSetting = BooleanSetting("Lock Rubix", true)
	private val anyClickRubixSetting = BooleanSetting("Any Click Rubix", false)
	private val offTickSlotsSetting = BooleanSetting("Off Tick Slots", false)
	private val orderNumbersSetting = BooleanSetting("Render order numbers", true)
	private val melodyBlockSetting = BooleanSetting("Block melody clicks", false)
	private val melodyEdgesSetting = BooleanSetting("Allow Edges on melody", false)

	private val timingGroup = GroupSetting("Timing", SubModule(this, "Timing", true))
	private val firstDelaySetting = NumberSetting("First Click", 0.0, 500.0, 400.0, 10.0, " ms")
	private val clickDelaySetting = NumberSetting("Forced Delay", 110.0, 150.0, 120.0, 1.0, " ms")
	private val timeoutSetting = NumberSetting("Timeout", 0.0, 1000.0, 500.0, 50.0, " ms")
	private val forcedFirstClickSetting = NumberSetting("Forced Firstclick", 0.0, 500.0, 400.0, 10.0, " ms")
	private val scaleSetting = NumberSetting("Scale", 0.2, 5.0, 1.0, 0.1)
	private val gapSetting = NumberSetting("Gap", 0.0, 5.0, 2.0, 0.1)

	private val titleGroup = GroupSetting("Titles", SubModule(this, "Titles", true))
	private val titlesSetting = BooleanSetting("Render Titles", false)
	private val orderTitleSetting = StringSetting("Order Title", "")
	private val panesTitleSetting = StringSetting("Panes Title", "")
	private val selectTitleSetting = StringSetting("Select Title", "")
	private val rubixTitleSetting = StringSetting("Rubix Title", "")
	private val startsTitleSetting = StringSetting("Starts With Title", "")
	private val melodyTitleSetting = StringSetting("Melody Title", "")

	private val statsGroup = GroupSetting("Stats", SubModule(this, "Stats", true))
	private val terminalTimeSetting = BooleanSetting("Send terminal time", false)
	private val statsSetting = MultiBoolSetting(
		"Chat Stats",
		listOf("Personal Best", "Average Click", "First Click", "CPS"),
		listOf("Personal Best"),
		supplier = { terminalTimeSetting.value }
	)

	private val coloursGroup = GroupSetting("Colours", SubModule(this, "Colours", true))
	private val backgroundSetting = ColourSetting("Background", Colour(0, 0, 12, 217))
	private val textColourSetting = ColourSetting("Text Colour", Colour(220, 220, 220))
	private val panesColourSetting = ColourSetting("Panes", Colour(144, 76, 56, 170))
	private val rubixColourSetting = ColourSetting("Rubix", Colour(144, 76, 56, 170))
	private val oppositeRubixColourSetting = ColourSetting("Opposite Rubix", Colour(184, 76, 56, 170))
	private val orderColourSetting = ColourSetting("Order", Colour(144, 76, 56, 170))
	private val order2ColourSetting = ColourSetting("Order 2", Colour(144, 76, 47, 128))
	private val order3ColourSetting = ColourSetting("Order 3", Colour(145, 77, 40, 96))
	private val startsWithColourSetting = ColourSetting("Starts With", Colour(144, 76, 56, 170))
	private val selectColourSetting = ColourSetting("Select", Colour(144, 76, 56, 170))
	private val canClickColourSetting = ColourSetting("Can Click", Colour(255, 192, 203, 190))
	private val melodyColumnColourSetting = ColourSetting("Mel Column", Colour(138, 43, 226, 150))
	private val melodyRowColourSetting = ColourSetting("Mel Row", Colour(0, 255, 0, 150))
	private val melodyRowLineColourSetting = ColourSetting("Mel Row Line", Colour(255, 255, 255, 150))
	private val melodyClayColourSetting = ColourSetting("Mel Clay", Colour(255, 0, 0, 150))
	private val melodyClayCorrectColourSetting = ColourSetting("Mel Clay Correct", Colour(255, 200, 0, 170))

	init {
		instance = this
		timingGroup.add(firstDelaySetting, clickDelaySetting, timeoutSetting, forcedFirstClickSetting, scaleSetting, gapSetting)
		titleGroup.add(
			titlesSetting,
			orderTitleSetting,
			panesTitleSetting,
			selectTitleSetting,
			rubixTitleSetting,
			startsTitleSetting,
			melodyTitleSetting
		)
		statsGroup.add(terminalTimeSetting, statsSetting)
		coloursGroup.add(
			backgroundSetting,
			textColourSetting,
			panesColourSetting,
			rubixColourSetting,
			oppositeRubixColourSetting,
			orderColourSetting,
			order2ColourSetting,
			order3ColourSetting,
			startsWithColourSetting,
			selectColourSetting,
			canClickColourSetting,
			melodyColumnColourSetting,
			melodyRowColourSetting,
			melodyRowLineColourSetting,
			melodyClayColourSetting,
			melodyClayCorrectColourSetting
		)
		registerProperty(
			terminalsSetting,
			blockAllSetting,
			modeSetting,
			canClickSetting,
			lockRubixSetting,
			anyClickRubixSetting,
			offTickSlotsSetting,
			orderNumbersSetting,
			melodyBlockSetting,
			melodyEdgesSetting,
			timingGroup,
			titleGroup,
			statsGroup,
			coloursGroup
		)
	}

	override fun onPacketSend(packet: Packet<*>): Boolean {
		AutoTerms.handleSendForTerminalSolver(packet)
		return false
	}

	override fun onWorldLoad() {
		AutoTerms.clearForTerminalSolver()
	}

	private fun colourFor(typeKey: String): Colour =
		when (typeKey) {
			"Order" -> orderColourSetting.value
			"Panes" -> panesColourSetting.value
			"Rubix" -> rubixColourSetting.value
			"Select" -> selectColourSetting.value
			"Starts With" -> startsWithColourSetting.value
			"Melody" -> melodyClayCorrectColourSetting.value
			else -> canClickColourSetting.value
		}

	companion object {
		private var instance: TerminalSolver? = null

		val active: Boolean
			get() = instance?.enabled == true

		val anyClickRubix: Boolean
			get() = instance?.enabled == true && instance?.anyClickRubixSetting?.value == true

		val offTickSlots: Boolean
			get() = instance?.enabled == true && instance?.offTickSlotsSetting?.value == true

		val blockAll: Boolean
			get() = instance?.enabled == true && instance?.blockAllSetting?.value == true

		val mode: String
			get() = instance?.modeSetting?.value ?: "Normal"

		val firstDelayMs: Long
			get() = instance?.firstDelaySetting?.value?.toLong() ?: 0L

		val clickDelayMs: Long
			get() = instance?.clickDelaySetting?.value?.toLong() ?: 0L

		val timeoutMs: Long
			get() = instance?.timeoutSetting?.value?.toLong() ?: 0L

		fun isTerminalEnabled(typeKey: String): Boolean {
			val solver = instance ?: return true
			if (!solver.enabled) {
				return true
			}
			return solver.terminalsSetting[typeKey]
		}

		@JvmStatic
		fun renderTerminalOverlay(screen: AbstractContainerScreen<*>, gfx: GuiGraphicsExtractor) {
			val solver = instance ?: return
			if (!solver.enabled) {
				return
			}

			val overlay = AutoTerms.terminalOverlay() ?: return
			if (screen.menu.containerId != overlay.windowId || overlay.solutionSlots.isEmpty()) {
				return
			}

			val access = screen as AccessorAbstractContainerScreen
			val left = access.`cgc$getLeftPos`()
			val top = access.`cgc$getTopPos`()
			val colour = if (solver.canClickSetting.value) solver.canClickColourSetting.value else solver.colourFor(overlay.typeKey)

			for (slot in screen.menu.slots) {
				if (slot.index !in overlay.solutionSlots) {
					continue
				}

				gfx.fill(left + slot.x, top + slot.y, left + slot.x + 16, top + slot.y + 16, colour.argb())
			}
		}
	}
}
