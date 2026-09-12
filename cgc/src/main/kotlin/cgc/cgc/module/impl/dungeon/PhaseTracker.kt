package cgc.cgc.module.impl.dungeon

import cgc.cgc.data.Colour
import cgc.cgc.data.Phase7
import cgc.cgc.dungeon.DungeonState
import cgc.cgc.location.Floor
import cgc.cgc.location.Location
import cgc.cgc.module.CgcModule
import cgc.cgc.module.ChatMessageModule
import cgc.cgc.module.ClientTickModule
import cgc.cgc.module.HudRenderModule
import cgc.cgc.module.ModuleCategory
import cgc.cgc.module.PacketReceiveModule
import cgc.cgc.module.WorldLoadModule
import cgc.cgc.module.setting.BooleanSetting
import cgc.cgc.module.setting.ColourSetting
import cgc.cgc.module.setting.DragSetting
import cgc.cgc.module.setting.NumberSetting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundBossEventPacket
import net.minecraft.network.protocol.game.ClientboundBundlePacket
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket
import net.minecraft.world.BossEvent
import org.joml.Vector2d
import java.util.UUID

class PhaseTracker : CgcModule(
	id = "phase-tracker",
	displayName = "Phase Tracker",
	category = ModuleCategory.DUNGEONS,
	description = "Shows the current F7/M7 phase, P3 section progress, and P4 Necron death state.",
	defaultEnabled = false
), ClientTickModule, ChatMessageModule, HudRenderModule, PacketReceiveModule, WorldLoadModule {
	private val hud = DragSetting(
		"Phase Tracker HUD",
		Vector2d(12.0, 70.0),
		Vector2d(HUD_WIDTH.toDouble(), HUD_HEIGHT.toDouble())
	)
	private val showP1 = BooleanSetting("Show P1", true)
	private val showP2 = BooleanSetting("Show P2", true)
	private val showP3 = BooleanSetting("Show P3", true)
	private val showP4 = BooleanSetting("Show P4", true)
	private val showP5 = BooleanSetting("Show P5", true)
	private val background = BooleanSetting("Background", true)
	private val backgroundColour = ColourSetting(
		"Background Colour",
		Colour(0, 0, 0, 150),
		supplier = { background.value }
	)
	private val phaseColour = ColourSetting("Phase Colour", Colour(255, 85, 85))
	private val progressColour = ColourSetting("Progress Colour", Colour(255, 255, 255))
	private val waitingColour = ColourSetting("Waiting Colour", Colour(255, 170, 0))
	private val doneColour = ColourSetting("Done Colour", Colour(85, 255, 85))
	private val textShadow = BooleanSetting("Text Shadow", true)
	private val doneDisplayTime = NumberSetting("Phase Done Time", 0.25, 5.0, 1.5, 0.25, " s")

	private val state = PhaseTrackerState()
	private val bossBarNames = hashMapOf<UUID, String>()

	private val bossBarHandler = object : ClientboundBossEventPacket.Handler {
		override fun add(
			id: UUID,
			name: Component,
			progress: Float,
			colour: BossEvent.BossBarColor,
			overlay: BossEvent.BossBarOverlay,
			darkenScreen: Boolean,
			playMusic: Boolean,
			createWorldFog: Boolean
		) {
			runOnClientThread {
				val text = name.string
				bossBarNames[id] = text
				state.handleNecronBossBar(text, progress)
			}
		}

		override fun remove(id: UUID) {
			runOnClientThread {
				state.handleNecronBossBar(bossBarNames.remove(id), 0.0f)
			}
		}

		override fun updateProgress(id: UUID, progress: Float) {
			runOnClientThread {
				state.handleNecronBossBar(bossBarNames[id], progress)
			}
		}

		override fun updateName(id: UUID, name: Component) {
			runOnClientThread {
				bossBarNames[id] = name.string
			}
		}

		override fun updateStyle(id: UUID, colour: BossEvent.BossBarColor, overlay: BossEvent.BossBarOverlay) {
		}

		override fun updateProperties(id: UUID, darkenScreen: Boolean, playMusic: Boolean, createWorldFog: Boolean) {
		}
	}

	init {
		registerProperty(
			hud,
			showP1,
			showP2,
			showP3,
			showP4,
			showP5,
			background,
			backgroundColour,
			phaseColour,
			progressColour,
			waitingColour,
			doneColour,
			textShadow,
			doneDisplayTime
		)
	}

	override fun onClientTick(client: Minecraft) {
		synchronizeWithDungeonState()
		state.tick(System.currentTimeMillis(), (doneDisplayTime.value.toDouble() * 1_000.0).toLong())
	}

	override fun onChatMessage(message: String) {
		state.handleMessage(message, System.currentTimeMillis())
	}

	override fun onPacketReceive(packet: Packet<*>): Boolean {
		observePacket(packet)
		return false
	}

	override fun onHudRender(gfx: GuiGraphicsExtractor) {
		val snapshot = state.snapshot()
		if (!shouldShow(snapshot.phase)) {
			return
		}

		val client = Minecraft.getInstance()
		if (client.player == null || client.level == null) {
			return
		}

		val (header, detail) = displayText(snapshot)
		val detailColour = detailColour(snapshot)
		val x = hud.position.x.toInt()
		val y = hud.position.y.toInt()
		if (background.value) {
			gfx.fill(x, y, x + HUD_WIDTH, y + HUD_HEIGHT, backgroundColour.value.argb())
		}

		if (header.isBlank()) {
			drawCentered(gfx, detail, x, y + SINGLE_LINE_Y, detailColour)
		} else {
			drawCentered(gfx, header, x, y + HEADER_Y, phaseColour.value.argb())
			drawCentered(gfx, detail, x, y + DETAIL_Y, detailColour)
		}
	}

	override fun onWorldLoad() {
		resetTracker()
	}

	override fun reset() {
		resetTracker()
	}

	private fun observePacket(packet: Packet<*>) {
		when (packet) {
			is ClientboundBundlePacket -> packet.subPackets().forEach(::observePacket)
			is ClientboundSetSubtitleTextPacket -> {
				val message = packet.text.string
				runOnClientThread {
					state.handleMessage(message, System.currentTimeMillis())
				}
			}
			is ClientboundBossEventPacket -> packet.dispatch(bossBarHandler)
		}
	}

	private fun synchronizeWithDungeonState() {
		if ((Location.floor != Floor.F7 && Location.floor != Floor.M7) || !DungeonState.inBoss) {
			return
		}

		val phase = when (DungeonState.f7Phase) {
			Phase7.P1 -> TrackedBossPhase.P1
			Phase7.P2 -> TrackedBossPhase.P2
			Phase7.P3, Phase7.S1, Phase7.S2, Phase7.S3, Phase7.S4 -> TrackedBossPhase.P3
			Phase7.P4 -> TrackedBossPhase.P4
			Phase7.P5 -> TrackedBossPhase.P5
			Phase7.UNKNOWN -> TrackedBossPhase.NONE
		}
		val section = when (DungeonState.p3Section) {
			Phase7.S1 -> 1
			Phase7.S2 -> 2
			Phase7.S3 -> 3
			Phase7.S4 -> 4
			else -> 1
		}
		state.synchronize(phase, section)
	}

	private fun displayText(snapshot: PhaseTrackerSnapshot): Pair<String, String> =
		when (snapshot.phase) {
			TrackedBossPhase.P1 -> "Phase 1" to "Maxor"
			TrackedBossPhase.P2 -> "Phase 2" to "Storm"
			TrackedBossPhase.P3 -> {
				val progress = when (snapshot.p3State) {
					P3ProgressState.ACTIVE -> "Terminals: ${snapshot.completed}/${snapshot.total ?: "?"}"
					P3ProgressState.WAITING_FOR_GATE -> "Waiting for Gate"
					P3ProgressState.DONE -> "Phase Done"
				}
				"" to progress
			}
			TrackedBossPhase.P4 -> "" to if (snapshot.necronDead) "Necron Dead" else "Necron Alive"
			TrackedBossPhase.P5 -> "Phase 5" to "Wither King"
			TrackedBossPhase.NONE -> "" to ""
		}

	private fun detailColour(snapshot: PhaseTrackerSnapshot): Int =
		when {
			snapshot.phase == TrackedBossPhase.P4 && snapshot.necronDead -> doneColour.value.argb()
			snapshot.phase == TrackedBossPhase.P3 && snapshot.p3State == P3ProgressState.DONE -> doneColour.value.argb()
			snapshot.phase == TrackedBossPhase.P3 && snapshot.p3State == P3ProgressState.WAITING_FOR_GATE -> waitingColour.value.argb()
			else -> progressColour.value.argb()
		}

	private fun shouldShow(phase: TrackedBossPhase): Boolean =
		when (phase) {
			TrackedBossPhase.P1 -> showP1.value
			TrackedBossPhase.P2 -> showP2.value
			TrackedBossPhase.P3 -> showP3.value
			TrackedBossPhase.P4 -> showP4.value
			TrackedBossPhase.P5 -> showP5.value
			TrackedBossPhase.NONE -> false
		}

	private fun drawCentered(gfx: GuiGraphicsExtractor, text: String, x: Int, y: Int, colour: Int) {
		val font = Minecraft.getInstance().font
		gfx.text(font, text, x + (HUD_WIDTH - font.width(text)) / 2, y, colour, textShadow.value)
	}

	private fun runOnClientThread(action: () -> Unit) {
		Minecraft.getInstance().execute(action)
	}

	private fun resetTracker() {
		state.reset()
		bossBarNames.clear()
	}

	private companion object {
		private const val HUD_WIDTH = 150
		private const val HUD_HEIGHT = 36
		private const val HEADER_Y = 6
		private const val DETAIL_Y = 20
		private const val SINGLE_LINE_Y = 13
	}
}
