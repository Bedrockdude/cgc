package cgc.cgc.dungeon

import cgc.cgc.data.DungeonClass
import cgc.cgc.data.DungeonPlayer
import cgc.cgc.data.Phase7
import cgc.cgc.location.Floor
import cgc.cgc.location.Island
import cgc.cgc.location.Location
import cgc.cgc.utils.DungeonUtils
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import java.util.Locale
import java.util.regex.Pattern

object DungeonState {
	private val tabListPattern = Pattern.compile("^\\[(?<sbLevel>\\d+)] (?:\\[?\\w+] )*(?<name>\\w+) .*?\\((?<class>\\w+)(?: (?<classLevel>\\w+))*\\)$")
	private val terminalPhasePattern = Pattern.compile(
		"(?i)^.*?(?:activated|completed) (?:a )?(?:terminal|device|lever)!?\\s*\\((\\d+)/(\\d+)\\)"
	)
	private val players = linkedSetOf<DungeonPlayer>()
	private var inP3: Boolean = false
	private var p3SectionIndex: Int = -1
	private var p3SectionAdvanceAwaitingGate = false
	private var lastP3GateSignalAtMs = Long.MIN_VALUE
	private var lastDungeonStartAtMs: Long = Long.MIN_VALUE

	@JvmStatic
	var runSequence: Long = 0L
		private set

	@JvmStatic
	var started: Boolean = false
		private set

	@JvmStatic
	var inBoss: Boolean = false
		private set

	@JvmStatic
	var f7Phase: Phase7 = Phase7.UNKNOWN
		private set

	@JvmStatic
	var p3Section: Phase7 = Phase7.UNKNOWN
		private set

	@JvmStatic
	var lastF7PhaseStart: Phase7 = Phase7.UNKNOWN
		private set

	@JvmStatic
	var lastF7PhaseStartSequence: Long = 0L
		private set

	@JvmStatic
	fun reset() {
		lastDungeonStartAtMs = Long.MIN_VALUE
		started = false
		inBoss = false
		inP3 = false
		p3SectionIndex = -1
		p3SectionAdvanceAwaitingGate = false
		lastP3GateSignalAtMs = Long.MIN_VALUE
		f7Phase = Phase7.UNKNOWN
		p3Section = Phase7.UNKNOWN
		lastF7PhaseStart = Phase7.UNKNOWN
		lastF7PhaseStartSequence = 0L
		players.clear()
		DungeonPuzzleStateTracker.resetTransient()
	}

	@JvmStatic
	fun tick(client: Minecraft) {
		val player = client.player ?: return
		if (!Location.area.isArea(Island.DUNGEON)) {
			inBoss = false
			return
		}

		inBoss = isInBossArea(Location.floor, player.position())
		players.forEach { it.findPlayer() }
		DungeonPuzzleStateTracker.tick(client, runSequence)
	}

	@JvmStatic
	fun handleChat(message: String) {
		val text = ChatFormatting.stripFormatting(message)?.trim() ?: message.trim()
		if (text.startsWith(MORT_DUNGEON_START)) {
			noteDungeonStart(monotonicNowMs())
			started = true
			inBoss = false
			return
		}

		if (text.startsWith("[BOSS]")) {
			inBoss = Location.area.isArea(Island.DUNGEON)
		}

		handleF7PhaseChat(text)
	}

	@JvmStatic
	fun handlePlayerInfo(packet: ClientboundPlayerInfoUpdatePacket) {
		if (!Location.area.isArea(Island.DUNGEON)) {
			return
		}

		val level = Minecraft.getInstance().level ?: return
		for (entry in packet.entries()) {
			val displayName = entry.displayName() ?: continue
			val text = ChatFormatting.stripFormatting(displayName.string.trim()) ?: continue
			val matcher = tabListPattern.matcher(text)
			if (!matcher.find()) {
				continue
			}

			val name = matcher.group("name")
			val clazz = DungeonClass.findClassString(matcher.group("class"))
			val classLevel = parseClassLevel(matcher.group("classLevel"))
			val player = findLevelPlayer(level, name)
			if (player == null) {
				getPlayer(name)?.update(clazz, classLevel)
				continue
			}

			val existing = getPlayer(player)
			if (existing == null) {
				players.add(DungeonPlayer(clazz, player, classLevel, 0))
			} else {
				existing.update(clazz, classLevel)
			}
		}
	}

