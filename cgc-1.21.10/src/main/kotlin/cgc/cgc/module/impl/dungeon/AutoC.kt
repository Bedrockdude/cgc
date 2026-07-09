package cgc.cgc.module.impl.dungeon

import cgc.cgc.data.DungeonClass
import cgc.cgc.data.Pos
import cgc.cgc.dungeon.DungeonState
import cgc.cgc.dungeon.room.DungeonRoomScanner
import cgc.cgc.dungeon.room.ScannedDungeonRoom
import cgc.cgc.location.Island
import cgc.cgc.location.Location
import cgc.cgc.module.ActionBarMessageModule
import cgc.cgc.module.ClientTickModule
import cgc.cgc.module.ClientTickStartModule
import cgc.cgc.module.CgcModule
import cgc.cgc.module.ModuleCategory
import cgc.cgc.module.PacketReceiveModule
import cgc.cgc.module.PacketSendModule
import cgc.cgc.module.WorldLoadModule
import cgc.cgc.module.WorldRenderExtractModule
import cgc.cgc.module.WorldRenderStartModule
import cgc.cgc.module.impl.dungeon.autoc.AutoCInputController
import cgc.cgc.module.impl.dungeon.autoc.AutoCLookController
import cgc.cgc.module.impl.dungeon.autoc.AutoCNode
import cgc.cgc.module.impl.dungeon.autoc.AutoCNodeAdapter
import cgc.cgc.module.impl.dungeon.autoc.AutoCNodeContext
import cgc.cgc.module.impl.dungeon.autoc.AutoCNodeType
import cgc.cgc.module.impl.dungeon.autoc.AutoCNodeUtils
import cgc.cgc.module.impl.dungeon.autoc.AutoCStrafeDirection
import cgc.cgc.module.impl.dungeon.autoc.nodes.BreakNode
import cgc.cgc.module.impl.dungeon.autoc.nodes.CrouchNode
import cgc.cgc.module.impl.dungeon.autoc.nodes.EtherwarpNode
import cgc.cgc.module.impl.dungeon.autoc.nodes.RecordEvent
import cgc.cgc.module.impl.dungeon.autoc.nodes.RecordEventType
import cgc.cgc.module.impl.dungeon.autoc.nodes.RecordFrame
import cgc.cgc.module.impl.dungeon.autoc.nodes.RecordLookSample
import cgc.cgc.module.impl.dungeon.autoc.nodes.RecordNode
import cgc.cgc.module.impl.dungeon.autoc.nodes.StrafeNode
import cgc.cgc.module.impl.dungeon.autoc.nodes.WalkNode
import cgc.cgc.module.setting.SaveSetting
import cgc.cgc.runtime.ItemInteractionUtils
import cgc.cgc.terminal.TerminalContext
import cgc.cgc.utils.ChatUtils
import cgc.cgc.utils.SpiritLeapMenu
import com.mojang.blaze3d.platform.InputConstants
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.ChatFormatting
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.network.protocol.game.ServerboundSwingPacket
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.ArrayDeque
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

