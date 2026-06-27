package cgc.cgc.module.impl.dungeon

import cgc.cgc.dungeon.DungeonState
import cgc.cgc.location.Island
import cgc.cgc.location.Location
import cgc.cgc.module.CgcModule
import cgc.cgc.module.ClientTickModule
import cgc.cgc.module.ModuleCategory
import cgc.cgc.module.PacketReceiveModule
import cgc.cgc.module.PacketSendModule
import cgc.cgc.module.WorldLoadModule
import cgc.cgc.module.setting.BooleanSetting
import cgc.cgc.module.setting.ModeSetting
import cgc.cgc.module.setting.MultiBoolSetting
import cgc.cgc.module.setting.NumberSetting
import cgc.cgc.terminal.TerminalContext
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import java.util.Locale
import java.util.regex.Pattern

class AutoTerms : CgcModule(
	id = "AutoTerms",
	displayName = "AutoTerms",
	category = ModuleCategory.DUNGEONS,
	description = "Automatically clicks known F7 terminal solutions.",
	defaultEnabled = false
), ClientTickModule, PacketReceiveModule, PacketSendModule, WorldLoadModule {
	private val skyblock = ModeSetting("Skyblock", "Auto", listOf("Auto", "Dungeon", "Skyblock", "Practice"))
	private val terminals = MultiBoolSetting(
		"Terminals",
		listOf("Colours", "Melody", "Numbers", "Red Green", "Rubix", "Starts With"),
		listOf("Colours", "Melody", "Numbers", "Red Green", "Rubix", "Starts With")
	)
	private val firstClickDelay = NumberSetting("First Click Delay", 0.0, 400.0, 100.0, 5.0, " ms")
	private val delay = NumberSetting("Delay", 0.0, 250.0, 150.0, 5.0, " ms")
	private val breakThreshold = NumberSetting("Break Threshold", 200.0, 800.0, 500.0, 10.0, " ms")
	private val melodySkip = BooleanSetting("Melody Skip", true)

	private var clickedWindow = false
	private var firstClick = true
	private var openedAtMs = 0L
	private var lastClickTime = 0L
	private var session: TerminalSession? = null
	private var terminalContainer: AbstractContainerMenu? = null
	private val clickedSlots = hashSetOf<Int>()
	private var lastHumanClickSlot: Int? = null
	private var panelPathStartsLeft = true
	private val melodyQueue = ArrayDeque<SolutionClick>()
	private var melodyState: MelodyState? = null
	private var lastMelodyClickState: MelodyState? = null
	private var skippedFirstMelodyOpportunity = false

	init {
		instance = this
		registerProperty(skyblock, terminals, firstClickDelay, delay, breakThreshold, melodySkip)
	}

	override fun onClientTick(client: Minecraft) {
		val current = session
		if (current == null || terminalContainer == null) {
			return
		}
		if (!hasTerminalScreenOpen()) {
			close()
			return
		}

		val now = System.currentTimeMillis()
		if (current.type == TerminalType.MELODY) {
			tickMelody(now)
			return
		}

		if (!canClickNow(now)) {
			return
		}
		if (!firstClick && now - lastClickTime > breakThreshold.value.toLong()) {
			clickedWindow = false
			clickedSlots.clear()
		}
		if (!isInTerm() || TerminalSolver.blockAll) {
			return
		}

		val solution = solve(current)
		if (solution.isEmpty()) {
			return
		}

		if (sendWindowClick(selectNextClick(current, solution))) {
			markClicked(now)
			clickedWindow = true
		}
	}

	override fun onPacketReceive(packet: Packet<*>): Boolean {
		handleTerminalPacket(packet)
		return false
	}

	fun handleTerminalPacket(packet: Packet<*>) {
		when (packet) {
			is ClientboundOpenScreenPacket -> handleOpenScreen(packet)
			is ClientboundContainerSetSlotPacket -> handleSetSlot(packet)
			is ClientboundContainerSetContentPacket -> handleSetContent(packet)
			is ClientboundContainerClosePacket -> {
				if (packet.containerId == terminalContainer?.containerId) {
					close()
				}
			}
		}
	}

	override fun onPacketSend(packet: Packet<*>): Boolean {
		handleTerminalSend(packet)
		return false
	}

	fun handleTerminalSend(packet: Packet<*>) {
		if (packet is ServerboundContainerClosePacket && isInTerm()) {
			close()
		}
	}

	override fun onWorldLoad() {
		close()
	}

	override fun reset() {
		close()
	}

	fun isInTerm(): Boolean =
		session != null && terminalContainer != null

	fun overlay(): TerminalOverlay? {
		val current = session ?: return null
		val menu = terminalContainer ?: return null
		if (Minecraft.getInstance().screen == null || Minecraft.getInstance().player?.containerMenu?.containerId != menu.containerId) {
			return null
		}

		val solutionSlots = if (current.loaded && isEnabled(current.type) && !TerminalSolver.blockAll) {
			solve(current).map { it.index }.toSet()
		} else {
			emptySet()
		}

		return TerminalOverlay(
			windowId = current.windowId,
			typeKey = current.type.terminalSolverKey,
			solutionSlots = solutionSlots,
			clickedSlots = clickedSlots.toSet()
		)
	}

	fun sendWindowClick(click: SolutionClick): Boolean {
		val player = Minecraft.getInstance().player ?: return false
		val menu = terminalContainer ?: return false
		val current = session ?: return false
		if (!hasTerminalScreenOpen()) {
			close()
			return false
		}
		if (click.index !in 0 until current.type.slotCount) {
			return false
		}

		if (current.type != TerminalType.RUBIX && current.type != TerminalType.MELODY) {
			clickedSlots.add(click.index)
		}

		Minecraft.getInstance().gameMode?.handleContainerInput(menu.containerId, click.index, click.button, click.type, player)
			?: return false
		return true
	}

	private fun handleOpenScreen(packet: ClientboundOpenScreenPacket) {
		val player = Minecraft.getInstance().player ?: return
		if (packet.containerId !in 1..100 || !passesSkyblockMode() || !isTerminalMenu(packet.type)) {
			if (isInTerm()) {
				close()
			}
			return
		}

		val type = TerminalType.fromTitle(packet.title.string, looseTerminalDetection()) ?: run {
			if (isInTerm()) {
				close()
			}
			return
		}
		terminalContainer = packet.type.create(packet.containerId, player.inventory)
		session = TerminalSession(type, packet.containerId, packet.title.string)
		clickedWindow = false
		firstClick = true
		openedAtMs = System.currentTimeMillis()
		lastClickTime = 0L
		clickedSlots.clear()
		lastHumanClickSlot = type.slotCount / 2
		panelPathStartsLeft = System.nanoTime() % 2L == 0L
		clearMelody()
		TerminalContext.open()
	}

	private fun handleSetSlot(packet: ClientboundContainerSetSlotPacket) {
		val menu = terminalContainer ?: return
		val current = session ?: return
		if (packet.containerId != menu.containerId) {
			return
		}

		menu.setItem(packet.slot, packet.stateId, packet.item)
		if (packet.slot in 0 until current.type.slotCount) {
			current.loadedSlots.add(packet.slot)
		}
		if (packet.slot > current.type.slotCount || current.loadedSlots.size >= current.type.slotCount) {
			current.loaded = true
		}
		if (current.type == TerminalType.MELODY) {
			loadMelodySlot(packet)
		}
		clickedWindow = false
	}

	private fun handleSetContent(packet: ClientboundContainerSetContentPacket) {
		val menu = terminalContainer ?: return
		val current = session ?: return
		if (packet.containerId() != menu.containerId) {
			return
		}

		for ((index, item) in packet.items().withIndex()) {
			if (index < menu.slots.size) {
				menu.setItem(index, packet.stateId(), item)
			}
		}
		current.loaded = true
		clickedWindow = false
	}

	private fun loadMelodySlot(packet: ClientboundContainerSetSlotPacket) {
		val current = session ?: return
		val menu = terminalContainer ?: return
		if (packet.containerId != current.windowId) {
			return
		}

		val slot = packet.slot
		if (slot in 10 until current.type.slotCount && packet.item.`is`(Items.LIME_STAINED_GLASS_PANE)) {
			val correctColumn = menu.slots
				.asSequence()
				.take(current.type.slotCount)
				.firstOrNull { it.index in 1..5 && it.item.`is`(Items.MAGENTA_STAINED_GLASS_PANE) }
				?.index
				?.minus(1)
				?: return
			val state = MelodyState(
				buttonRow = slot / 9 - 1,
				currentColumn = slot % 9 - 1,
				correctColumn = correctColumn
			)
			if (state != melodyState) {
				melodyQueue.clear()
				lastMelodyClickState = null
			}
			melodyState = state
		}
	}

	private fun tickMelody(now: Long) {
		if (!isEnabled(TerminalType.MELODY) || TerminalSolver.blockAll || !isInTerm()) {
			return
		}

		if (melodyQueue.isEmpty()) {
			queueMelodyClicks()
		}

		if (melodyQueue.isEmpty() || !canClickNow(now)) {
			return
		}

		if (sendWindowClick(melodyQueue.removeFirst())) {
			markClicked(now)
		}
	}

	private fun queueMelodyClicks() {
		val state = melodyState ?: return
		if (state.currentColumn != state.correctColumn || state == lastMelodyClickState) {
			return
		}

		val baseSlot = state.buttonRow * 9 + 16
		if (baseSlot !in 16..43) {
			return
		}

		val shouldWaitForFirstRow = melodySkip.value
			&& firstClick
			&& !skippedFirstMelodyOpportunity
			&& state.buttonRow == 0
			&& state.currentColumn == 0
		if (shouldWaitForFirstRow) {
			skippedFirstMelodyOpportunity = true
			lastMelodyClickState = state
			return
		}

		melodyQueue.add(SolutionClick(ContainerInput.CLONE, baseSlot, 0))
		lastMelodyClickState = state
	}

	private fun canClickNow(now: Long): Boolean =
		if (firstClick) {
			now - openedAtMs >= currentFirstDelayMs()
		} else {
			now - lastClickTime >= currentClickDelayMs()
		}

	private fun markClicked(now: Long) {
		lastClickTime = now
		firstClick = false
	}

	private fun currentFirstDelayMs(): Long =
		if (TerminalSolver.active) TerminalSolver.firstDelayMs else firstClickDelay.value.toLong()

	private fun currentClickDelayMs(): Long =
		if (TerminalSolver.active) TerminalSolver.clickDelayMs else delay.value.toLong()

	private fun solve(current: TerminalSession): List<SolutionClick> {
		if (!current.loaded || !isEnabled(current.type)) {
			return emptyList()
		}

		return when (current.type) {
			TerminalType.NUMBERS -> solveNumbers()
			TerminalType.COLORS -> solveColors(current.title)
			TerminalType.STARTS_WITH -> solveStartsWith(current.title)
			TerminalType.RUBIX -> solveRubix()
			TerminalType.RED_GREEN -> solveRedGreen()
			TerminalType.MELODY -> emptyList()
		}
	}

	private fun selectNextClick(current: TerminalSession, solution: List<SolutionClick>): SolutionClick {
		if (current.type == TerminalType.NUMBERS || solution.size == 1) {
			val click = solution.first()
			lastHumanClickSlot = click.index
			return click
		}
		if (current.type == TerminalType.RED_GREEN) {
			return selectPanelPathClick(current, solution)
		}

		val menu = terminalContainer ?: return solution.first()
		val lastSlot = lastHumanClickSlot ?: (current.type.slotCount / 2)
		val click = solution
			.shuffled()
			.minWithOrNull(
				compareBy<SolutionClick>(
					{ slotDistanceSquared(menu, it.index, lastSlot) },
					{ neighborCount(menu, it.index, solution) }
				)
			)
			?: solution.first()

		lastHumanClickSlot = click.index
		return click
	}

	private fun selectPanelPathClick(current: TerminalSession, solution: List<SolutionClick>): SolutionClick {
		val bottomRow = (current.type.slotCount - 1) / 9
		val click = solution
			.minWithOrNull(
				compareBy<SolutionClick>(
					{ bottomRow - slotRow(it.index) },
					{ panelColumnRank(it.index, bottomRow) }
				)
			)
			?: solution.first()

		lastHumanClickSlot = click.index
		return click
	}

	private fun panelColumnRank(slot: Int, bottomRow: Int): Int {
		val rowFromBottom = bottomRow - slotRow(slot)
		val leftToRight = if (rowFromBottom % 2 == 0) panelPathStartsLeft else !panelPathStartsLeft
		val column = slot % 9
		return if (leftToRight) column else -column
	}

	private fun slotRow(slot: Int): Int =
		slot / 9

	private fun neighborCount(menu: AbstractContainerMenu, slot: Int, solution: List<SolutionClick>): Int =
		solution.count { other ->
			other.index != slot && slotDistanceSquared(menu, slot, other.index) <= HUMAN_NEIGHBOR_RADIUS_SQUARED
		}

	private fun slotDistanceSquared(menu: AbstractContainerMenu, first: Int, second: Int): Int {
		val firstSlot = menu.slots.getOrNull(first) ?: return Int.MAX_VALUE
		val secondSlot = menu.slots.getOrNull(second) ?: return Int.MAX_VALUE
		val dx = firstSlot.x - secondSlot.x
		val dy = firstSlot.y - secondSlot.y
		return dx * dx + dy * dy
	}

	private fun solveNumbers(): List<SolutionClick> =
		terminalContainer?.slots
			?.asSequence()
			?.filter { it.index in 0 until TerminalType.NUMBERS.slotCount }
			?.filter { !clickedSlots.contains(it.index) }
			?.filter { it.item.`is`(Items.RED_STAINED_GLASS_PANE) }
			?.sortedBy { it.item.count }
			?.map { SolutionClick(ContainerInput.CLONE, it.index, 0) }
			?.toList()
			.orEmpty()

	private fun solveRedGreen(): List<SolutionClick> =
		terminalContainer?.slots
			?.asSequence()
			?.filter { it.index in 0 until TerminalType.RED_GREEN.slotCount }
			?.filter { !clickedSlots.contains(it.index) }
			?.filter { !it.item.isEmpty && it.item.`is`(Items.RED_STAINED_GLASS_PANE) }
			?.map { SolutionClick(ContainerInput.CLONE, it.index, 0) }
			?.toList()
			.orEmpty()

	private fun solveColors(title: String): List<SolutionClick> {
		val matcher = COLORS_PATTERN.matcher(title)
		if (!matcher.find()) {
			return emptyList()
		}

		val color = matcher.group(1).lowercase(Locale.ROOT)
		return terminalContainer?.slots
			?.asSequence()
			?.filter { it.index in 0 until TerminalType.COLORS.slotCount }
			?.filter { !it.item.isEmpty && !clickedSlots.contains(it.index) }
			?.filter { !it.item.`is`(Items.BLACK_STAINED_GLASS_PANE) && !it.item.hasFoil() }
			?.filter { fixedColorItemName(ChatFormatting.stripFormatting(it.item.hoverName.string)?.lowercase(Locale.ROOT).orEmpty()).startsWith(color) }
			?.map { SolutionClick(ContainerInput.CLONE, it.index, 0) }
			?.toList()
			.orEmpty()
	}

	private fun solveStartsWith(title: String): List<SolutionClick> {
		val matcher = STARTS_WITH_PATTERN.matcher(title)
		if (!matcher.find()) {
			return emptyList()
		}

		val prefix = matcher.group(1).lowercase(Locale.ROOT)
		return terminalContainer?.slots
			?.asSequence()
			?.filter { it.index in 0 until TerminalType.STARTS_WITH.slotCount }
			?.filter { !it.item.isEmpty && !clickedSlots.contains(it.index) }
			?.filter { !it.item.hasFoil() }
			?.filter { (ChatFormatting.stripFormatting(it.item.hoverName.string) ?: "").lowercase(Locale.ROOT).startsWith(prefix) }
			?.map { SolutionClick(ContainerInput.CLONE, it.index, 0) }
			?.toList()
			.orEmpty()
	}

	private fun solveRubix(): List<SolutionClick> {
		val menu = terminalContainer ?: return emptyList()
		val rubixSlots = menu.slots
			.filter { it.index in 0 until TerminalType.RUBIX.slotCount }
			.filter { !it.item.isEmpty && !it.item.`is`(Items.BLACK_STAINED_GLASS_PANE) && isRubixPane(it.item.item) }
			.map { it.index }

		var minIndex = -1
		var minTotal = Int.MAX_VALUE
		for (targetIndex in RUBIX_COLOR_ORDER.indices) {
			var totalClicks = 0
			for (slot in rubixSlots) {
				val currentIndex = RUBIX_COLOR_ORDER.indexOf(menu.getSlot(slot).item.item)
				val clockwise = (targetIndex - currentIndex + RUBIX_COLOR_ORDER.size) % RUBIX_COLOR_ORDER.size
				val counterClockwise = (currentIndex - targetIndex + RUBIX_COLOR_ORDER.size) % RUBIX_COLOR_ORDER.size
				totalClicks += minOf(clockwise, counterClockwise)
			}
			if (totalClicks < minTotal) {
				minTotal = totalClicks
				minIndex = targetIndex
			}
		}

		val solution = arrayListOf<SolutionClick>()
		for (slot in rubixSlots) {
			val currentIndex = RUBIX_COLOR_ORDER.indexOf(menu.getSlot(slot).item.item)
			val clockwise = (minIndex - currentIndex + RUBIX_COLOR_ORDER.size) % RUBIX_COLOR_ORDER.size
			val counterClockwise = (currentIndex - minIndex + RUBIX_COLOR_ORDER.size) % RUBIX_COLOR_ORDER.size
			if (TerminalSolver.anyClickRubix) {
				repeat(minOf(clockwise, counterClockwise)) {
					solution.add(SolutionClick(ContainerInput.PICKUP, slot, 0))
				}
			} else if (clockwise <= counterClockwise) {
				repeat(clockwise) { solution.add(SolutionClick(ContainerInput.PICKUP, slot, 0)) }
			} else {
				repeat(counterClockwise) { solution.add(SolutionClick(ContainerInput.PICKUP, slot, 1)) }
			}
		}
		return solution
	}

	private fun close() {
		session = null
		terminalContainer = null
		clickedWindow = false
		firstClick = true
		openedAtMs = 0L
		lastClickTime = 0L
		clickedSlots.clear()
		lastHumanClickSlot = null
		clearMelody()
		TerminalContext.close()
	}

	private fun hasTerminalScreenOpen(): Boolean {
		val client = Minecraft.getInstance()
		val menu = terminalContainer ?: return false
		return client.screen != null && client.player?.containerMenu?.containerId == menu.containerId
	}

	private fun clearMelody() {
		melodyQueue.clear()
		melodyState = null
		lastMelodyClickState = null
		skippedFirstMelodyOpportunity = false
	}

	private fun isTerminalMenu(type: MenuType<*>): Boolean =
		if (looseTerminalDetection()) {
			type == MenuType.GENERIC_9x1
				|| type == MenuType.GENERIC_9x2
				|| type == MenuType.GENERIC_9x3
				|| type == MenuType.GENERIC_9x4
				|| type == MenuType.GENERIC_9x5
				|| type == MenuType.GENERIC_9x6
		} else {
			type == MenuType.GENERIC_9x4 || type == MenuType.GENERIC_9x5 || type == MenuType.GENERIC_9x6
		}

	private fun passesSkyblockMode(): Boolean =
		when (skyblock.value) {
			"Dungeon" -> Location.area.isArea(Island.DUNGEON) && DungeonState.inBoss
			"Skyblock" -> Location.inSkyblock
			"Practice" -> true
			else -> true
		}

	private fun looseTerminalDetection(): Boolean =
		skyblock.isMode("Practice")

	private fun isEnabled(type: TerminalType): Boolean =
		when (type) {
			TerminalType.NUMBERS -> terminals["Numbers"]
			TerminalType.COLORS -> terminals["Colours"]
			TerminalType.STARTS_WITH -> terminals["Starts With"]
			TerminalType.RUBIX -> terminals["Rubix"]
			TerminalType.RED_GREEN -> terminals["Red Green"]
			TerminalType.MELODY -> terminals["Melody"]
		} && TerminalSolver.isTerminalEnabled(type.terminalSolverKey)

	private fun fixedColorItemName(name: String): String {
		for ((from, to) in COLOR_REPLACEMENTS) {
			if (name.startsWith(from)) {
				return to + name.substring(from.length)
			}
		}
		return name
	}

	private fun isRubixPane(item: Item): Boolean =
		RUBIX_COLOR_ORDER.contains(item)

	private data class TerminalSession(
		val type: TerminalType,
		val windowId: Int,
		val title: String,
		var loaded: Boolean = false,
		val loadedSlots: MutableSet<Int> = hashSetOf()
	)

	data class SolutionClick(val type: ContainerInput, val index: Int, val button: Int)

	private data class MelodyState(
		val buttonRow: Int,
		val currentColumn: Int,
		val correctColumn: Int
	)

	data class TerminalOverlay(
		val windowId: Int,
		val typeKey: String,
		val solutionSlots: Set<Int>,
		val clickedSlots: Set<Int>
	)

	private enum class TerminalType(val title: String, val slotCount: Int, val terminalSolverKey: String, val aliases: List<String> = emptyList()) {
		NUMBERS("Click in order!", 36, "Order", listOf("click in order", "click the numbers")),
		COLORS("Select all the", 54, "Select", listOf("select all the")),
		STARTS_WITH("What starts with:", 45, "Starts With", listOf("what starts with")),
		RUBIX("Change all to same color!", 45, "Rubix", listOf("change all to same color", "change all to same colour")),
		RED_GREEN("Correct all the panes!", 45, "Panes", listOf("correct all the panes")),
		MELODY("Click the button on time!", 54, "Melody", listOf("click the button on time"));

		companion object {
			fun fromTitle(title: String, loose: Boolean): TerminalType? {
				val stripped = ChatFormatting.stripFormatting(title) ?: title
				val normalized = stripped.lowercase(Locale.ROOT)
				return entries.firstOrNull { stripped.startsWith(it.title) }
					?: if (loose) {
						entries.firstOrNull { type ->
							type.aliases.any { normalized.contains(it.lowercase(Locale.ROOT)) }
						}
					} else {
						null
					}
			}
		}
	}

	companion object {
		private var instance: AutoTerms? = null
		private const val HUMAN_NEIGHBOR_RADIUS_SQUARED = 400
		private val COLORS_PATTERN = Pattern.compile("Select all the (.+) items!")
		private val STARTS_WITH_PATTERN = Pattern.compile("What starts with: '(\\w+)'\\?")
		private val COLOR_REPLACEMENTS = linkedMapOf(
			"light gray" to "silver",
			"wool" to "white",
			"bone" to "white",
			"ink" to "black",
			"lapis" to "blue",
			"cocoa" to "brown",
			"dandelion" to "yellow",
			"rose" to "red",
			"cactus" to "green"
		)
		private val RUBIX_COLOR_ORDER = listOf(
			Items.ORANGE_STAINED_GLASS_PANE,
			Items.YELLOW_STAINED_GLASS_PANE,
			Items.GREEN_STAINED_GLASS_PANE,
			Items.BLUE_STAINED_GLASS_PANE,
			Items.RED_STAINED_GLASS_PANE
		)

		@JvmStatic
		fun isInTerminal(): Boolean =
			instance?.enabled == true && instance?.isInTerm() == true

		@JvmStatic
		fun terminalOverlay(): TerminalOverlay? =
			instance?.takeIf { it.enabled || TerminalSolver.active }?.overlay()

		@JvmStatic
		fun handlePacketForTerminalSolver(packet: Packet<*>) {
			val terms = instance ?: return
			if (!terms.enabled) {
				terms.handleTerminalPacket(packet)
			}
		}

		@JvmStatic
		fun handleSendForTerminalSolver(packet: Packet<*>) {
			val terms = instance ?: return
			if (!terms.enabled) {
				terms.handleTerminalSend(packet)
			}
		}

		@JvmStatic
		fun clearForTerminalSolver() {
			val terms = instance ?: return
			if (!terms.enabled) {
				terms.close()
			}
		}
	}
}