	@JvmStatic
	fun getPlayers(): Set<DungeonPlayer> =
		players.toSet()

	@JvmStatic
	fun getMyPlayer(): DungeonPlayer? {
		val local = Minecraft.getInstance().player ?: return null
		return players.firstOrNull { it.name.equals(local.name.string, ignoreCase = true) }
	}

	@JvmStatic
	fun getPlayer(name: String): DungeonPlayer? =
		players.firstOrNull { it.name.equals(name, ignoreCase = true) }

	@JvmStatic
	fun getPlayer(player: Player): DungeonPlayer? =
		players.firstOrNull { it.player == player || it.name.equals(player.name.string, ignoreCase = true) }

	@JvmStatic
	fun getClassPlayer(clazz: DungeonClass): DungeonPlayer? =
		players.firstOrNull { it.dungeonClass.sameClass(clazz) }

	@JvmStatic
	fun getClassPlayer(index: Int): DungeonPlayer? =
		getClassPlayer(
			when (index) {
				0 -> DungeonClass.ARCHER
				1 -> DungeonClass.MAGE
				2 -> DungeonClass.BERSERKER
				3 -> DungeonClass.HEALER
				4 -> DungeonClass.TANK
				else -> DungeonClass.NONE
			}
		)

	private fun isInBossArea(floor: Floor, pos: Vec3): Boolean =
		when (floor) {
			Floor.F1, Floor.M1 -> pos.x > -70.0 && pos.z > -40.0
			Floor.F2, Floor.M2, Floor.F3, Floor.M3, Floor.F4, Floor.M4 -> pos.x > -40.0 && pos.z > -40.0
			Floor.F5, Floor.M5, Floor.F6, Floor.M6 -> pos.x > -40.0 && pos.z > -8.0
			Floor.F7, Floor.M7 -> DungeonUtils.isPositionInF7Boss(pos)
			else -> false
		}

	private fun handleF7PhaseChat(text: String) {
		if (!Location.area.isArea(Island.DUNGEON) || !(Location.floor == Floor.F7 || Location.floor == Floor.M7)) {
			return
		}

		when (text) {
			"[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!" -> {
				inP3 = false
				p3SectionIndex = -1
				p3SectionAdvanceAwaitingGate = false
				p3Section = Phase7.UNKNOWN
				noteF7PhaseStart(Phase7.P1)
				return
			}
			"[BOSS] Maxor: I'M TOO YOUNG TO DIE AGAIN!" -> {
				inP3 = false
				p3SectionIndex = -1
				p3SectionAdvanceAwaitingGate = false
				p3Section = Phase7.UNKNOWN
				noteF7PhaseStart(Phase7.P2)
				return
			}
			"[BOSS] Goldor: Who dares trespass into my domain?" -> {
				f7Phase = Phase7.P3
				inP3 = true
				p3SectionIndex = 0
				p3SectionAdvanceAwaitingGate = false
				p3Section = Phase7.S1
				noteF7PhaseStart(Phase7.S1)
				return
			}
			"The gate has been destroyed!" -> {
				val now = monotonicNowMs()
				val duplicate = lastP3GateSignalAtMs != Long.MIN_VALUE
					&& now - lastP3GateSignalAtMs < P3_GATE_DUPLICATE_WINDOW_MS
				lastP3GateSignalAtMs = now
				if (inP3 && !duplicate) {
					if (p3SectionAdvanceAwaitingGate) {
						// The completed counter already advanced this boundary.
						p3SectionAdvanceAwaitingGate = false
					} else {
						advanceP3Section()
					}
				}
				return
			}
			"The Core entrance is opening!" -> {
				inP3 = false
				p3SectionAdvanceAwaitingGate = false
				return
			}
			"[BOSS] Necron: I'm afraid, your journey ends now." -> {
				inP3 = false
				p3SectionIndex = -1
				p3SectionAdvanceAwaitingGate = false
				p3Section = Phase7.UNKNOWN
				noteF7PhaseStart(Phase7.P4)
				return
			}
			"[BOSS] Wither King: I no longer wish to fight, but I know that will not stop you." -> {
				inP3 = false
				p3SectionIndex = -1
				p3SectionAdvanceAwaitingGate = false
				p3Section = Phase7.UNKNOWN
				noteF7PhaseStart(Phase7.P5)
				return
			}
		}

		if (!inP3) {
			return
		}

		val matcher = terminalPhasePattern.matcher(text)
		if (!matcher.find()) {
			return
		}
		val completed = matcher.group(1).toIntOrNull() ?: return
		val total = matcher.group(2).toIntOrNull() ?: return
		if (completed != total) {
			// A new/incomplete counter proves any prior gate acknowledgement is no
			// longer relevant to the current section.
			p3SectionAdvanceAwaitingGate = false
			return
		}

		if (advanceP3Section()) {
			p3SectionAdvanceAwaitingGate = true
		}
	}