class AutoC : CgcModule(
	id = "AutoC",
	displayName = "Auto C",
	category = ModuleCategory.DUNGEONS,
	description = "Auto C route tooling.",
	defaultEnabled = false
), ClientTickStartModule, ClientTickModule, WorldRenderStartModule, WorldRenderExtractModule, ActionBarMessageModule, PacketReceiveModule, PacketSendModule, WorldLoadModule {
	private val nodeListType = object : TypeToken<MutableList<AutoCNode>>() {}.type
	private val nodeGson = GsonBuilder()
		.registerTypeHierarchyAdapter(AutoCNode::class.java, AutoCNodeAdapter())
		.setPrettyPrinting()
		.create()
	private val data = SaveSetting(
		name = "Nodes",
		path = "dungeon/auto_c",
		defaultFile = "nodes.json",
		factory = { mutableListOf<AutoCNode>() },
		valueType = nodeListType,
		gson = nodeGson,
		allowEdits = true,
		action = this::reload
	)

	private val globalNodes = arrayListOf<AutoCNode>()
	private val roomNodes = arrayListOf<AutoCNode>()
	private val nodes = arrayListOf<AutoCNode>()
	private val redo = arrayListOf<AutoCNode>()
	private val lookController = AutoCLookController()
	private val inputController = AutoCInputController()
	private val leapMenu = SpiritLeapMenu("AC »") { target -> modMessage("Leaping to $target") }
	private val nodeContext = object : AutoCNodeContext {
		override fun smoothLook(yaw: Float, pitch: Float) {
			startSmoothLook(yaw, pitch)
		}

		override fun smoothLookAtBlock(yaw: Float, pitch: Float, block: BlockPos) {
			val player = Minecraft.getInstance().player
			if (player == null || !isAimingAtBlock(player, block)) {
				startSmoothLook(yaw, pitch)
			}
		}

		override fun startWalk(yaw: Float, pitch: Float) {
			startWalkAction(yaw, pitch)
		}

		override fun startStrafe(direction: AutoCStrafeDirection) {
			startStrafeAction(direction)
		}

		override fun warp(yaw: Float, pitch: Float) {
			startUseAction(yaw, pitch, sneak = false, itemIds = listOf("ASPECT_OF_THE_VOID", "ASPECT_OF_THE_END"))
		}

		override fun etherwarp(yaw: Float, pitch: Float, block: BlockPos) {
			startUseAction(yaw, pitch, sneak = true, itemIds = listOf("ASPECT_OF_THE_VOID"), targetBlock = block)
		}

		override fun interact(yaw: Float, pitch: Float, block: BlockPos, hit: Vec3, await: Boolean) {
			startInteractAction(yaw, pitch, block, hit, await)
		}

		override fun useItem(yaw: Float, pitch: Float, skyBlockId: String, itemId: String, displayName: String) {
			startHeldUseAction(yaw, pitch, skyBlockId, itemId, displayName)
		}

		override fun bonzo(yaw: Float, pitch: Float) {
			startBonzoAction(yaw, pitch)
		}

		override fun crouch(seconds: Double) {
			startCrouchAction(seconds)
		}

		override fun runCommand(command: String) {
			runStoredCommand(command)
		}

		override fun jump() {
			scheduleJump()
		}

		override fun edge() {
			armEdge()
		}

		override fun leap(clazz: DungeonClass) {
			startLeap(clazz)
		}

		override fun stopActions(except: Set<String>) {
			this@AutoC.stopActions(except)
		}

		override fun breakBlocks(blocks: List<Pos>, zeroTick: Boolean): Boolean =
			breakConfiguredBlocks(blocks, zeroTick)

		override fun playRecording(frames: List<RecordFrame>): Boolean =
			startRecordPlayback(frames)
	}
	private var tickTime = 0
	private var inNode: AutoCNode? = null
	private var lastType: Class<out AutoCNode>? = null
	private var previousPlayerPos: Pos? = null
	private var walkPlan: WalkPlan? = null
	private var strafePlan: StrafePlan? = null
	private var useAction: UseAction? = null
	private var bonzoAction: BonzoAction? = null
	private var useKeyTicks = 0
	private var useKeySneak = false
	private var etherwarpShiftHoldTicks = 0
	private var interactAction: InteractAction? = null
	private var queuedInteractAction: InteractAction? = null
	private var crouchAction: CrouchAction? = null
	private var edgeUntilMs = 0L
	private var routeWaitUntilMs = 0L
	private var routeWaitUntilTick = 0
	private var routeActive = false
	private var awaitSecretNode: AutoCNode? = null
	private var awaitSecretPendingNode: AutoCNode? = null
	private val stackedNodeQueue = arrayListOf<AutoCNode>()
	private var awaitSecretRemaining = 0
	private var awaitSecretBaseline: Int? = null
	private var awaitSecretMax: Int? = null
	private var awaitSecretCompleteOnFirstCounter = false
	private var awaitSecretCrouchHeld = false
	private var currentSecretCount: Int? = null
	private var currentSecretMax: Int? = null
	private var autoJumpTicks = 0
	private var pendingJumpUntilMs = 0L
	private var manualBreakPlan: ManualBreakPlan? = null
	private var breakRecording: BreakRecording? = null
	private var recordRecording: RecordRecording? = null
	private var recordPlayback: RecordPlayback? = null
	private var activeScopeSignature: String? = null
	private var activeRoom: ScannedDungeonRoom? = null
	private val activatedNodeIds = linkedSetOf<String>()
	private val nodeActivationTimes = hashMapOf<String, ArrayDeque<Long>>()
	private var terminalExitListenerArmed = false
	private var terminalExitWindowUntilMs = 0L
	private val terminalExitActivatedNodeIds = linkedSetOf<String>()

	init {
		data.load()
		reload()
	}

	override fun onClientTickStart(client: Minecraft) {
		guardManualBreakAttack(client)
	}

	override fun onClientTick(client: Minecraft) {
		val player = client.player ?: return
		client.level ?: return
		refreshActiveNodes(client)
		leapMenu.tickTimeout(MENU_TIMEOUT_MS)
		finishBreakRecordingIfNeeded()
		finishRecordRecordingIfNeeded()
		if (TerminalContext.inTerminal) {
			armTerminalExitTrigger()
		}
		if (client.screen != null) {
			inputController.releaseAll()
			bonzoAction = null
			crouchAction = null
			useKeyTicks = 0
			useKeySneak = false
			etherwarpShiftHoldTicks = 0
			awaitSecretCrouchHeld = false
			autoJumpTicks = 0
			pendingJumpUntilMs = 0L
			if (interactAction?.await != true) {
				interactAction = null
			}
			if (queuedInteractAction?.await != true) {
				queuedInteractAction = null
			}
			recordPlayback = null
			previousPlayerPos = Pos(player.position())
			return
		}

		tickTime++
		val playerPos = Pos(player.position())
		if (recordRecording != null) {
			recordSnapshot(client, player)
			finishRecordRecordingIfNeeded()
			previousPlayerPos = playerPos
			return
		}

		val lastPlayerPos = previousPlayerPos
		updateRouteActiveState(client)
		synchronized(nodes) {
			nodes.forEach { it.updateNodeState(playerPos, tickTime) }
			if (isTerminalExitWindowActive()) {
				while (handleTerminalExitQueue(player, playerPos)) {
				}
			}
			while (handleQueue(playerPos, lastPlayerPos)) {
			}
		}
		updateAwaitSecretCrouch(client)
		if (updateRecordPlayback(client, player)) {
			previousPlayerPos = playerPos
			return
		}
		updateUseAction(client)
		updateInteractAction(client, player)
		executeQueuedInteract(client)
		updateManualBreak(client, player)
		updateStrafe(client)
		updateWalk(client, player)
		updateBonzoAction(client, player)
		updateEdge(client, player)
		updateAutoJump(client)
		updateUseKeyHold(client)
		updateCrouchAction(client, player)
		finishBreakRecordingIfNeeded()
		updateRouteActiveState(client)
		previousPlayerPos = playerPos
	}

	override fun onWorldRenderStart() {
		val client = Minecraft.getInstance()
		if (client.screen != null) {
			return
		}

		val player = client.player ?: return
		if (recordRecording != null) {
			recordLookSample(player)
			return
		}
		if (updateRecordCameraPlayback(player)) {
			return
		}
		lookController.update(player)
	}

	override fun onWorldRenderExtract(context: LevelRenderContext) {
		val client = Minecraft.getInstance()
		if (client.player == null || client.level == null) {
			return
		}

		synchronized(nodes) {
			nodes.forEach { it.render(NODE_DEPTH) }
		}
	}

	override fun onActionBarMessage(message: String) {
		val counter = parseSecretCounter(message) ?: return
		currentSecretCount = counter.current
		currentSecretMax = counter.max

		if (!hasActiveSecretAwait()) {
			return
		}

		val baseline = awaitSecretBaseline
		if (baseline == null || counter.current < baseline || (awaitSecretMax != null && awaitSecretMax != counter.max)) {
			awaitSecretBaseline = counter.current
			awaitSecretMax = counter.max
			if (baseline == null && awaitSecretCompleteOnFirstCounter) {
				awaitSecretCompleteOnFirstCounter = false
				consumeSecretAwait()
			}
			return
		}

		val gained = counter.current - baseline
		if (gained > 0) {
			awaitSecretBaseline = counter.current
			consumeSecretAwait(gained)
		}
	}

	override fun onPacketReceive(packet: Packet<*>): Boolean {
		consumeSecretAwaitPacket(packet)
		if (packet is ClientboundOpenScreenPacket && leapMenu.handleOpenScreen(packet)) {
			return true
		}
		if (packet is ClientboundContainerSetSlotPacket && leapMenu.handleSetSlot(packet)) {
			return true
		}
		return false
	}

	override fun onPacketSend(packet: Packet<*>): Boolean {
		recordActionPacket(packet)
		if (packet is ServerboundPlayerActionPacket
			&& packet.action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK
		) {
			recordBreakBlock(packet.pos)
		}
		return false
	}

	override fun onWorldLoad() {
		synchronized(nodes) {
			activeScopeSignature = null
			activeRoom = null
			roomNodes.clear()
			replaceActiveNodes(globalNodes)
		}
		clearRuntime()
	}

	override fun reset() {
		clearRuntime()
	}

	fun addNode(type: AutoCNodeType, args: String = ""): AutoCNode? {
		val client = Minecraft.getInstance()
		val player = client.player
		if (player == null) {
			modMessage("${ChatFormatting.RED}You need to be in-world to add an Auto C node.")
			return null
		}
		val scope = editableScope(client) ?: return null

		val placementArgs = parsePlacementArgs(type, args)
		val node = type.supply(player, placementArgs.args) ?: return null
		if (placementArgs.heightOffset != 0.0) {
			node.pos.y += placementArgs.heightOffset
		}
		placementArgs.radius?.let { node.radius = it }
		node.waitSeconds = placementArgs.waitSeconds
		node.awaitSecret = placementArgs.awaitSecret
		node.notStart = placementArgs.notStart
		node.maxActivationsPerMinute = placementArgs.maxActivationsPerMinute
		if (node is BreakNode && !enabled) {
			modMessage("${ChatFormatting.RED}Enable Auto C before adding break nodes so block recording can run.")
			return null
		}
		if (node is RecordNode && !enabled) {
			modMessage("${ChatFormatting.RED}Enable Auto C before adding record nodes so action recording can run.")
			return null
		}

		var recordingNode: BreakNode? = null
		var recordingRoom: ScannedDungeonRoom? = null
		var actionRecordingNode: RecordNode? = null
		var actionRecordingRoom: ScannedDungeonRoom? = null
		when (scope) {
			RouteScope.Global -> {
				synchronized(nodes) {
					node.calculate()
					globalNodes.add(node)
					replaceActiveNodes(globalNodes)
					activeScopeSignature = scope.signature
					activeRoom = null
					redo.clear()
				}
				saveGlobal()
				recordingNode = node as? BreakNode
				actionRecordingNode = node as? RecordNode
			}
			is RouteScope.Room -> {
				val relativeNode = transformNode(node, scope.room, toWorld = false) ?: run {
					modMessage("${ChatFormatting.RED}Couldn't transform node into ${scope.room.displayName}.")
					return null
				}
				synchronized(nodes) {
					relativeNode.calculate()
					roomNodes.add(relativeNode)
					replaceActiveNodes(materializeRoomNodes(scope.room))
					activeScopeSignature = scope.signature
					activeRoom = scope.room
					redo.clear()
				}
				saveRoomNodes(scope.room)
				recordingNode = relativeNode as? BreakNode
				recordingRoom = scope.room
				actionRecordingNode = relativeNode as? RecordNode
				actionRecordingRoom = scope.room
			}
			is RouteScope.UnavailableRoom -> return null
		}
		if (recordingNode != null) {
			startBreakRecording(recordingNode, recordingRoom)
		}
		if (actionRecordingNode != null) {
			startRecordRecording(actionRecordingNode, actionRecordingRoom)
		}
		return node
	}

	fun removeNearest(): AutoCNode? {
		val client = Minecraft.getInstance()
		val player = client.player ?: return null
		val scope = editableScope(client) ?: return null
		val pos = player.position()
		val removed = when (scope) {
			RouteScope.Global -> {
				synchronized(nodes) {
					if (globalNodes.isEmpty()) {
						null
					} else {
						val index = nodes.indices.minByOrNull { nodes[it].pos.squaredDistanceTo(pos) } ?: return null
						val removed = nodes.getOrNull(index) ?: return null
						globalNodes.removeAt(index)
						replaceActiveNodes(globalNodes)
						removed
					}
				}.also {
					if (it != null) {
						saveGlobal()
					}
				}
			}
			is RouteScope.Room -> {
				synchronized(nodes) {
					if (roomNodes.isEmpty()) {
						null
					} else {
						val index = nodes.indices.minByOrNull { nodes[it].pos.squaredDistanceTo(pos) } ?: return null
						val removed = nodes.getOrNull(index) ?: return null
						roomNodes.removeAt(index)
						replaceActiveNodes(materializeRoomNodes(scope.room))
						removed
					}
				}.also {
					if (it != null) {
						saveRoomNodes(scope.room)
					}
				}
			}
			is RouteScope.UnavailableRoom -> null
		}

		if (removed != null) {
			clearRuntime()
		}
		return removed
	}

	fun undo(): AutoCNode? {
		val client = Minecraft.getInstance()
		val scope = editableScope(client) ?: return null
		val removed = when (scope) {
			RouteScope.Global -> {
				synchronized(nodes) {
					if (globalNodes.isEmpty()) {
						null
					} else {
						val node = globalNodes.removeAt(globalNodes.lastIndex)
						redo.add(node)
						replaceActiveNodes(globalNodes)
						node
					}
				}.also {
					if (it != null) {
						saveGlobal()
					}
				}
			}
			is RouteScope.Room -> {
				synchronized(nodes) {
					if (roomNodes.isEmpty()) {
						null
					} else {
						val relativeNode = roomNodes.removeAt(roomNodes.lastIndex)
						redo.add(relativeNode)
						replaceActiveNodes(materializeRoomNodes(scope.room))
						transformNode(relativeNode, scope.room, toWorld = true)
					}
				}.also {
					if (it != null) {
						saveRoomNodes(scope.room)
					}
				}
			}
			is RouteScope.UnavailableRoom -> null
		}

		if (removed != null) {
			clearRuntime()
		}
		return removed
	}

	fun getNodes(): List<AutoCNode> =
		synchronized(nodes) { nodes.toList() }

	fun createEditSession(): EditSession? {
		val client = Minecraft.getInstance()
		val player = client.player ?: run {
			modMessage("${ChatFormatting.RED}You need to be in-world to edit Auto C nodes.")
			return null
		}
		val scope = editableScope(client) ?: return null
		val playerPos = Pos(player.position())
		return synchronized(nodes) {
			val sourceNodes = when (scope) {
				RouteScope.Global -> globalNodes
				is RouteScope.Room -> roomNodes
				is RouteScope.UnavailableRoom -> return@synchronized null
			}
			EditSession(
				module = this,
				scopeLabel = when (scope) {
					RouteScope.Global -> "Global"
					is RouteScope.Room -> scope.room.displayName
					is RouteScope.UnavailableRoom -> "Unavailable"
				},
				sourceNodes = sourceNodes,
				room = (scope as? RouteScope.Room)?.room,
				playerWorldPos = playerPos,
				entries = editEntries(sourceNodes, (scope as? RouteScope.Room)?.room, playerPos)
			)
		}
	}

	fun refreshEditSession(session: EditSession) {
		synchronized(nodes) {
			session.entries.clear()
			session.entries.addAll(editEntries(session.sourceNodes, session.room, session.playerWorldPos))
		}
	}

	fun saveEditSession(session: EditSession) {
		synchronized(nodes) {
			session.sourceNodes.forEach { it.calculate() }
			if (session.room == null) {
				replaceActiveNodes(globalNodes)
				activeScopeSignature = RouteScope.Global.signature
				activeRoom = null
				saveGlobal()
			} else {
				replaceActiveNodes(materializeRoomNodes(session.room))
				activeScopeSignature = RouteScope.Room(session.room).signature
				activeRoom = session.room
				saveRoomNodes(session.room)
			}
			redo.clear()
		}
		clearRuntime()
	}

	private fun editEntries(sourceNodes: List<AutoCNode>, room: ScannedDungeonRoom?, playerPos: Pos): MutableList<EditEntry> =
		sourceNodes
			.mapIndexedNotNull { index, node ->
				val worldPos = editWorldPos(node, room, index) ?: return@mapIndexedNotNull null
				if (worldPos.squaredDistanceTo(playerPos.asVec3()) <= EDIT_RADIUS_SQ) {
					EditEntry(node, index, worldPos)
				} else {
					null
				}
			}
			.toMutableList()

	private fun editWorldPos(node: AutoCNode, room: ScannedDungeonRoom?, index: Int): Pos? =
		if (room == null) {
			node.pos.copy()
		} else {
			transformNode(node, room, toWorld = true, label = "edit ${index + 1}")?.pos?.copy()
		}

	private fun editableScope(client: Minecraft): RouteScope? {
		val scope = currentScope(client, forceRoomScan = true)
		if (scope is RouteScope.UnavailableRoom) {
			modMessage("${ChatFormatting.RED}${scope.reason}")
			return null
		}
		refreshActiveNodes(client, scope)
		return scope
	}

	private fun refreshActiveNodes(
		client: Minecraft = Minecraft.getInstance(),
		scope: RouteScope = currentScope(client),
		resetRuntimeOnChange: Boolean = true
	) {
		if (scope.signature == activeScopeSignature) {
			return
		}

		val changed = synchronized(nodes) {
			when (scope) {
				RouteScope.Global -> {
					roomNodes.clear()
					replaceActiveNodes(globalNodes)
					activeRoom = null
				}
				is RouteScope.Room -> {
					roomNodes.clear()
					roomNodes.addAll(loadRoomNodes(scope.room))
					replaceActiveNodes(materializeRoomNodes(scope.room))
					activeRoom = scope.room
				}
				is RouteScope.UnavailableRoom -> {
					roomNodes.clear()
					nodes.clear()
					activeRoom = null
				}
			}
			activeScopeSignature = scope.signature
			redo.clear()
			true
		}

		if (changed && resetRuntimeOnChange) {
			clearRuntime()
		}
	}

	private fun currentScope(client: Minecraft = Minecraft.getInstance(), forceRoomScan: Boolean = false): RouteScope {
		if (!Location.area.isArea(Island.DUNGEON) || DungeonState.inBoss) {
			return RouteScope.Global
		}

		val room = DungeonRoomScanner.currentRoom(client, forceScan = forceRoomScan)
		if (room == null) {
			return RouteScope.UnavailableRoom("Couldn't identify this dungeon room yet. Wait for chunks to load and try again.")
		}
		if (!room.canTransform) {
			return RouteScope.UnavailableRoom(
				"Identified ${room.displayName} (${room.core}), but couldn't find the room rotation yet. Move deeper into the room or wait for chunks to load."
			)
		}
		return RouteScope.Room(room)
	}

	private fun parsePlacementArgs(type: AutoCNodeType, args: String): PlacementArgs {
		val trimmed = args.trim()
		if (trimmed.isBlank()) {
			return PlacementArgs()
		}

		val tokens = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
		if (type == AutoCNodeType.COMMAND) {
			var remaining = trimmed
			var placement = PlacementArgs(trimmed)
			while (true) {
				val token = remaining.substringBefore(' ', remaining)
				val parsed = parsePlacementModifier(token) ?: break
				placement = placement.with(parsed)
				remaining = remaining.removePrefix(token).trimStart()
				placement = placement.copy(args = remaining)
			}
			return placement
		}

		var placement = PlacementArgs()
		val kept = arrayListOf<String>()
		for (token in tokens) {
			val parsed = parsePlacementModifier(token)
			if (parsed == null) {
				kept.add(token)
			} else {
				placement = placement.with(parsed)
			}
		}
		return placement.copy(args = kept.joinToString(" "))
	}

	private fun parsePlacementModifier(token: String): PlacementModifier? {
		HEIGHT_OFFSET_ARG.matchEntire(token)?.let { match ->
			return PlacementModifier(heightOffset = match.groupValues[1].toDouble())
		}
		RADIUS_ARG.matchEntire(token)?.let { match ->
			val radius = match.groupValues[1].toFloat()
			return if (radius > 0.0f) PlacementModifier(radius = radius) else null
		}
		WAIT_ARG.matchEntire(token)?.let { match ->
			return PlacementModifier(waitSeconds = match.groupValues[1].toDouble().coerceAtLeast(0.0))
		}
		MAX_ACTIVATIONS_ARG.matchEntire(token)?.let { match ->
			return PlacementModifier(maxActivationsPerMinute = match.groupValues[1].toInt().coerceAtLeast(0))
		}
		if (token.equals("AS", ignoreCase = true)) {
			return PlacementModifier(awaitSecret = true)
		}
		if (token.equals("nr", ignoreCase = true)) {
			return PlacementModifier(notStart = true)
		}
		return null
	}

	private fun replaceActiveNodes(newNodes: Collection<AutoCNode>) {
		nodes.clear()
		nodes.addAll(newNodes)
		nodes.forEach { it.calculate() }
	}

	private fun materializeRoomNodes(room: ScannedDungeonRoom): List<AutoCNode> =
		roomNodes.mapIndexedNotNull { index, node ->
			transformNode(node, room, toWorld = true, label = "${room.displayName} node ${index + 1}")
		}

	private fun transformNode(
		node: AutoCNode,
		room: ScannedDungeonRoom,
		toWorld: Boolean,
		label: String = node.name()
	): AutoCNode? =
		runCatching {
			val json = node.serialize().deepCopy().asJsonObject
			transformNodeJson(json, room, toWorld)
			nodeGson.fromJson(json, AutoCNode::class.java)?.also { it.calculate() }
		}.getOrElse { error ->
			modMessage("${ChatFormatting.RED}Couldn't transform Auto C $label (${node.name()}): ${error.message ?: error::class.java.simpleName}")
			null
		}

	private fun transformNodeJson(json: JsonObject, room: ScannedDungeonRoom, toWorld: Boolean) {
		json.get("pos")?.takeIf { it.isJsonObject }?.asJsonObject?.let { transformPosition(it, room, toWorld) }
		json.get("block")?.takeIf { it.isJsonObject }?.asJsonObject?.let { transformBlockPosition(it, room, toWorld) }
		json.get("hit")?.takeIf { it.isJsonObject }?.asJsonObject?.let { transformPosition(it, room, toWorld) }
		json.get("target")?.takeIf { it.isJsonObject }?.asJsonObject?.let { transformPosition(it, room, toWorld) }
		json.get("blocks")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { element ->
			if (element.isJsonObject) {
				transformBlockPosition(element.asJsonObject, room, toWorld)
			}
		}
		json.get("yaw")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.let { element ->
			val yaw = element.asFloat
			json.addProperty("yaw", if (toWorld) room.toWorldYaw(yaw) else room.toRelativeYaw(yaw))
		}
		transformRecordFrames(json, room, toWorld)
	}

	private fun transformRecordFrames(json: JsonObject, room: ScannedDungeonRoom, toWorld: Boolean) {
		json.get("frames")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { frameElement ->
			if (!frameElement.isJsonObject) {
				return@forEach
			}
			val frame = frameElement.asJsonObject
			frame.get("yaw")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.let { element ->
				val yaw = element.asFloat
				frame.addProperty("yaw", if (toWorld) room.toWorldYaw(yaw) else room.toRelativeYaw(yaw))
			}
			frame.get("lookSamples")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { sampleElement ->
				if (!sampleElement.isJsonObject) {
					return@forEach
				}
				val sample = sampleElement.asJsonObject
				sample.get("yaw")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.let { element ->
					val yaw = element.asFloat
					sample.addProperty("yaw", if (toWorld) room.toWorldYaw(yaw) else room.toRelativeYaw(yaw))
				}
			}
			frame.get("events")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { eventElement ->
				if (!eventElement.isJsonObject) {
					return@forEach
				}
				val event = eventElement.asJsonObject
				event.get("pos")?.takeIf { it.isJsonObject }?.asJsonObject?.let { transformPosition(it, room, toWorld) }
				event.get("block")?.takeIf { it.isJsonObject }?.asJsonObject?.let { transformBlockPosition(it, room, toWorld) }
				event.get("hit")?.takeIf { it.isJsonObject }?.asJsonObject?.let { transformPosition(it, room, toWorld) }
				event.get("target")?.takeIf { it.isJsonObject }?.asJsonObject?.let { transformPosition(it, room, toWorld) }
				event.get("yaw")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.let { element ->
					val yaw = element.asFloat
					event.addProperty("yaw", if (toWorld) room.toWorldYaw(yaw) else room.toRelativeYaw(yaw))
				}
				val direction = event.get("direction")?.asString?.let { raw ->
					runCatching { Direction.valueOf(raw) }.getOrNull()
				}
				if (direction != null) {
					event.addProperty("direction", transformRecordedDirection(direction, room, toWorld).name)
				}
			}
		}
	}

	private fun transformPosition(obj: JsonObject, room: ScannedDungeonRoom, toWorld: Boolean) {
		val pos = Pos(
			obj.get("x")?.asDouble ?: 0.0,
			obj.get("y")?.asDouble ?: 0.0,
			obj.get("z")?.asDouble ?: 0.0
		)
		val transformed = if (toWorld) room.toWorld(pos) else room.toRelative(pos)
		obj.addProperty("x", transformed.x)
		obj.addProperty("y", transformed.y)
		obj.addProperty("z", transformed.z)
	}

	private fun transformBlockPosition(obj: JsonObject, room: ScannedDungeonRoom, toWorld: Boolean) {
		val pos = Pos(
			obj.get("x")?.asDouble ?: 0.0,
			obj.get("y")?.asDouble ?: 0.0,
			obj.get("z")?.asDouble ?: 0.0
		)
		val transformed = if (toWorld) room.toWorldBlock(pos) else room.toRelativeBlock(pos)
		obj.addProperty("x", transformed.x)
		obj.addProperty("y", transformed.y)
		obj.addProperty("z", transformed.z)
	}

	private fun loadRoomNodes(room: ScannedDungeonRoom): MutableList<AutoCNode> {
		val file = roomFile(room)
		if (!file.exists()) {
			return mutableListOf()
		}
		return loadNodeFile(file, room.displayName)
	}

	private fun loadNodeFile(file: Path, label: String): MutableList<AutoCNode> =
		runCatching {
			Files.newBufferedReader(file, StandardCharsets.UTF_8).use { reader ->
				val root = JsonParser.parseReader(reader)
				if (!root.isJsonArray) {
					modMessage("${ChatFormatting.RED}Auto C $label route file is not a node list.")
					return@runCatching mutableListOf()
				}
				readNodeArray(root.asJsonArray, label)
			}
		}.getOrElse { error ->
			modMessage("${ChatFormatting.RED}Couldn't load Auto C $label route file: ${error.message ?: error::class.java.simpleName}")
			mutableListOf()
		}

	private fun readNodeArray(array: JsonArray, label: String): MutableList<AutoCNode> {
		val loaded = mutableListOf<AutoCNode>()
		array.forEachIndexed { index, element ->
			val node = runCatching {
				nodeGson.fromJson(element, AutoCNode::class.java)?.also { it.calculate() }
			}.getOrElse { error ->
				modMessage("${ChatFormatting.RED}Couldn't load Auto C $label node ${index + 1}: ${error.message ?: error::class.java.simpleName}")
				null
			}
			if (node != null) {
				loaded.add(node)
			}
		}
		return loaded
	}

	private fun saveRoomNodes(room: ScannedDungeonRoom) {
		val file = roomFile(room)
		file.parent?.createDirectories()
		Files.newBufferedWriter(file, StandardCharsets.UTF_8).use { writer ->
			nodeGson.toJson(roomNodes, nodeListType, writer)
		}
	}

	private fun roomFile(room: ScannedDungeonRoom): Path =
		FabricLoader.getInstance()
			.configDir
			.resolve("cgc")
			.resolve("dungeon")
			.resolve("auto_c")
			.resolve("rooms")
			.resolve("${room.key}.json")
			.normalize()

	private fun armTerminalExitTrigger() {
		if (terminalExitListenerArmed) {
			return
		}
		terminalExitListenerArmed = true
		TerminalContext.runOnClose {
			terminalExitListenerArmed = false
			terminalExitWindowUntilMs = System.currentTimeMillis() + TERMINAL_EXIT_BUFFER_MS
			terminalExitActivatedNodeIds.clear()
		}
	}

	private fun isTerminalExitWindowActive(): Boolean =
		System.currentTimeMillis() <= terminalExitWindowUntilMs

	private fun handleTerminalExitQueue(player: LocalPlayer, playerPos: Pos): Boolean {
		val terminalNodes = nodes
			.asSequence()
			.filter { it.onTerminalExit && it.isInNode(playerPos) }
			.filter { it.id !in terminalExitActivatedNodeIds }
			.filter { !it.hasRanThisTick(tickTime) }
			.toList()
		val eligible = routeEligibleNodes(terminalNodes).filter { canActivateByRules(it) }
		if (eligible.isEmpty()) {
			return false
		}

		val node = eligible.first()
		if (manualBreakPlan != null && !canRunDuringManualBreak(node)) {
			queueStackedNodes(eligible)
			return false
		}
		queueStackedNodes(eligible.drop(1))
		terminalExitActivatedNodeIds.add(node.id)
		return runRouteNode(node, player, playerPos)
	}

	private fun handleQueue(playerPos: Pos, previousPlayerPos: Pos?): Boolean {
		val player = Minecraft.getInstance().player ?: return false
		if (hasBlockingAwaitInteract()) {
			return false
		}
		if (hasBlockingSecretAwait()) {
			inNode = awaitSecretNode
			return false
		}
		awaitSecretPendingNode?.let { pending ->
			return runAwaitSecretPendingNode(pending, player, playerPos)
		}
		if (bonzoAction != null) {
			return false
		}
		if (System.currentTimeMillis() < routeWaitUntilMs) {
			return false
		}
		if (tickTime <= routeWaitUntilTick) {
			return false
		}
		nextStackedNode()?.let { node ->
			return runRouteNode(node, player, playerPos)
		}
		if (manualBreakPlan != null && stackedNodeQueue.isNotEmpty()) {
			return false
		}
		val triggeredNodes = nodes
			.asSequence()
			.filter { !it.onTerminalExit }
			.filter { it.canTrigger(playerPos, previousPlayerPos) || (routeActive && it.isInNode(playerPos)) }
			.filter { !it.isTriggered() && !it.hasRanThisTick(tickTime) }
			.toList()
		val eligible = routeEligibleNodes(triggeredNodes).filter { canActivateByRules(it) }

		if (eligible.isEmpty()) {
			inNode = null
			return false
		}

		val node = eligible.first()
		if (manualBreakPlan != null && !canRunDuringManualBreak(node)) {
			queueStackedNodes(eligible)
			return false
		}
		queueStackedNodes(eligible.drop(1))
		return runRouteNode(node, player, playerPos)
	}

	private fun routeEligibleNodes(triggeredNodes: List<AutoCNode>): List<AutoCNode> {
		if (routeActive) {
			return triggeredNodes
		}

		val starter = triggeredNodes.firstOrNull { !it.notStart } ?: return emptyList()
		return listOf(starter) + triggeredNodes.filter { it !== starter }
	}

	private fun nextStackedNode(): AutoCNode? {
		while (stackedNodeQueue.isNotEmpty()) {
			val node = stackedNodeQueue.first()
			if (node.hasRanThisTick(tickTime) || (!routeActive && node.notStart)) {
				stackedNodeQueue.removeAt(0)
				continue
			}
			if (!canActivateByRules(node)) {
				stackedNodeQueue.removeAt(0)
				continue
			}
			if (manualBreakPlan != null && !canRunDuringManualBreak(node)) {
				return null
			}
			return stackedNodeQueue.removeAt(0)
		}
		return null
	}

	private fun queueStackedNodes(nodesToQueue: Collection<AutoCNode>) {
		for (node in nodesToQueue) {
			if (node !in stackedNodeQueue) {
				stackedNodeQueue.add(node)
			}
		}
	}

	private fun canRunDuringManualBreak(node: AutoCNode): Boolean =
		node is WalkNode || node is StrafeNode || node is CrouchNode

	private fun runRouteNode(node: AutoCNode, player: LocalPlayer, playerPos: Pos): Boolean {
		if (node.notStart && !routeActive) {
			return false
		}
		if (!canActivateByRules(node)) {
			return false
		}
		if (!routeActive) {
			clearRouteActivationState()
		}
		routeActive = true
		inNode = node
		if (node.awaitSecret && !node.handlesAwaitSecretInRun()) {
			node.prepareAwaitSecret(nodeContext)
			node.preTrigger(tickTime)
			startSecretAwait(node, pendingNode = node)
			return false
		}
		val triggerBeforeRun = node !is BreakNode
		if (triggerBeforeRun) {
			node.preTrigger(tickTime)
		}
		val ran = node.run(player, playerPos, nodeContext)
		val routeDelayTicks = node.routeDelayTicks().coerceAtLeast(0)
		if (ran) {
			if (!triggerBeforeRun) {
				node.preTrigger(tickTime)
			}
			noteNodeActivated(node)
			if (node.waitSeconds > 0.0) {
				routeWaitUntilMs = System.currentTimeMillis() + (node.waitSeconds * 1000.0).toLong().coerceAtLeast(1L)
			}
			if (routeDelayTicks > 0) {
				routeWaitUntilTick = maxOf(routeWaitUntilTick, tickTime + routeDelayTicks)
			}
		}
		if (ran && (node.awaitSecret || node.waitSeconds > 0.0 || routeDelayTicks > 0)) {
			return false
		}
		return ran
	}

	private fun runAwaitSecretPendingNode(node: AutoCNode, player: LocalPlayer, playerPos: Pos): Boolean {
		awaitSecretPendingNode = null
		routeActive = true
		inNode = node
		if (!canActivateByRules(node)) {
			return false
		}
		val ran = node.run(player, playerPos, nodeContext)
		val routeDelayTicks = node.routeDelayTicks().coerceAtLeast(0)
		if (ran) {
			noteNodeActivated(node)
		}
		if (ran && node.waitSeconds > 0.0) {
			routeWaitUntilMs = System.currentTimeMillis() + (node.waitSeconds * 1000.0).toLong().coerceAtLeast(1L)
		}
		if (ran && routeDelayTicks > 0) {
			routeWaitUntilTick = maxOf(routeWaitUntilTick, tickTime + routeDelayTicks)
		}
		return false
	}

	private fun hasBlockingAwaitInteract(): Boolean {
		val action = interactAction?.takeIf { it.await } ?: queuedInteractAction?.takeIf { it.await } ?: return false
		return action.await
	}

	private fun canActivateByRules(node: AutoCNode, now: Long = System.currentTimeMillis()): Boolean {
		if (node.requiredNodeIds.any { it !in activatedNodeIds }) {
			return false
		}
		val max = node.maxActivationsPerMinute
		if (max <= 0) {
			return true
		}
		val times = nodeActivationTimes.getOrPut(node.id) { ArrayDeque<Long>() }
		while (times.isNotEmpty() && now - times.peekFirst() >= NODE_ACTIVATION_WINDOW_MS) {
			times.removeFirst()
		}
		return times.size < max
	}

	private fun noteNodeActivated(node: AutoCNode, now: Long = System.currentTimeMillis()) {
		activatedNodeIds.add(node.id)
		val max = node.maxActivationsPerMinute
		if (max <= 0) {
			return
		}
		val times = nodeActivationTimes.getOrPut(node.id) { ArrayDeque<Long>() }
		while (times.isNotEmpty() && now - times.peekFirst() >= NODE_ACTIVATION_WINDOW_MS) {
			times.removeFirst()
		}
		times.addLast(now)
	}

	private fun clearRouteActivationState() {
		activatedNodeIds.clear()
	}

	private fun hasBlockingSecretAwait(): Boolean =
		hasActiveSecretAwait()

	private fun startSecretAwait(node: AutoCNode?, pendingNode: AutoCNode? = null, completeOnFirstCounter: Boolean = false) {
		awaitSecretNode = node
		awaitSecretPendingNode = pendingNode
		awaitSecretRemaining = 1
		awaitSecretBaseline = currentSecretCount
		awaitSecretMax = currentSecretMax
		awaitSecretCompleteOnFirstCounter = completeOnFirstCounter && currentSecretCount == null
	}

	private fun hasActiveSecretAwait(): Boolean =
		awaitSecretRemaining > 0

	private fun clearSecretAwait(clearPending: Boolean = true) {
		awaitSecretRemaining = 0
		awaitSecretNode = null
		awaitSecretBaseline = null
		awaitSecretMax = null
		awaitSecretCompleteOnFirstCounter = false
		if (clearPending) {
			awaitSecretPendingNode = null
		}
	}

	private fun consumeSecretAwait(amount: Int = 1) {
		if (!hasActiveSecretAwait()) {
			return
		}

		awaitSecretRemaining -= amount
		if (awaitSecretRemaining <= 0) {
			completeSecretAwait()
		}
	}

	private fun completeSecretAwait() {
		val completedNode = awaitSecretNode
		awaitSecretRemaining = 0
		awaitSecretNode = null
		awaitSecretBaseline = null
		awaitSecretMax = null
		awaitSecretCompleteOnFirstCounter = false
		if (interactAction?.awaitNode === completedNode) {
			interactAction = null
		}
		if (queuedInteractAction?.awaitNode === completedNode) {
			queuedInteractAction = null
		}
	}

	private fun updateAwaitSecretCrouch(client: Minecraft) {
		val shouldHold = hasActiveSecretAwait() && awaitSecretNode is EtherwarpNode
		if (shouldHold) {
			inputController.press(client.options.keyShift)
			awaitSecretCrouchHeld = true
			return
		}

		if (!awaitSecretCrouchHeld) {
			return
		}
		awaitSecretCrouchHeld = false
		if (useAction?.sneak == true || useKeySneak || etherwarpShiftHoldTicks > 0 || shouldHoldEtherwarpShift(client)) {
			return
		}
		inputController.release(client.options.keyShift)
	}

	private fun consumeSecretAwaitPacket(packet: Packet<*>) {
		if (!hasActiveSecretAwait()) {
			return
		}

		val client = Minecraft.getInstance()
		val level = client.level ?: return
		when (packet) {
			is ClientboundTakeItemEntityPacket -> {
				val entity = level.getEntity(packet.itemId) as? ItemEntity ?: return
				if (isSecretItem(entity)) {
					consumeSecretAwait()
				}
			}
			is ClientboundRemoveEntitiesPacket -> {
				val player = client.player ?: return
				val ids = packet.entityIds
				for (i in 0 until ids.size) {
					val id = ids.getInt(i)
					val entity = level.getEntity(id) as? ItemEntity ?: continue
					if (entity.distanceToSqr(player) < SECRET_ITEM_REMOVE_RANGE_SQ && isSecretItem(entity)) {
						consumeSecretAwait()
						if (!hasActiveSecretAwait()) {
							return
						}
					}
				}
			}
		}
	}

	private fun isSecretItem(entity: ItemEntity): Boolean {
		val name = ChatFormatting.stripFormatting(entity.item.hoverName.string)?.trim()
			?: entity.item.hoverName.string.trim()
		return name in SECRET_NAMES
	}

	private fun parseSecretCounter(message: String): SecretCounter? {
		val text = ChatFormatting.stripFormatting(message) ?: message
		val match = SECRET_COUNTER_PATTERN.find(text) ?: return null
		val current = match.groupValues[1].replace(",", "").toIntOrNull() ?: return null
		val max = match.groupValues[2].replace(",", "").toIntOrNull() ?: return null
		return SecretCounter(current, max)
	}

	private fun startSmoothLook(yaw: Float, pitch: Float) {
		val player = Minecraft.getInstance().player ?: return
		walkPlan?.takeIf { !it.looking }?.nodeLookActive = true
		lookController.start(player, yaw, pitch)
	}

	private fun startFastLook(yaw: Float, pitch: Float) {
		val player = Minecraft.getInstance().player ?: return
		walkPlan?.takeIf { !it.looking }?.nodeLookActive = true
		lookController.startFast(player, yaw, pitch)
	}

	private fun startManualBreakLook(yaw: Float, pitch: Float) {
		walkPlan?.nodeLookActive = true
		startSmoothLook(yaw, pitch)
	}

	private fun startWalkAction(yaw: Float, pitch: Float) {
		val client = Minecraft.getInstance()
		useAction = null
		interactAction = null
		strafePlan = null
		inputController.releaseMovement(client)
		walkPlan = WalkPlan(yaw, pitch.coerceIn(-90.0f, 90.0f), inputController.movementInputBaseline(client), looking = true)
		startSmoothLook(yaw, pitch)
	}

	private fun updateWalk(client: Minecraft, player: LocalPlayer) {
		val plan = walkPlan ?: return
		val breaking = manualBreakPlan != null
		if (inputController.movementInputDown(client, plan.inputBaseline)) {
			stopActions()
			return
		}

		if (lookController.hasPlan()) {
			if (breaking) {
				player.yRot = plan.yaw
				player.yHeadRot = plan.yaw
				inputController.press(client.options.keyUp, client.options.keySprint)
				return
			}
			if (plan.looking && !plan.nodeLookActive) {
				return
			}
			inputController.press(client.options.keyUp, client.options.keySprint)
			return
		}
		if (plan.looking) {
			plan.looking = false
		}
		if (plan.nodeLookActive) {
			plan.nodeLookActive = false
			player.yRot = plan.yaw
			if (!breaking) {
				player.xRot = plan.pitch
			}
			player.yHeadRot = plan.yaw
		}
		if (walkMouseMoved(player, plan, ignorePitch = breaking)) {
			stopActions()
			return
		}

		player.yRot = plan.yaw
		if (!breaking) {
			player.xRot = plan.pitch
		}
		player.yHeadRot = plan.yaw
		inputController.press(client.options.keyUp, client.options.keySprint)
	}

	private fun startStrafeAction(direction: AutoCStrafeDirection) {
		val client = Minecraft.getInstance()
		walkPlan = null
		useAction = null
		interactAction = null
		inputController.releaseMovement(client)
		strafePlan = StrafePlan(direction, inputController.movementInputBaseline(client))
	}

	private fun updateStrafe(client: Minecraft) {
		val plan = strafePlan ?: return
		if (inputController.movementInputDown(client, plan.inputBaseline)) {
			stopActions()
			return
		}

		when (plan.direction) {
			AutoCStrafeDirection.W -> inputController.press(client.options.keyUp)
			AutoCStrafeDirection.A -> inputController.press(client.options.keyLeft)
			AutoCStrafeDirection.S -> inputController.press(client.options.keyDown)
			AutoCStrafeDirection.D -> inputController.press(client.options.keyRight)
		}
	}

	private fun walkMouseMoved(player: LocalPlayer, plan: WalkPlan, ignorePitch: Boolean = false): Boolean {
		val yawDiff = abs(Mth.wrapDegrees(player.yRot - plan.yaw))
		val pitchDiff = if (ignorePitch) 0.0f else abs(player.xRot - plan.pitch)
		return yawDiff > WALK_MOUSE_CANCEL_DEGREES || pitchDiff > WALK_MOUSE_CANCEL_DEGREES
	}

	private fun startUseAction(yaw: Float, pitch: Float, sneak: Boolean, itemIds: List<String>, targetBlock: BlockPos? = null) {
		val client = Minecraft.getInstance()
		val player = client.player
		val useStoredRotation = player == null || targetBlock == null || !isAimingAtBlock(player, targetBlock)
		val etherwarp = sneak && targetBlock != null
		manualBreakPlan = null
		interactAction = null
		edgeUntilMs = 0L
		inputController.release(client.options.keyAttack)
		if (!etherwarp) {
			etherwarpShiftHoldTicks = 0
			inputController.release(client.options.keyShift)
		}
		useAction = UseAction(
			yaw = yaw,
			pitch = pitch.coerceIn(-90.0f, 90.0f),
			sneak = sneak,
			skyBlockIds = itemIds,
			targetBlock = targetBlock,
			useStoredRotation = useStoredRotation,
			etherwarp = etherwarp
		)
		if (useStoredRotation) {
			startSmoothLook(yaw, pitch)
		}
	}

	private fun startHeldUseAction(yaw: Float, pitch: Float, skyBlockId: String, itemId: String, displayName: String) {
		val client = Minecraft.getInstance()
		bonzoAction = null
		manualBreakPlan = null
		interactAction = null
		edgeUntilMs = 0L
		etherwarpShiftHoldTicks = 0
		inputController.release(client.options.keyAttack)
		inputController.release(client.options.keyShift)
		useAction = UseAction(yaw, pitch.coerceIn(-90.0f, 90.0f), sneak = false, skyBlockId = skyBlockId, itemId = itemId, displayName = displayName)
		startSmoothLook(yaw, pitch)
	}

	private fun startBonzoAction(yaw: Float, pitch: Float) {
		val client = Minecraft.getInstance()
		val player = client.player ?: return
		val targetYaw = yaw
		val targetPitch = pitch.coerceIn(-90.0f, 90.0f)
		useAction = null
		manualBreakPlan = null
		interactAction = null
		queuedInteractAction = null
		edgeUntilMs = 0L
		useKeyTicks = 0
		useKeySneak = false
		etherwarpShiftHoldTicks = 0
		inputController.release(client.options.keyAttack)
		inputController.release(client.options.keyUse)
		inputController.release(client.options.keyShift)
		if (!selectBonzoStaff()) {
			modMessage("${ChatFormatting.RED}Missing Bonzo Staff in hotbar.")
			bonzoAction = null
			return
		}
		bonzoAction = BonzoAction(targetYaw, targetPitch)
		startFastLook(targetYaw, targetPitch)
		if (!lookController.hasPlan()) {
			player.yRot = targetYaw
			player.xRot = targetPitch
			player.yHeadRot = targetYaw
		}
	}

	private fun updateBonzoAction(client: Minecraft, player: LocalPlayer) {
		val action = bonzoAction ?: return
		if (!selectBonzoStaff()) {
			modMessage("${ChatFormatting.RED}Missing Bonzo Staff in hotbar.")
			bonzoAction = null
			return
		}
		if (lookController.hasPlan()) {
			return
		}
		player.yRot = action.yaw
		player.xRot = action.pitch
		player.yHeadRot = action.yaw
		pressBonzoUseKey(client)
		bonzoAction = null
	}

	private fun selectBonzoStaff(): Boolean =
		ItemInteractionUtils.selectHotbarItem(BONZO_STAFF_ID)

	private fun pressBonzoUseKey(client: Minecraft) {
		inputController.release(client.options.keyShift)
		inputController.release(client.options.keyUse)
		useKeySneak = false
		etherwarpShiftHoldTicks = 0
		KeyMapping.click(InputConstants.getKey(client.options.keyUse.saveString()))
		inputController.press(client.options.keyUse)
		useKeyTicks = maxOf(useKeyTicks, BONZO_USE_KEY_HOLD_TICKS)
	}

	private fun updateUseAction(client: Minecraft) {
		val action = useAction ?: return

		if (!action.prepared) {
			if (!selectUseItem(action)) {
				modMessage("${ChatFormatting.RED}Missing ${action.itemLabel()} in hotbar.")
				inputController.release(client.options.keyShift)
				etherwarpShiftHoldTicks = 0
				useAction = null
				return
			}
			if (action.sneak) {
				inputController.press(client.options.keyShift)
			}
			action.prepared = true
		}

		if (!selectUseItem(action)) {
			modMessage("${ChatFormatting.RED}Missing ${action.itemLabel()} in hotbar.")
			inputController.release(client.options.keyShift)
			etherwarpShiftHoldTicks = 0
			useAction = null
			return
		}
		if (lookController.hasPlan()) {
			return
		}
		if (action.sneak) {
			inputController.press(client.options.keyShift)
		}
		client.player?.let { player ->
			if (!action.useStoredRotation && action.targetBlock != null && !isAimingAtBlock(player, action.targetBlock)) {
				action.useStoredRotation = true
				startSmoothLook(action.yaw, action.pitch)
				return
			}
		}
		if (action.useStoredRotation) {
			client.player?.let { player ->
				player.yRot = action.yaw
				player.xRot = action.pitch
				player.yHeadRot = action.yaw
			}
		}
		pressUseKey(client, action)
		useAction = null
	}

	private fun selectUseItem(action: UseAction): Boolean =
		if (action.skyBlockIds.isNotEmpty()) {
			ItemInteractionUtils.selectHotbarItem(*action.skyBlockIds.toTypedArray())
		} else {
			ItemInteractionUtils.selectHotbarItem(action.skyBlockId, action.itemId, action.displayName)
		}

	private fun pressUseKey(client: Minecraft, action: UseAction) {
		KeyMapping.click(InputConstants.getKey(client.options.keyUse.saveString()))
		inputController.press(client.options.keyUse)
		useKeyTicks = if (action.etherwarp) ETHERWARP_USE_KEY_HOLD_TICKS else USE_KEY_HOLD_TICKS
		useKeySneak = action.sneak
		if (action.etherwarp) {
			etherwarpShiftHoldTicks = maxOf(etherwarpShiftHoldTicks, ETHERWARP_SHIFT_GRACE_TICKS)
		}
	}

	private fun updateUseKeyHold(client: Minecraft) {
		if (useKeyTicks <= 0) {
			updateEtherwarpShiftHold(client)
			return
		}
		useKeyTicks--
		if (useKeyTicks > 0) {
			inputController.press(client.options.keyUse)
			if (useKeySneak) {
				inputController.press(client.options.keyShift)
			}
			return
		}

		inputController.release(client.options.keyUse)
		if (useKeySneak) {
			if (etherwarpShiftHoldTicks > 0 || shouldHoldEtherwarpShift(client)) {
				inputController.press(client.options.keyShift)
				if (shouldHoldEtherwarpShift(client)) {
					etherwarpShiftHoldTicks = ETHERWARP_SHIFT_GRACE_TICKS
				}
			} else {
				inputController.release(client.options.keyShift)
				etherwarpShiftHoldTicks = 0
			}
		}
		useKeySneak = false
	}

	private fun updateEtherwarpShiftHold(client: Minecraft) {
		if (etherwarpShiftHoldTicks <= 0) {
			return
		}
		if (shouldHoldEtherwarpShift(client)) {
			inputController.press(client.options.keyShift)
			etherwarpShiftHoldTicks = ETHERWARP_SHIFT_GRACE_TICKS
			return
		}
		etherwarpShiftHoldTicks--
		if (etherwarpShiftHoldTicks > 0) {
			inputController.press(client.options.keyShift)
			return
		}
		inputController.release(client.options.keyShift)
	}

	private fun shouldHoldEtherwarpShift(client: Minecraft): Boolean {
		if (useAction?.etherwarp == true) {
			return true
		}
		nextStackedRouteNode()?.let { node ->
			return node is EtherwarpNode
		}
		val player = client.player ?: return false
		val playerPos = Pos(player.position())
		return synchronized(nodes) {
			val triggeredNodes = nodes
				.asSequence()
				.filter { it.isInNode(playerPos) }
				.filter { !it.isTriggered() && !it.hasRanThisTick(tickTime) }
				.toList()
			routeEligibleNodes(triggeredNodes).firstOrNull() is EtherwarpNode
		}
	}

	private fun nextStackedRouteNode(): AutoCNode? =
		stackedNodeQueue.firstOrNull { !it.hasRanThisTick(tickTime) && (routeActive || !it.notStart) }

	private fun startInteractAction(yaw: Float, pitch: Float, block: BlockPos, hit: Vec3, await: Boolean) {
		val client = Minecraft.getInstance()
		walkPlan = null
		strafePlan = null
		useAction = null
		useKeyTicks = 0
		useKeySneak = false
		etherwarpShiftHoldTicks = 0
		manualBreakPlan = null
		edgeUntilMs = 0L
		inputController.release(client.options.keyUse)
		inputController.release(client.options.keyShift)
		inputController.releaseMovement(client)
		interactAction = InteractAction(
			yaw = yaw,
			pitch = pitch.coerceIn(-90.0f, 90.0f),
			block = block,
			hit = hit,
			await = await,
			awaitNode = if (await) inNode else null,
			inputBaseline = inputController.movementInputBaseline(client)
		)
		startSmoothLook(yaw, pitch)
	}

	private fun updateInteractAction(client: Minecraft, player: LocalPlayer) {
		val action = interactAction ?: return
		if (interactCancelledByPlayerInput(client, player, action)) {
			stopActions()
			return
		}
		if (action.retryAtMs > System.currentTimeMillis()) {
			return
		}
		if (lookController.hasPlan() || queuedInteractAction != null) {
			return
		}

		queuedInteractAction = action
		interactAction = null
	}

	private fun executeQueuedInteract(client: Minecraft) {
		val action = queuedInteractAction ?: return
		val player = client.player ?: return
		if (interactCancelledByPlayerInput(client, player, action)) {
			stopActions()
			return
		}
		if (client.screen != null) {
			if (!action.await) {
				queuedInteractAction = null
			}
			return
		}
		if (action.await) {
			if (!hasActiveSecretAwait()) {
				startSecretAwait(action.awaitNode, completeOnFirstCounter = true)
			}
		}
		queuedInteractAction = null

		when (performInteract(client, player, action)) {
			InteractAttempt.RETRY -> {
				interactAction = action.copy(retryAtMs = System.currentTimeMillis() + if (action.await) INTERACT_SECRET_RETRY_MS else 1L)
				startSmoothLook(action.yaw, action.pitch)
				return
			}
			InteractAttempt.ABORT -> {
				if (action.await) {
					clearSecretAwait()
				}
				return
			}
			InteractAttempt.SUCCESS -> {
			}
		}

		if (!action.await) {
			return
		}

		if (hasActiveSecretAwait()) {
			interactAction = action.copy(retryAtMs = System.currentTimeMillis() + INTERACT_SECRET_RETRY_MS)
		}
	}

	private fun performInteract(client: Minecraft, player: LocalPlayer, action: InteractAction): InteractAttempt {
		val hit = currentBlockHit(player) ?: return InteractAttempt.RETRY
		if (hit.blockPos != action.block) {
			return InteractAttempt.RETRY
		}
		if (!ItemInteractionUtils.selectHotbarItem("DUNGEONBREAKER")) {
			modMessage("${ChatFormatting.RED}Missing Dungeonbreaker in hotbar.")
			return InteractAttempt.ABORT
		}
		client.hitResult = hit
		pressInteractKey(client)
		return InteractAttempt.SUCCESS
	}

	private fun interactCancelledByPlayerInput(client: Minecraft, player: LocalPlayer, action: InteractAction): Boolean {
		if (inputController.movementInputDown(client, action.inputBaseline)) {
			return true
		}
		if (lookController.hasPlan()) {
			return false
		}
		val yawDiff = abs(Mth.wrapDegrees(player.yRot - action.yaw))
		val pitchDiff = abs(player.xRot - action.pitch)
		return yawDiff > INTERACT_MOUSE_CANCEL_DEGREES || pitchDiff > INTERACT_MOUSE_CANCEL_DEGREES
	}

	private fun pressInteractKey(client: Minecraft) {
		inputController.release(client.options.keyShift)
		inputController.release(client.options.keyUse)
		useKeySneak = false
		etherwarpShiftHoldTicks = 0
		KeyMapping.click(InputConstants.getKey(client.options.keyUse.saveString()))
		inputController.press(client.options.keyUse)
		useKeyTicks = maxOf(useKeyTicks, USE_KEY_HOLD_TICKS)
	}

	private fun updateRouteActiveState(client: Minecraft) {
		if (routeActive && !hasActiveRouteWork() && inputController.anyMovementInputDown(client)) {
			routeActive = false
			clearRouteActivationState()
		}
	}

	private fun hasActiveRouteWork(): Boolean {
		val now = System.currentTimeMillis()
		return walkPlan != null
			|| strafePlan != null
			|| useAction != null
			|| bonzoAction != null
			|| useKeyTicks > 0
			|| etherwarpShiftHoldTicks > 0
			|| interactAction != null
			|| queuedInteractAction != null
			|| crouchAction != null
			|| manualBreakPlan != null
			|| recordPlayback != null
			|| lookController.hasPlan()
			|| autoJumpTicks > 0
			|| pendingJumpUntilMs > now
			|| edgeUntilMs > now
			|| routeWaitUntilMs > now
			|| tickTime <= routeWaitUntilTick
			|| hasActiveSecretAwait()
			|| awaitSecretPendingNode != null
			|| stackedNodeQueue.isNotEmpty()
	}

	private fun runStoredCommand(command: String) {
		val trimmed = command.trim()
		if (trimmed.isBlank()) {
			return
		}
		Minecraft.getInstance().connection?.sendCommand(trimmed.removePrefix("/"))
	}

	private fun scheduleJump() {
		val client = Minecraft.getInstance()
		val player = client.player ?: return
		if (player.onGround()) {
			pressJumpNow(client, 2)
			return
		}
		pendingJumpUntilMs = System.currentTimeMillis() + JUMP_GROUND_WAIT_MS
	}

	private fun pressJumpNow(client: Minecraft, ticks: Int) {
		inputController.press(client.options.keyJump)
		autoJumpTicks = maxOf(autoJumpTicks, ticks)
	}

	private fun updateAutoJump(client: Minecraft) {
		val player = client.player
		if (pendingJumpUntilMs > 0L) {
			when {
				player?.onGround() == true -> {
					pendingJumpUntilMs = 0L
					pressJumpNow(client, 2)
				}
				System.currentTimeMillis() > pendingJumpUntilMs -> pendingJumpUntilMs = 0L
			}
		}
		if (autoJumpTicks <= 0) {
			return
		}
		autoJumpTicks--
		if (autoJumpTicks <= 0) {
			inputController.release(client.options.keyJump)
		}
	}

	private fun startCrouchAction(seconds: Double) {
		val client = Minecraft.getInstance()
		if (client.player == null) {
			return
		}
		val now = System.currentTimeMillis()
		crouchAction = CrouchAction(
			releaseAtMs = if (seconds > 0.0) now + (seconds * 1000.0).toLong().coerceAtLeast(1L) else 0L
		)
		inputController.press(client.options.keyShift)
	}

	private fun updateCrouchAction(client: Minecraft, player: LocalPlayer) {
		val action = crouchAction ?: return
		val now = System.currentTimeMillis()
		if (action.releaseAtMs > 0L) {
			if (now >= action.releaseAtMs) {
				finishCrouchAction(client)
			} else {
				inputController.press(client.options.keyShift)
			}
			return
		}

		inputController.press(client.options.keyShift)
		updateCrouchMoveDirection(player, action)
		if (!player.onGround() || !isPlayerInCoyoteEdgeBuffer(client, player, action) || !isHorizontalVelocitySettled(player)) {
			action.edgeSettledSinceMs = 0L
			return
		}

		if (action.edgeSettledSinceMs == 0L) {
			action.edgeSettledSinceMs = now
			return
		}
		if (now - action.edgeSettledSinceMs >= CROUCH_EDGE_SETTLE_MS) {
			finishCrouchAction(client)
		}
	}

	private fun finishCrouchAction(client: Minecraft) {
		crouchAction = null
		releaseCrouchKeyIfUnused(client)
	}

	private fun releaseCrouchKeyIfUnused(client: Minecraft) {
		if (awaitSecretCrouchHeld || useKeySneak || etherwarpShiftHoldTicks > 0 || shouldHoldEtherwarpShift(client)) {
			return
		}
		inputController.release(client.options.keyShift)
	}

	private fun isHorizontalVelocitySettled(player: LocalPlayer): Boolean {
		val velocity = player.deltaMovement
		return velocity.x * velocity.x + velocity.z * velocity.z <= CROUCH_EDGE_SETTLE_SPEED_SQ
	}

	private fun updateCrouchMoveDirection(player: LocalPlayer, action: CrouchAction) {
		walkPlan?.let { plan ->
			val dir = horizontalDirection(plan.yaw)
			action.moveDirX = dir.x
			action.moveDirZ = dir.z
			return
		}
		strafePlan?.let { plan ->
			val yaw = when (plan.direction) {
				AutoCStrafeDirection.W -> player.yRot
				AutoCStrafeDirection.A -> player.yRot - 90.0f
				AutoCStrafeDirection.S -> player.yRot + 180.0f
				AutoCStrafeDirection.D -> player.yRot + 90.0f
			}
			val dir = horizontalDirection(yaw)
			action.moveDirX = dir.x
			action.moveDirZ = dir.z
			return
		}

		val velocity = player.deltaMovement
		val speedSq = velocity.x * velocity.x + velocity.z * velocity.z
		if (speedSq > CROUCH_DIRECTION_SPEED_SQ) {
			val speed = sqrt(speedSq)
			action.moveDirX = velocity.x / speed
			action.moveDirZ = velocity.z / speed
		}
	}

	private fun horizontalDirection(yaw: Float): HorizontalDirection {
		val radians = Math.toRadians(yaw.toDouble())
		return HorizontalDirection(-sin(radians), cos(radians))
	}

	private fun isPlayerInCoyoteEdgeBuffer(client: Minecraft, player: LocalPlayer, action: CrouchAction): Boolean {
		val level = client.level ?: return false
		val box = player.boundingBox
		var dirX = action.moveDirX
		var dirZ = action.moveDirZ
		if (dirX * dirX + dirZ * dirZ <= 1.0E-8) {
			val dir = horizontalDirection(player.yRot)
			dirX = dir.x
			dirZ = dir.z
		}
		val centerX = (box.minX + box.maxX) * 0.5
		val centerZ = (box.minZ + box.maxZ) * 0.5
		val footY = box.minY
		val halfExtent = directionalHalfExtent(box, centerX, centerZ, dirX, dirZ)
		var supported = false
		var furthestSupportedProjection = Double.NEGATIVE_INFINITY
		for (xIndex in 0..CROUCH_EDGE_SUPPORT_GRID) {
			val x = box.minX + CROUCH_EDGE_SUPPORT_INSET + (box.xsize - CROUCH_EDGE_SUPPORT_INSET * 2.0) * xIndex / CROUCH_EDGE_SUPPORT_GRID
			for (zIndex in 0..CROUCH_EDGE_SUPPORT_GRID) {
				val z = box.minZ + CROUCH_EDGE_SUPPORT_INSET + (box.zsize - CROUCH_EDGE_SUPPORT_INSET * 2.0) * zIndex / CROUCH_EDGE_SUPPORT_GRID
				if (!hasFootSupport(level, player, x, footY, z)) {
					continue
				}
				supported = true
				val projection = (x - centerX) * dirX + (z - centerZ) * dirZ
				furthestSupportedProjection = maxOf(furthestSupportedProjection, projection)
			}
		}

		return !supported || furthestSupportedProjection <= -halfExtent + CROUCH_EDGE_REAR_SUPPORT_MARGIN
	}

	private fun directionalHalfExtent(box: AABB, centerX: Double, centerZ: Double, dirX: Double, dirZ: Double): Double =
		maxOf(
			abs((box.minX - centerX) * dirX + (box.minZ - centerZ) * dirZ),
			abs((box.minX - centerX) * dirX + (box.maxZ - centerZ) * dirZ),
			abs((box.maxX - centerX) * dirX + (box.minZ - centerZ) * dirZ),
			abs((box.maxX - centerX) * dirX + (box.maxZ - centerZ) * dirZ)
		)

	private fun hasFootSupport(level: Level, player: LocalPlayer, x: Double, footY: Double, z: Double): Boolean {
		val probe = AABB(
			x - CROUCH_EDGE_PROBE_RADIUS,
			footY - CROUCH_EDGE_PROBE_DEPTH,
			z - CROUCH_EDGE_PROBE_RADIUS,
			x + CROUCH_EDGE_PROBE_RADIUS,
			footY + CROUCH_EDGE_PROBE_HEIGHT,
			z + CROUCH_EDGE_PROBE_RADIUS
		)
		return level.getBlockCollisions(player, probe).iterator().hasNext()
	}

	private fun armEdge() {
		edgeUntilMs = System.currentTimeMillis() + EDGE_ACTIVE_MS
	}

	private fun updateEdge(client: Minecraft, player: LocalPlayer) {
		if (edgeUntilMs <= 0L) {
			return
		}
		if (System.currentTimeMillis() > edgeUntilMs) {
			edgeUntilMs = 0L
			return
		}
		if (!player.onGround()
			|| inputController.physicalDown(client, client.options.keyJump)
			|| inputController.physicalDown(client, client.options.keyShift)
		) {
			return
		}

		val level = client.level ?: return
		val supportBox = player.boundingBox
			.move(0.0, EDGE_CHECK_Y_OFFSET, 0.0)
			.inflate(-EDGE_DISTANCE, 0.0, -EDGE_DISTANCE)
		if (!level.getBlockCollisions(player, supportBox).iterator().hasNext()) {
			edgeUntilMs = 0L
			pressJumpNow(client, 2)
		}
	}

	private fun startLeap(clazz: DungeonClass) {
		val player = Minecraft.getInstance().player ?: return
		val target = DungeonState.getClassPlayer(clazz)
		if (target == null || target.name.equals(player.name.string, ignoreCase = true)) {
			modMessage("${ChatFormatting.RED}Couldn't find a ${clazz.displayName} to leap to.")
			return
		}

		if (!ItemInteractionUtils.selectHotbarItem("INFINITE_SPIRIT_LEAP", "SPIRIT_LEAP")) {
			modMessage("${ChatFormatting.RED}Missing Spirit Leap or Infinite Spirit Leap in hotbar.")
			return
		}

		leapMenu.start(target.name)
	}

	private fun stopActions(except: Set<String> = emptySet()) {
		val preserve = StopPreserve.from(except)
		routeActive = false
		clearRouteActivationState()
		if (!preserve.walk) {
			walkPlan = null
		}
		if (!preserve.strafe) {
			strafePlan = null
		}
		if (!preserve.use) {
			useAction = null
			useKeyTicks = 0
			useKeySneak = false
			etherwarpShiftHoldTicks = 0
		}
		if (!preserve.bonzo) {
			bonzoAction = null
		}
		if (!preserve.crouch) {
			crouchAction = null
		}
		if (!preserve.interact) {
			interactAction = null
			queuedInteractAction = null
		}
		if (!preserve.breakBlocks) {
			manualBreakPlan = null
		}
		if (!preserve.record) {
			recordPlayback = null
		}
		if (!preserve.edge) {
			edgeUntilMs = 0L
		}
		if (!preserve.jump) {
			autoJumpTicks = 0
			pendingJumpUntilMs = 0L
		}
		if (!preserve.look) {
			lookController.clear()
		}
		routeWaitUntilMs = 0L
		routeWaitUntilTick = 0
		stackedNodeQueue.clear()
		if (!preserve.awaitSecret) {
			clearSecretAwait()
			awaitSecretCrouchHeld = false
		}
		inputController.releaseAll()
		restorePreservedInputs(Minecraft.getInstance(), preserve)
	}

	private fun restorePreservedInputs(client: Minecraft, preserve: StopPreserve) {
		if (preserve.crouch && crouchAction != null) {
			inputController.press(client.options.keyShift)
		}
		if (preserve.walk && walkPlan != null) {
			inputController.press(client.options.keyUp, client.options.keySprint)
		}
		if (preserve.strafe && strafePlan != null) {
			when (strafePlan?.direction) {
				AutoCStrafeDirection.W -> inputController.press(client.options.keyUp)
				AutoCStrafeDirection.A -> inputController.press(client.options.keyLeft)
				AutoCStrafeDirection.S -> inputController.press(client.options.keyDown)
				AutoCStrafeDirection.D -> inputController.press(client.options.keyRight)
				null -> {}
			}
		}
		if (preserve.use && useKeyTicks > 0) {
			inputController.press(client.options.keyUse)
			if (useKeySneak) {
				inputController.press(client.options.keyShift)
			}
		}
		if (preserve.jump && autoJumpTicks > 0) {
			inputController.press(client.options.keyJump)
		}
		if (preserve.awaitSecret && awaitSecretCrouchHeld) {
			inputController.press(client.options.keyShift)
		}
	}

	private fun startRecordRecording(node: RecordNode, room: ScannedDungeonRoom?) {
		val durationMs = (node.recordSeconds * 1000.0).toLong().coerceAtLeast(1L)
		val now = System.nanoTime()
		recordPlayback = null
		crouchAction = null
		inputController.releaseAll()
		lookController.clear()
		recordRecording = RecordRecording(
			node = node,
			endAtMs = System.currentTimeMillis() + durationMs,
			room = room,
			tickStartedAtNanos = now
		)
		modMessage("Recording actions for ${formatSeconds(node.recordSeconds)}s.")
	}

	private fun recordSnapshot(client: Minecraft, player: LocalPlayer) {
		val recording = recordRecording ?: return
		val options = client.options
		val room = recording.room
		val events = recording.pendingEvents.toMutableList()
		recording.pendingEvents.clear()
		recordLookSample(player, 1.0f)
		val lookSamples = recording.pendingLookSamples.toMutableList()
		recording.pendingLookSamples.clear()
		recording.tickStartedAtNanos = System.nanoTime()
		recording.node.addFrame(
			RecordFrame(
				yaw = room?.toRelativeYaw(player.yRot) ?: player.yRot,
				pitch = player.xRot,
				slot = player.inventory.selectedSlot.coerceIn(0, 8),
				forward = options.keyUp.isDown,
				back = options.keyDown.isDown,
				left = options.keyLeft.isDown,
				right = options.keyRight.isDown,
				jump = options.keyJump.isDown,
				sneak = options.keyShift.isDown,
				sprint = options.keySprint.isDown,
				attack = options.keyAttack.isDown,
				lookSamples = lookSamples,
				events = events
			)
		)
	}

	private fun recordLookSample(player: LocalPlayer, forcedOffset: Float? = null) {
		val recording = recordRecording ?: return
		val room = recording.room
		val offset = forcedOffset ?: ((System.nanoTime() - recording.tickStartedAtNanos).toDouble() / RECORD_TICK_NANOS)
			.toFloat()
			.coerceIn(0.0f, 1.0f)
		val sample = RecordLookSample(
			offset = offset,
			yaw = room?.toRelativeYaw(player.yRot) ?: player.yRot,
			pitch = player.xRot
		)
		recording.pendingLookSamples.addCameraSample(sample)
	}

	private fun MutableList<RecordLookSample>.addCameraSample(sample: RecordLookSample) {
		val last = lastOrNull()
		if (last != null
			&& abs(Mth.wrapDegrees(sample.yaw - last.yaw)) < RECORD_LOOK_EPSILON
			&& abs(sample.pitch - last.pitch) < RECORD_LOOK_EPSILON
		) {
			return
		}
		add(sample)
	}

	private fun recordActionPacket(packet: Packet<*>) {
		val recording = recordRecording ?: return
		val event = when (packet) {
			is ServerboundUseItemPacket -> {
				val room = recording.room
				RecordEvent(
					type = RecordEventType.USE_ITEM,
					hand = packet.hand,
					yaw = room?.toRelativeYaw(packet.yRot) ?: packet.yRot,
					pitch = packet.xRot
				)
			}
			is ServerboundUseItemOnPacket -> {
				val hit = packet.hitResult
				val room = recording.room
				RecordEvent(
					type = RecordEventType.USE_BLOCK,
					hand = packet.hand,
					block = transformRecordedBlockPos(Pos(hit.blockPos), room, toWorld = false),
					hit = transformRecordedPos(Pos(hit.location), room, toWorld = false),
					direction = transformRecordedDirection(hit.direction, room, toWorld = false),
					inside = hit.isInside,
					worldBorderHit = hit.isWorldBorderHit
				)
			}
			is ServerboundPlayerActionPacket -> {
				if (packet.action.isDestroyAction()) {
					return
				}
				val room = recording.room
				RecordEvent(
					type = RecordEventType.PLAYER_ACTION,
					action = packet.action.name,
					block = transformRecordedBlockPos(Pos(packet.pos), room, toWorld = false),
					direction = transformRecordedDirection(packet.direction, room, toWorld = false)
				)
			}
			is ServerboundSwingPacket -> RecordEvent(
				type = RecordEventType.SWING,
				hand = packet.hand
			)
			is ServerboundSetCarriedItemPacket -> RecordEvent(
				type = RecordEventType.SET_SLOT,
				slot = packet.slot
			)
			else -> return
		}

		if (!recording.node.addEventToLastFrame(event)) {
			recording.pendingEvents.add(event)
		}
	}

	private fun finishRecordRecordingIfNeeded() {
		val recording = recordRecording ?: return
		if (System.currentTimeMillis() <= recording.endAtMs) {
			return
		}
		recordRecording = null
		if (recording.room != null) {
			saveRoomNodes(recording.room)
			synchronized(nodes) {
				replaceActiveNodes(materializeRoomNodes(recording.room))
			}
		} else {
			saveGlobal()
		}
		modMessage("Record node saved ${recording.node.frameCount()} tick(s).")
	}

	private fun startRecordPlayback(frames: List<RecordFrame>): Boolean {
		val client = Minecraft.getInstance()
		val player = client.player ?: return false
		if (frames.isEmpty()) {
			modMessage("${ChatFormatting.RED}Record node has no recorded actions.")
			return false
		}

		walkPlan = null
		strafePlan = null
		useAction = null
		crouchAction = null
		interactAction = null
		queuedInteractAction = null
		manualBreakPlan = null
		edgeUntilMs = 0L
		autoJumpTicks = 0
		pendingJumpUntilMs = 0L
		lookController.clear()
		inputController.releaseAll()
		recordPlayback = RecordPlayback(
			frames = frames.toList(),
			inputBaseline = inputController.movementInputBaseline(client),
			initialYaw = player.yRot,
			initialPitch = player.xRot,
			lastSlot = player.inventory.selectedSlot
		)
		return true
	}

	private fun updateRecordPlayback(client: Minecraft, player: LocalPlayer): Boolean {
		val playback = recordPlayback ?: return false
		if (playback.finished) {
			finishRecordPlayback(client)
			return false
		}
		if (inputController.movementInputDown(client, playback.inputBaseline)) {
			modMessage("Cancelling record playback.")
			finishRecordPlayback(client)
			return false
		}
		if (playback.index >= playback.frames.size) {
			finishRecordPlayback(client)
			return false
		}

		val frameIndex = playback.index
		val frame = playback.frames[playback.index++]
		playback.activeFrameIndex = frameIndex
		playback.activeFrameStartedAtNanos = System.nanoTime()
		applyRecordFrame(client, player, playback, frameIndex, frame)
		if (playback.index >= playback.frames.size) {
			playback.finished = true
		}
		return true
	}

	private fun finishRecordPlayback(client: Minecraft = Minecraft.getInstance()) {
		recordPlayback = null
		inputController.releaseAll()
	}

	private fun applyRecordFrame(client: Minecraft, player: LocalPlayer, playback: RecordPlayback, frameIndex: Int, frame: RecordFrame) {
		applyRecordCamera(player, playback, frameIndex, 0.0f)
		setRecordSlot(client, player, playback, frame.slot)

		inputController.releaseAll()
		if (frame.forward) inputController.press(client.options.keyUp)
		if (frame.back) inputController.press(client.options.keyDown)
		if (frame.left) inputController.press(client.options.keyLeft)
		if (frame.right) inputController.press(client.options.keyRight)
		if (frame.jump) inputController.press(client.options.keyJump)
		if (frame.sneak) inputController.press(client.options.keyShift)
		if (frame.sprint) inputController.press(client.options.keySprint)
		if (frame.attack) inputController.press(client.options.keyAttack)

		frame.events
			.asSequence()
			.filterNot { frame.attack && it.type == RecordEventType.SWING && it.hand == InteractionHand.MAIN_HAND }
			.forEach { replayRecordEvent(client, player, playback, it) }
	}

	private fun updateRecordCameraPlayback(player: LocalPlayer): Boolean {
		val playback = recordPlayback ?: return false
		val frameIndex = playback.activeFrameIndex
		if (frameIndex !in playback.frames.indices) {
			return true
		}
		val offset = ((System.nanoTime() - playback.activeFrameStartedAtNanos).toDouble() / RECORD_TICK_NANOS)
			.toFloat()
			.coerceIn(0.0f, 1.0f)
		applyRecordCamera(player, playback, frameIndex, offset)
		return true
	}

	private fun applyRecordCamera(player: LocalPlayer, playback: RecordPlayback, frameIndex: Int, offset: Float) {
		val rotation = recordCameraAt(playback, frameIndex, offset)
		player.yRot = rotation.yaw
		player.xRot = rotation.pitch.coerceIn(-90.0f, 90.0f)
		player.yHeadRot = rotation.yaw
		player.yRotO = player.yRot
		player.xRotO = player.xRot
		player.yHeadRotO = player.yHeadRot
	}

	private fun recordCameraAt(playback: RecordPlayback, frameIndex: Int, offset: Float): RecordRotation {
		val frame = playback.frames[frameIndex]
		val samples = frame.lookSamples
		if (samples.isEmpty()) {
			return lerpRecordRotation(previousRecordRotation(playback, frameIndex), RecordRotation(frame.yaw, frame.pitch), offset)
		}

		val clampedOffset = offset.coerceIn(0.0f, 1.0f)
		val first = samples.first()
		if (clampedOffset <= first.offset) {
			val range = first.offset.coerceAtLeast(0.0001f)
			return lerpRecordRotation(previousRecordRotation(playback, frameIndex), first.toRotation(), clampedOffset / range)
		}

		for (i in 0 until samples.lastIndex) {
			val from = samples[i]
			val to = samples[i + 1]
			if (clampedOffset <= to.offset) {
				val range = (to.offset - from.offset).coerceAtLeast(0.0001f)
				return lerpRecordRotation(from.toRotation(), to.toRotation(), (clampedOffset - from.offset) / range)
			}
		}

		val last = samples.last()
		if (last.offset >= 1.0f) {
			return last.toRotation()
		}
		val range = (1.0f - last.offset).coerceAtLeast(0.0001f)
		return lerpRecordRotation(last.toRotation(), RecordRotation(frame.yaw, frame.pitch), (clampedOffset - last.offset) / range)
	}

	private fun previousRecordRotation(playback: RecordPlayback, frameIndex: Int): RecordRotation {
		if (frameIndex <= 0) {
			return RecordRotation(playback.initialYaw, playback.initialPitch)
		}
		val previous = playback.frames[frameIndex - 1]
		return previous.lookSamples.lastOrNull()?.toRotation() ?: RecordRotation(previous.yaw, previous.pitch)
	}

	private fun RecordLookSample.toRotation(): RecordRotation =
		RecordRotation(yaw, pitch)

	private fun lerpRecordRotation(from: RecordRotation, to: RecordRotation, amount: Float): RecordRotation {
		val clamped = amount.coerceIn(0.0f, 1.0f)
		return RecordRotation(
			yaw = from.yaw + Mth.wrapDegrees(to.yaw - from.yaw) * clamped,
			pitch = Mth.lerp(clamped, from.pitch, to.pitch)
		)
	}

	private fun replayRecordEvent(client: Minecraft, player: LocalPlayer, playback: RecordPlayback, event: RecordEvent) {
		val connection = client.connection?.connection ?: return
		when (event.type) {
			RecordEventType.USE_ITEM -> {
				player.yRot = event.yaw
				player.xRot = event.pitch.coerceIn(-90.0f, 90.0f)
				player.yHeadRot = event.yaw
				connection.send(ServerboundUseItemPacket(event.hand, 0, event.yaw, event.pitch))
			}
			RecordEventType.USE_BLOCK -> {
				val block = event.block?.asBlockPos() ?: return
				val hit = event.hit?.asVec3() ?: return
				connection.send(
					ServerboundUseItemOnPacket(
						event.hand,
						BlockHitResult(hit, event.direction, block, event.inside, event.worldBorderHit),
						0
					)
				)
			}
			RecordEventType.PLAYER_ACTION -> {
				val action = runCatching {
					ServerboundPlayerActionPacket.Action.valueOf(event.action)
				}.getOrNull() ?: return
				if (action.isDestroyAction()) {
					return
				}
				val block = event.block?.asBlockPos() ?: return
				connection.send(ServerboundPlayerActionPacket(action, block, event.direction))
			}
			RecordEventType.SWING -> {
				connection.send(ServerboundSwingPacket(event.hand))
			}
			RecordEventType.SET_SLOT -> {
				setRecordSlot(client, player, playback, event.slot)
			}
		}
	}

	private fun setRecordSlot(client: Minecraft, player: LocalPlayer, playback: RecordPlayback, slot: Int) {
		if (slot !in 0..8) {
			return
		}
		if (player.inventory.selectedSlot == slot && playback.lastSlot == slot) {
			return
		}
		player.inventory.selectedSlot = slot
		playback.lastSlot = slot
		client.connection?.connection?.send(ServerboundSetCarriedItemPacket(slot))
	}

	private fun transformRecordedPos(pos: Pos, room: ScannedDungeonRoom?, toWorld: Boolean): Pos =
		when {
			room == null -> pos
			toWorld -> room.toWorld(pos)
			else -> room.toRelative(pos)
		}

	private fun transformRecordedBlockPos(pos: Pos, room: ScannedDungeonRoom?, toWorld: Boolean): Pos =
		when {
			room == null -> pos
			toWorld -> room.toWorldBlock(pos)
			else -> room.toRelativeBlock(pos)
		}

	private fun ServerboundPlayerActionPacket.Action.isDestroyAction(): Boolean =
		this == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK
			|| this == ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK
			|| this == ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK

	private fun transformRecordedDirection(direction: Direction, room: ScannedDungeonRoom?, toWorld: Boolean): Direction {
		if (room == null || direction.axis == Direction.Axis.Y) {
			return direction
		}
		val steps = ((room.rotation.yawOffset / 90.0f).toInt()).floorMod(4)
		return rotateHorizontalDirection(direction, if (toWorld) steps else -steps)
	}

	private fun rotateHorizontalDirection(direction: Direction, steps: Int): Direction {
		var result = direction
		repeat(steps.floorMod(4)) {
			result = when (result) {
				Direction.NORTH -> Direction.EAST
				Direction.EAST -> Direction.SOUTH
				Direction.SOUTH -> Direction.WEST
				Direction.WEST -> Direction.NORTH
				else -> result
			}
		}
		return result
	}

	private fun Int.floorMod(divisor: Int): Int =
		Math.floorMod(this, divisor)

	private fun startBreakRecording(node: BreakNode, room: ScannedDungeonRoom?) {
		val durationMs = (node.recordSeconds * 1000.0).toLong().coerceAtLeast(1L)
		breakRecording = BreakRecording(node, System.currentTimeMillis() + durationMs, room)
		modMessage("Recording broken blocks for ${formatSeconds(node.recordSeconds)}s.")
	}

	private fun recordBreakBlock(pos: BlockPos) {
		val recording = breakRecording ?: return
		if (System.currentTimeMillis() > recording.endAtMs) {
			finishBreakRecordingIfNeeded()
			return
		}
		val storedPos = recording.room?.toRelativeBlock(Pos(pos))?.asBlockPos() ?: pos
		if (recording.node.addBlock(storedPos)) {
			modMessage("Recorded block ${Pos(pos).toChatString()}.")
		}
	}

	private fun finishBreakRecordingIfNeeded() {
		val recording = breakRecording ?: return
		if (System.currentTimeMillis() <= recording.endAtMs) {
			return
		}
		breakRecording = null
		if (recording.room != null) {
			saveRoomNodes(recording.room)
			synchronized(nodes) {
				replaceActiveNodes(materializeRoomNodes(recording.room))
			}
		} else {
			saveGlobal()
		}
		modMessage("Break node recorded ${recording.node.blockCount()} block(s).")
	}

	private fun breakConfiguredBlocks(blocks: List<Pos>, zeroTick: Boolean): Boolean {
		val client = Minecraft.getInstance()
		val player = client.player ?: return false
		val level = client.level ?: return false
		val gameMode = client.gameMode ?: return false
		if (blocks.isEmpty()) {
			modMessage("${ChatFormatting.RED}Break node has no recorded blocks.")
			return false
		}
		if (!ItemInteractionUtils.selectHotbarItem("DUNGEONBREAKER")) {
			modMessage("${ChatFormatting.RED}Missing Dungeonbreaker in hotbar.")
			return false
		}
		if (!zeroTick) {
			manualBreakPlan = ManualBreakPlan(blocks.toMutableList())
			useAction = null
			interactAction = null
			edgeUntilMs = 0L
			inputController.release(client.options.keyAttack)
			return true
		}

		var broke = false
		for (pos in blocks) {
			val bp = pos.asBlockPos()
			val state = level.getBlockState(bp)
			if (state.getShape(level, bp).isEmpty || !DungeonBreaker.canInstantMine(state)) {
				continue
			}
			if (faceDistance(pos.asVec3(), player.eyePosition) > BREAK_RANGE_SQ) {
				continue
			}

			gameMode.startDestroyBlock(bp, closestFace(pos.asVec3(), player.eyePosition))
			player.swing(InteractionHand.MAIN_HAND)
			broke = true
		}
		return broke
	}

	private fun updateManualBreak(client: Minecraft, player: LocalPlayer) {
		val plan = manualBreakPlan ?: return
		val level = client.level ?: return
		if (!ItemInteractionUtils.selectHotbarItem("DUNGEONBREAKER")) {
			modMessage("${ChatFormatting.RED}Missing Dungeonbreaker in hotbar.")
			clearManualBreak(client)
			return
		}

		removeBrokenManualBreakBlocks(level, plan)
		if (plan.blocks.isEmpty()) {
			clearManualBreak(client)
			return
		}

		val previousTarget = plan.current?.asBlockPos()
		val target = selectManualBreakTarget(level, player, plan)
		if (target == null) {
			val distanceSq = nearestManualBreakDistance(level, player, plan)
			if (distanceSq == null) {
				waitManualBreakOrTimeout(client, plan)
			} else {
				waitManualBreakRangeOrTimeout(client, plan, distanceSq)
			}
			return
		}
		if (previousTarget != target.pos.asBlockPos()) {
			manualBreakProgress(plan)
		}
		plan.current = target.pos
		manualBreakTargetReachable(plan, target.distanceSq)

		if (lookController.hasPlan()) {
			inputController.release(client.options.keyAttack)
			return
		}

		val current = target.pos
		val currentBp = current.asBlockPos()
		val currentState = level.getBlockState(currentBp)
		if (currentState.getShape(level, currentBp).isEmpty) {
			inputController.release(client.options.keyAttack)
			removeManualBreakBlock(plan, currentBp)
			return
		}

		val aimTarget = manualBreakAimPoint(level, player, current)
			?: faceVec(closestFace(current.asVec3(), player.eyePosition), current.asVec3())
		val rotation = AutoCNodeUtils.rotationTo(player.eyePosition, aimTarget)
		val targetYaw = manualBreakTargetYaw(rotation.yaw)
		if (!isManualBreakReady(player, currentBp)) {
			inputController.release(client.options.keyAttack)
			if (waitManualBreakOrTimeout(client, plan)) {
				return
			}
			startManualBreakLook(targetYaw, rotation.pitch)
			return
		}
		if (manualBreakTimedOut(client, plan)) {
			return
		}
		inputController.press(client.options.keyAttack)
	}

	private fun clearManualBreak(client: Minecraft = Minecraft.getInstance()) {
		manualBreakPlan = null
		inputController.release(client.options.keyAttack)
	}

	private fun guardManualBreakAttack(client: Minecraft) {
		val plan = manualBreakPlan ?: return
		val player = client.player ?: return
		val level = client.level ?: return
		val current = plan.current ?: run {
			inputController.release(client.options.keyAttack)
			return
		}
		if (lookController.hasPlan()) {
			inputController.release(client.options.keyAttack)
			return
		}

		val bp = current.asBlockPos()
		val state = level.getBlockState(bp)
		if (state.getShape(level, bp).isEmpty
			|| !DungeonBreaker.canInstantMine(state)
			|| faceDistance(current.asVec3(), player.eyePosition) > BREAK_RANGE_SQ
			|| !isManualBreakReady(player, bp)
		) {
			inputController.release(client.options.keyAttack)
		}
	}

	private fun removeBrokenManualBreakBlocks(level: Level, plan: ManualBreakPlan) {
		val currentBlock = plan.current?.asBlockPos()
		var removed = false
		var removedCurrent = false
		val iterator = plan.blocks.iterator()
		while (iterator.hasNext()) {
			val pos = iterator.next()
			val bp = pos.asBlockPos()
			if (level.getBlockState(bp).getShape(level, bp).isEmpty) {
				iterator.remove()
				removed = true
				if (bp == currentBlock) {
					removedCurrent = true
				}
			}
		}
		if (removedCurrent) {
			plan.current = null
		}
		if (removed) {
			manualBreakProgress(plan)
		}
	}

	private fun removeManualBreakBlock(plan: ManualBreakPlan, block: BlockPos) {
		if (plan.blocks.removeAll { it.asBlockPos() == block }) {
			plan.current = null
			manualBreakProgress(plan)
		}
	}

	private fun selectManualBreakTarget(level: Level, player: LocalPlayer, plan: ManualBreakPlan): ManualBreakTarget? {
		manualBreakCurrentHitTarget(level, player, plan)?.let { return it }
		manualBreakCandidate(level, player, plan.current)
			?.takeIf { it.distanceSq <= BREAK_RANGE_SQ }
			?.let { return it }

		plan.current = null
		return plan.blocks
			.asSequence()
			.mapNotNull { manualBreakCandidate(level, player, it) }
			.filter { it.distanceSq <= BREAK_RANGE_SQ }
			.minWithOrNull(
				compareBy<ManualBreakTarget> { if (isManualBreakReady(player, it.pos.asBlockPos())) 0 else 1 }
					.thenBy { manualBreakAimDelta(level, player, it.pos) }
					.thenBy { it.distanceSq }
			)
	}

	private fun manualBreakCurrentHitTarget(level: Level, player: LocalPlayer, plan: ManualBreakPlan): ManualBreakTarget? {
		val hit = currentBlockHit(player) ?: return null
		val pos = plan.blocks.firstOrNull { it.asBlockPos() == hit.blockPos } ?: return null
		return manualBreakCandidate(level, player, pos)?.takeIf { it.distanceSq <= BREAK_RANGE_SQ }
	}

	private fun manualBreakCandidate(level: Level, player: LocalPlayer, pos: Pos?): ManualBreakTarget? {
		if (pos == null) {
			return null
		}
		val bp = pos.asBlockPos()
		val state = level.getBlockState(bp)
		if (state.getShape(level, bp).isEmpty || !DungeonBreaker.canInstantMine(state)) {
			return null
		}
		return ManualBreakTarget(pos, faceDistance(pos.asVec3(), player.eyePosition))
	}

	private fun nearestManualBreakDistance(level: Level, player: LocalPlayer, plan: ManualBreakPlan): Double? =
		plan.blocks
			.asSequence()
			.mapNotNull { manualBreakCandidate(level, player, it) }
			.minOfOrNull { it.distanceSq }

	private fun manualBreakTargetYaw(defaultYaw: Float): Float =
		walkPlan?.yaw ?: defaultYaw

	private fun manualBreakAimDelta(level: Level, player: LocalPlayer, pos: Pos): Double {
		val target = manualBreakAimPoint(level, player, pos)
			?: faceVec(closestFace(pos.asVec3(), player.eyePosition), pos.asVec3())
		val rotation = AutoCNodeUtils.rotationTo(player.eyePosition, target)
		val yawDiff = abs(Mth.wrapDegrees(rotation.yaw - manualBreakTargetYaw(rotation.yaw))).toDouble()
		val pitchDiff = abs(player.xRot - rotation.pitch).toDouble()
		return yawDiff * MANUAL_BREAK_YAW_SELECTION_WEIGHT + pitchDiff
	}

	private fun manualBreakAimPoint(level: Level, player: LocalPlayer, pos: Pos): Vec3? {
		val block = pos.asBlockPos()
		val state = level.getBlockState(block)
		val shape = state.getShape(level, block)
		val box = if (!shape.isEmpty) shape.bounds().move(block) else AABB(block)
		val eye = player.eyePosition
		return manualBreakAimCandidates(box)
			.asSequence()
			.filter { eye.distanceToSqr(it) <= BREAK_RANGE_SQ }
			.filter { canRaycastManualBreakPoint(level, player, eye, it, block) }
			.minByOrNull { manualBreakAimPointScore(player, it) }
	}

	private fun manualBreakAimCandidates(box: AABB): List<Vec3> {
		val xs = manualBreakAxisSamples(box.minX, box.maxX)
		val ys = manualBreakAxisSamples(box.minY, box.maxY)
		val zs = manualBreakAxisSamples(box.minZ, box.maxZ)
		val candidates = arrayListOf<Vec3>()
		candidates.add(Vec3((box.minX + box.maxX) * 0.5, (box.minY + box.maxY) * 0.5, (box.minZ + box.maxZ) * 0.5))
		for (xi in xs.indices) {
			for (yi in ys.indices) {
				for (zi in zs.indices) {
					if (xi == 1 && yi == 1 && zi == 1) {
						continue
					}
					candidates.add(Vec3(xs[xi], ys[yi], zs[zi]))
				}
			}
		}
		return candidates.distinct()
	}

	private fun manualBreakAxisSamples(min: Double, max: Double): List<Double> {
		val size = max - min
		if (size <= MANUAL_BREAK_AIM_INSET * 2.0) {
			val center = (min + max) * 0.5
			return listOf(center, center, center)
		}
		val inset = (size * MANUAL_BREAK_AIM_INSET_FRACTION)
			.coerceIn(MANUAL_BREAK_AIM_INSET, size * 0.33)
		return listOf(min + inset, (min + max) * 0.5, max - inset)
	}

	private fun canRaycastManualBreakPoint(level: Level, player: LocalPlayer, eye: Vec3, point: Vec3, block: BlockPos): Boolean {
		val hit = level.clip(ClipContext(eye, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
		return hit.type == HitResult.Type.BLOCK && hit.blockPos == block
	}

	private fun manualBreakAimPointScore(player: LocalPlayer, point: Vec3): Double {
		val rotation = AutoCNodeUtils.rotationTo(player.eyePosition, point)
		val yawAnchor = walkPlan?.yaw ?: player.yRot
		val yawDiff = abs(Mth.wrapDegrees(rotation.yaw - yawAnchor)).toDouble()
		val pitchDiff = abs(player.xRot - rotation.pitch).toDouble()
		return yawDiff * MANUAL_BREAK_YAW_SELECTION_WEIGHT + pitchDiff + player.eyePosition.distanceToSqr(point) * 0.01
	}

	private fun manualBreakProgress(plan: ManualBreakPlan) {
		plan.blockedSinceMs = null
		plan.closestDistanceSq = Double.POSITIVE_INFINITY
		plan.reachedBreakRange = false
	}

	private fun manualBreakTargetReachable(plan: ManualBreakPlan, distanceSq: Double) {
		val firstReach = !plan.reachedBreakRange
		val improved = distanceSq + MANUAL_BREAK_DISTANCE_PROGRESS_EPSILON < plan.closestDistanceSq
		plan.reachedBreakRange = true
		if (improved) {
			plan.closestDistanceSq = distanceSq
		}
		if (firstReach || improved) {
			plan.blockedSinceMs = null
		}
	}

	private fun waitManualBreakRangeOrTimeout(client: Minecraft, plan: ManualBreakPlan, distanceSq: Double): Boolean {
		inputController.release(client.options.keyAttack)
		if (manualBreakStillApproaching(plan, distanceSq)) {
			return false
		}
		return manualBreakTimedOut(client, plan)
	}

	private fun manualBreakStillApproaching(plan: ManualBreakPlan, distanceSq: Double): Boolean {
		if (plan.reachedBreakRange) {
			return false
		}
		if (distanceSq + MANUAL_BREAK_DISTANCE_PROGRESS_EPSILON < plan.closestDistanceSq) {
			plan.closestDistanceSq = distanceSq
			plan.blockedSinceMs = null
			return true
		}
		return false
	}

	private fun waitManualBreakOrTimeout(client: Minecraft, plan: ManualBreakPlan): Boolean {
		inputController.release(client.options.keyAttack)
		return manualBreakTimedOut(client, plan)
	}

	private fun manualBreakTimedOut(client: Minecraft, plan: ManualBreakPlan): Boolean {
		val now = System.currentTimeMillis()
		val blockedSince = plan.blockedSinceMs ?: now.also { plan.blockedSinceMs = it }
		if (now - blockedSince >= MANUAL_BREAK_BLOCKED_TIMEOUT_MS) {
			clearManualBreak(client)
			return true
		}
		return false
	}

	private fun isManualBreakReady(player: LocalPlayer, block: BlockPos): Boolean {
		return isLookingAtBlock(player, block)
	}

	private fun isLookingAtBlock(player: LocalPlayer, block: BlockPos): Boolean {
		return currentBlockHit(player)?.blockPos == block
	}

	private fun currentBlockHit(player: LocalPlayer): BlockHitResult? {
		val hit = player.pick(5.0, 1.0f, false)
		return if (hit is BlockHitResult && hit.type == HitResult.Type.BLOCK) hit else null
	}

	private fun isAimingAtBlock(player: LocalPlayer, block: BlockPos): Boolean {
		if (isLookingAtBlock(player, block)) {
			return true
		}

		val eye = player.eyePosition
		val end = eye.add(player.lookAngle.scale(AIM_BLOCK_RANGE))
		return AABB(block).inflate(AIM_BLOCK_TOLERANCE).clip(eye, end).isPresent
	}

	private fun faceDistance(pos: Vec3, player: Vec3): Double =
		Direction.entries.minOf { direction -> player.distanceToSqr(faceVec(direction, pos)) }

	private fun closestFace(pos: Vec3, player: Vec3): Direction =
		Direction.entries.minByOrNull { direction -> player.distanceToSqr(faceVec(direction, pos)) } ?: Direction.UP

	private fun faceVec(direction: Direction, pos: Vec3): Vec3 {
		val offset = when (direction) {
			Direction.DOWN -> Vec3(0.5, 0.0, 0.5)
			Direction.UP -> Vec3(0.5, 1.0, 0.5)
			Direction.NORTH -> Vec3(0.5, 0.5, 0.0)
			Direction.SOUTH -> Vec3(0.5, 0.5, 1.0)
			Direction.WEST -> Vec3(0.0, 0.5, 0.5)
			Direction.EAST -> Vec3(1.0, 0.5, 0.5)
		}
		return pos.add(offset)
	}

	private fun formatSeconds(value: Double): String =
		if (value % 1.0 == 0.0) value.toInt().toString() else "%.2f".format(value)

	private fun reload() {
		synchronized(nodes) {
			globalNodes.clear()
			globalNodes.addAll(data.value)
			globalNodes.forEach { it.calculate() }
			activeScopeSignature = null
		}
		refreshActiveNodes(resetRuntimeOnChange = false)
		clearRuntime()
	}

	private fun saveGlobal() {
		data.value = synchronized(nodes) { globalNodes.toMutableList() }
		data.save()
	}

	private fun clearRuntime() {
		inNode = null
		lastType = null
		previousPlayerPos = null
		walkPlan = null
		strafePlan = null
		useAction = null
		bonzoAction = null
		crouchAction = null
		useKeyTicks = 0
		useKeySneak = false
		etherwarpShiftHoldTicks = 0
		interactAction = null
		queuedInteractAction = null
		manualBreakPlan = null
		edgeUntilMs = 0L
		routeWaitUntilMs = 0L
		routeWaitUntilTick = 0
		routeActive = false
		awaitSecretNode = null
		awaitSecretPendingNode = null
		stackedNodeQueue.clear()
		awaitSecretRemaining = 0
		awaitSecretBaseline = null
		awaitSecretMax = null
		awaitSecretCompleteOnFirstCounter = false
		awaitSecretCrouchHeld = false
		currentSecretCount = null
		currentSecretMax = null
		autoJumpTicks = 0
		pendingJumpUntilMs = 0L
		breakRecording = null
		recordRecording = null
		recordPlayback = null
		terminalExitWindowUntilMs = 0L
		terminalExitActivatedNodeIds.clear()
		if (!TerminalContext.inTerminal) {
			terminalExitListenerArmed = false
		}
		clearRouteActivationState()
		nodeActivationTimes.clear()
		lookController.clear()
		inputController.releaseAll()
		leapMenu.clear()
		synchronized(nodes) {
			nodes.forEach { it.reset() }
		}
	}

	private fun modMessage(message: String) {
		ChatUtils.chat("${ChatFormatting.AQUA}AC » ${ChatFormatting.RESET}$message")
	}

	data class EditSession(
		val module: AutoC,
		val scopeLabel: String,
		val sourceNodes: MutableList<AutoCNode>,
		val room: ScannedDungeonRoom?,
		val playerWorldPos: Pos,
		val entries: MutableList<EditEntry>
	)

	data class EditEntry(
		val node: AutoCNode,
		var index: Int,
		var worldPos: Pos
	)

	private data class WalkPlan(
		val yaw: Float,
		val pitch: Float,
		val inputBaseline: AutoCInputController.MovementInputBaseline,
		var looking: Boolean,
		var nodeLookActive: Boolean = false
	)

	private data class StrafePlan(
		val direction: AutoCStrafeDirection,
		val inputBaseline: AutoCInputController.MovementInputBaseline
	)

	private data class UseAction(
		val yaw: Float,
		val pitch: Float,
		val sneak: Boolean,
		val skyBlockIds: List<String> = emptyList(),
		val skyBlockId: String = "",
		val itemId: String = "",
		val displayName: String = "",
		val targetBlock: BlockPos? = null,
		var useStoredRotation: Boolean = true,
		val etherwarp: Boolean = false,
		var prepared: Boolean = false
	) {
		fun itemLabel(): String =
			if (skyBlockIds.isNotEmpty()) skyBlockIds.joinToString(" or ") else skyBlockId.ifBlank { displayName.ifBlank { itemId } }
	}

	private data class BonzoAction(
		val yaw: Float,
		val pitch: Float
	)

	private data class CrouchAction(
		val releaseAtMs: Long,
		var edgeSettledSinceMs: Long = 0L,
		var moveDirX: Double = 0.0,
		var moveDirZ: Double = 0.0
	)

	private data class HorizontalDirection(
		val x: Double,
		val z: Double
	)

	private data class StopPreserve(
		val look: Boolean = false,
		val walk: Boolean = false,
		val strafe: Boolean = false,
		val use: Boolean = false,
		val bonzo: Boolean = false,
		val crouch: Boolean = false,
		val interact: Boolean = false,
		val breakBlocks: Boolean = false,
		val record: Boolean = false,
		val edge: Boolean = false,
		val jump: Boolean = false,
		val awaitSecret: Boolean = false
	) {
		companion object {
			fun from(raw: Set<String>): StopPreserve {
				val names = raw.mapTo(mutableSetOf()) { it.lowercase(Locale.ROOT) }
				val use = names.any { it in USE_NAMES }
				val interact = names.any { it in INTERACT_NAMES }
				return StopPreserve(
					look = "look" in names,
					walk = "walk" in names,
					strafe = "strafe" in names,
					use = use,
					bonzo = "bonzo" in names,
					crouch = "crouch" in names,
					interact = interact,
					breakBlocks = "break" in names,
					record = "record" in names,
					edge = "edge" in names,
					jump = "jump" in names,
					awaitSecret = interact || use || "as" in names || "awaitsecret" in names
				)
			}

			private val USE_NAMES = setOf("use", "warp", "etherwarp")
			private val INTERACT_NAMES = setOf("interact")
		}
	}

	private data class InteractAction(
		val yaw: Float,
		val pitch: Float,
		val block: BlockPos,
		val hit: Vec3,
		val await: Boolean,
		val awaitNode: AutoCNode?,
		val inputBaseline: AutoCInputController.MovementInputBaseline,
		val retryAtMs: Long = 0L
	)

	private data class SecretCounter(
		val current: Int,
		val max: Int
	)

	private enum class InteractAttempt {
		SUCCESS,
		RETRY,
		ABORT
	}

	private data class ManualBreakPlan(
		val blocks: MutableList<Pos>,
		var current: Pos? = null,
		var blockedSinceMs: Long? = null,
		var closestDistanceSq: Double = Double.POSITIVE_INFINITY,
		var reachedBreakRange: Boolean = false
	)

	private data class ManualBreakTarget(
		val pos: Pos,
		val distanceSq: Double
	)

	private data class BreakRecording(
		val node: BreakNode,
		val endAtMs: Long,
		val room: ScannedDungeonRoom?
	)

	private data class RecordRecording(
		val node: RecordNode,
		val endAtMs: Long,
		val room: ScannedDungeonRoom?,
		var tickStartedAtNanos: Long,
		val pendingEvents: MutableList<RecordEvent> = mutableListOf(),
		val pendingLookSamples: MutableList<RecordLookSample> = mutableListOf()
	)

	private data class RecordPlayback(
		val frames: List<RecordFrame>,
		val inputBaseline: AutoCInputController.MovementInputBaseline,
		val initialYaw: Float,
		val initialPitch: Float,
		var index: Int = 0,
		var activeFrameIndex: Int = -1,
		var activeFrameStartedAtNanos: Long = 0L,
		var lastSlot: Int = 0,
		var finished: Boolean = false
	)

	private data class RecordRotation(
		val yaw: Float,
		val pitch: Float
	)

	private data class PlacementArgs(
		val args: String = "",
		val heightOffset: Double = 0.0,
		val radius: Float? = null,
		val waitSeconds: Double = 0.0,
		val awaitSecret: Boolean = false,
		val notStart: Boolean = false,
		val maxActivationsPerMinute: Int = 0
	) {
		fun with(modifier: PlacementModifier): PlacementArgs =
			copy(
				heightOffset = modifier.heightOffset ?: heightOffset,
				radius = modifier.radius ?: radius,
				waitSeconds = modifier.waitSeconds ?: waitSeconds,
				awaitSecret = modifier.awaitSecret || awaitSecret,
				notStart = modifier.notStart || notStart,
				maxActivationsPerMinute = modifier.maxActivationsPerMinute ?: maxActivationsPerMinute
			)
	}

	private data class PlacementModifier(
		val heightOffset: Double? = null,
		val radius: Float? = null,
		val waitSeconds: Double? = null,
		val awaitSecret: Boolean = false,
		val notStart: Boolean = false,
		val maxActivationsPerMinute: Int? = null
	)

	private sealed class RouteScope(val signature: String) {
		object Global : RouteScope("global")
		data class UnavailableRoom(val reason: String) : RouteScope("room:unavailable")
		data class Room(val room: ScannedDungeonRoom) : RouteScope("room:${room.signature}")
	}

	private companion object {
		private const val NODE_DEPTH = true
		private const val EDIT_RADIUS_SQ = 400.0
		private const val NODE_ACTIVATION_WINDOW_MS = 60_000L
		private const val TERMINAL_EXIT_BUFFER_MS = 1000L
		private const val MENU_TIMEOUT_MS = 1500L
		private const val EDGE_ACTIVE_MS = 5000L
		private const val JUMP_GROUND_WAIT_MS = 750L
		private const val CROUCH_EDGE_SETTLE_MS = 120L
		private const val CROUCH_EDGE_SETTLE_SPEED_SQ = 0.0009
		private const val CROUCH_DIRECTION_SPEED_SQ = 0.0004
		private const val CROUCH_EDGE_SUPPORT_GRID = 8
		private const val CROUCH_EDGE_SUPPORT_INSET = 0.02
		private const val CROUCH_EDGE_REAR_SUPPORT_MARGIN = 0.055
		private const val CROUCH_EDGE_PROBE_RADIUS = 0.035
		private const val CROUCH_EDGE_PROBE_DEPTH = 0.08
		private const val CROUCH_EDGE_PROBE_HEIGHT = 0.02
		private const val EDGE_CHECK_Y_OFFSET = -0.5
		private const val EDGE_DISTANCE = 0.001
		private const val BREAK_RANGE_SQ = 25.0
		private const val MANUAL_BREAK_BLOCKED_TIMEOUT_MS = 750L
		private const val MANUAL_BREAK_DISTANCE_PROGRESS_EPSILON = 0.0025
		private const val MANUAL_BREAK_YAW_SELECTION_WEIGHT = 2.0
		private const val MANUAL_BREAK_AIM_INSET = 0.015
		private const val MANUAL_BREAK_AIM_INSET_FRACTION = 0.08
		private const val USE_KEY_HOLD_TICKS = 2
		private const val BONZO_USE_KEY_HOLD_TICKS = 2
		private const val ETHERWARP_USE_KEY_HOLD_TICKS = 2
		private const val BONZO_STAFF_ID = "BONZO_STAFF"
		private const val ETHERWARP_SHIFT_GRACE_TICKS = 4
		private const val INTERACT_SECRET_RETRY_MS = 180L
		private const val WALK_MOUSE_CANCEL_DEGREES = 0.35f
		private const val INTERACT_MOUSE_CANCEL_DEGREES = 0.35f
		private const val AIM_BLOCK_TOLERANCE = 0.08
		private const val AIM_BLOCK_RANGE = 5.0
		private const val RECORD_TICK_NANOS = 50_000_000.0
		private const val RECORD_LOOK_EPSILON = 0.001f
		private const val SECRET_ITEM_REMOVE_RANGE_SQ = 64.0
		private val HEIGHT_OFFSET_ARG = Regex("(?i)^h(\\d+(?:\\.\\d+)?)$")
		private val RADIUS_ARG = Regex("(?i)^r\\((\\d+(?:\\.\\d+)?)\\)$")
		private val WAIT_ARG = Regex("(?i)^wait\\((\\d+(?:\\.\\d+)?)\\)$")
		private val MAX_ACTIVATIONS_ARG = Regex("(?i)^maxA\\((\\d+)\\)$")
		private val SECRET_COUNTER_PATTERN = Regex("([\\d,]+)/([\\d,]+) Secrets")
		private val SECRET_NAMES = setOf(
			"Health Potion VIII Splash Potion",
			"Healing Potion 8 Splash Potion",
			"Healing Potion VIII Splash Potion",
			"Healing VIII Splash Potion",
			"Healing 8 Splash Potion",
			"Decoy",
			"Inflatable Jerry",
			"Spirit Leap",
			"Trap",
			"Training Weights",
			"Defuse Kit",
			"Dungeon Chest Key",
			"Treasure Talisman",
			"Revive Stone",
			"Architect's First Draft",
			"Secret Dye",
			"Candycomb"
		)
	}
}