	private fun advanceP3Section(): Boolean {
		val nextIndex = p3SectionIndex + 1
		val nextSection = p3SectionFromIndex(nextIndex)
		if (nextSection == p3Section) {
			return false
		}
		p3SectionIndex = nextIndex
		p3Section = nextSection
		noteF7PhaseStart(nextSection)
		return true
	}

	private fun noteF7PhaseStart(phase: Phase7) {
		f7Phase = when (phase) {
			Phase7.S1, Phase7.S2, Phase7.S3, Phase7.S4 -> Phase7.P3
			else -> phase
		}
		lastF7PhaseStart = phase
		lastF7PhaseStartSequence++
	}

	internal fun noteDungeonStart(nowMs: Long): Boolean {
		if (lastDungeonStartAtMs != Long.MIN_VALUE && nowMs - lastDungeonStartAtMs < DUNGEON_START_DUPLICATE_WINDOW_MS) {
			return false
		}
		lastDungeonStartAtMs = nowMs
		runSequence++
		DungeonPuzzleStateTracker.onRunStarted(runSequence)
		return true
	}

	private fun p3SectionFromIndex(index: Int): Phase7 =
		when (index) {
			0 -> Phase7.S1
			1 -> Phase7.S2
			2 -> Phase7.S3
			3 -> Phase7.S4
			else -> Phase7.S4
		}

	private fun findLevelPlayer(level: ClientLevel, name: String): Player? =
		level.players().firstOrNull { it.name.string.equals(name, ignoreCase = true) }

	private fun parseClassLevel(raw: String?): Int {
		if (raw.isNullOrBlank()) {
			return 0
		}

		return raw.toIntOrNull() ?: romanToInt(raw.uppercase(Locale.ROOT))
	}

	private fun romanToInt(value: String): Int {
		val numerals = mapOf('I' to 1, 'V' to 5, 'X' to 10, 'L' to 50, 'C' to 100)
		var total = 0
		var previous = 0
		for (char in value.reversed()) {
			val current = numerals[char] ?: return 0
			if (current < previous) {
				total -= current
			} else {
				total += current
				previous = current
			}
		}
		return total
	}

	private const val MORT_DUNGEON_START = "[NPC] Mort: Here, I found this map when I first entered the dungeon."
	private const val DUNGEON_START_DUPLICATE_WINDOW_MS = 30_000L
	private const val P3_GATE_DUPLICATE_WINDOW_MS = 1_000L

	private fun monotonicNowMs(): Long = System.nanoTime() / 1_000_000L
}
