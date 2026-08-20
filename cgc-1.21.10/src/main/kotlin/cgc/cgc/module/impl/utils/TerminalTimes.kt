package cgc.cgc.module.impl.utils

import cgc.cgc.module.CgcModule
import cgc.cgc.module.ClientTickStartModule
import cgc.cgc.module.ModuleCategory
import cgc.cgc.module.WorldLoadModule
import cgc.cgc.utils.ChatUtils
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.HashedStack
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket
import net.minecraft.network.protocol.game.ClientboundBundlePacket
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket
import net.minecraft.world.item.ItemStack
import net.minecraft.world.inventory.ContainerInput
import java.util.IdentityHashMap
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import java.util.regex.Pattern

/**
 * A deliberately verbose terminal timing audit.
 *
 * The remote server does not acknowledge container clicks or include its clock/tick in container
 * responses. Consequently this module keeps the exact local timeline separate from the exact
 * server-bound payload and the authoritative server responses observed by the client.
 */
class TerminalTimes : CgcModule(
	id = "TerminalTimes",
	displayName = "Terminal Times",
	category = ModuleCategory.UTILS,
	description = "Prints detailed client timing and server-protocol evidence for every terminal.",
	defaultEnabled = false
), ClientTickStartModule, WorldLoadModule {
	private val stateLock = Any()
	private val clientTick = AtomicLong()
	private val inputStack = ThreadLocal.withInitial { ArrayDeque<InputToken>() }
	private val packetOpens = IdentityHashMap<Packet<*>, OpenLink>()
	private val packetScreenReplacements = IdentityHashMap<Packet<*>, TerminalSession?>()
	private val packetClicks = IdentityHashMap<Packet<*>, ClickTrace>()
	private val packetResponses = IdentityHashMap<Packet<*>, ResponseLink>()
	private val pendingSessions = arrayListOf<TerminalSession>()
	private val readyReports = ArrayDeque<TerminalReport>()

	@Volatile
	private var observing = false
	@Volatile
	private var worldTransitioning = false
	@Volatile
	private var worldEpoch = 0L
	private var activeSession: TerminalSession? = null
	private var visibleSession: TerminalSession? = null
	private var visitExitCandidateNs: Long? = null

	init {
		instance = this
	}

	override fun onEnable() {
		synchronized(stateLock) {
			observing = true
		}
	}

	override fun onDisable() {
		synchronized(stateLock) {
			observing = false
			clearStateLocked(resetTick = false)
		}
		inputStack.remove()
	}

	override fun onClientTickStart(client: Minecraft) {
		clientTick.incrementAndGet()
		if (!observing) {
			return
		}

		val now = System.nanoTime()
		val reports = synchronized(stateLock) {
			val current = activeSession
			if (current != null) {
				observeScreenFallback(current, client, now)
				if (current.reportAfterNs != null && now >= current.reportAfterNs!!) {
					finishSessionLocked(current)
				}
			}
			pendingSessions.toList().forEach { pending ->
				if (pending.reportAfterNs != null && now >= pending.reportAfterNs!!) {
					finishSessionLocked(pending)
				}
			}

			val visitStillOpen = activeSession != null
				|| pendingSessions.isNotEmpty()
				|| isTerminalScreenVisible(client)
			if (client.player == null || visitStillOpen || readyReports.isEmpty()) {
				visitExitCandidateNs = null
				emptyList()
			} else {
				val exitCandidate = visitExitCandidateNs
				if (exitCandidate == null) {
					visitExitCandidateNs = now
					emptyList()
				} else if (now - exitCandidate < VISIT_EXIT_GRACE_NS) {
					emptyList()
				} else {
					visitExitCandidateNs = null
					buildList {
						while (readyReports.isNotEmpty()) {
							add(readyReports.removeFirst())
						}
					}
				}
			}
		}

		if (reports.isNotEmpty()) {
			printReport(reports)
		}
	}

	override fun onWorldLoad() {
		worldTransitioning = true
		val now = System.nanoTime()
		try {
			synchronized(stateLock) {
				val unfinished = buildList {
					activeSession?.let(::add)
					addAll(pendingSessions)
				}.distinctBy { it.id }
				unfinished.forEach { current ->
					current.end = current.end ?: EndTrace(
						EndKind.WORLD_CHANGE,
						now,
						clientTick.get(),
						"the client changed connection/world before another terminal boundary was observed"
					)
					readyReports.add(current.snapshot())
				}
				activeSession = null
				visibleSession = null
				pendingSessions.clear()
				packetOpens.clear()
				packetScreenReplacements.clear()
				packetClicks.clear()
				packetResponses.clear()
				visitExitCandidateNs = null
				clientTick.set(0L)
				worldEpoch++
			}
			inputStack.remove()
		} finally {
			worldTransitioning = false
		}
	}

	protected override fun reset() {
		// onDisable performs the synchronized invalidation before CgcModule calls reset().
	}

	private fun clearState(resetTick: Boolean) {
		synchronized(stateLock) {
			clearStateLocked(resetTick)
		}
		inputStack.remove()
	}

	private fun clearStateLocked(resetTick: Boolean) {
		activeSession = null
		visibleSession = null
		pendingSessions.clear()
		packetOpens.clear()
		packetScreenReplacements.clear()
		packetClicks.clear()
		packetResponses.clear()
		readyReports.clear()
		visitExitCandidateNs = null
		if (resetTick) {
			clientTick.set(0L)
		}
		worldEpoch++
	}

	private inline fun withObservation(action: TerminalTimes.(Long) -> Unit) {
		synchronized(stateLock) {
			if (!observing || worldTransitioning) return
			action(worldEpoch)
		}
	}

	private fun isTerminalScreenVisible(client: Minecraft): Boolean {
		val screen = client.screen as? AbstractContainerScreen<*> ?: return false
		val title = ChatFormatting.stripFormatting(screen.title.string) ?: screen.title.string
		return TerminalKind.fromTitle(title) != null
	}

	private fun handleDecodedPacket(packet: Packet<*>, eventEpoch: Long) {
		val deliveryNs = System.nanoTime()
		val tick = clientTick.get()
		handleDecodedPacket(packet, deliveryNs, tick, eventEpoch, null)
	}

	private fun handleDecodedPacket(packet: Packet<*>, deliveryNs: Long, tick: Long, eventEpoch: Long, bundlePosition: String?) {
		when (packet) {
			is ClientboundBundlePacket -> {
				val children = packet.subPackets().toList()
				children.forEachIndexed { index, child ->
					val position = "${index + 1}/${children.size}"
					val path = bundlePosition?.let { "$it > $position" } ?: position
					handleDecodedPacket(child, deliveryNs, tick, eventEpoch, path)
				}
			}
			is ClientboundOpenScreenPacket -> observeOpenDecoded(packet, deliveryNs, tick, eventEpoch, bundlePosition)
			is ClientboundContainerSetSlotPacket -> observeResponseDecoded(
				packet,
				packet.containerId,
				ResponseKind.SET_SLOT,
				deliveryNs,
				tick,
				eventEpoch,
				bundlePosition
			)
			is ClientboundContainerSetContentPacket -> observeResponseDecoded(
				packet,
				packet.containerId(),
				ResponseKind.SET_CONTENT,
				deliveryNs,
				tick,
				eventEpoch,
				bundlePosition
			)
			is ClientboundContainerSetDataPacket -> observeResponseDecoded(
				packet,
				packet.containerId,
				ResponseKind.SET_DATA,
				deliveryNs,
				tick,
				eventEpoch,
				bundlePosition
			)
			is ClientboundSetCursorItemPacket -> observeCursorDecoded(packet, deliveryNs, tick, eventEpoch, bundlePosition)
			is ClientboundContainerClosePacket -> observeCloseDecoded(packet, deliveryNs, tick, eventEpoch, bundlePosition)
			is ClientboundSystemChatPacket -> observeCompletionChatDecoded(packet, deliveryNs, tick, eventEpoch, bundlePosition)
		}
	}

	private fun observeOpenDecoded(packet: ClientboundOpenScreenPacket, now: Long, tick: Long, eventEpoch: Long, bundlePosition: String?) {
		val observerStartedNs = System.nanoTime()
		val title = ChatFormatting.stripFormatting(packet.title.string) ?: packet.title.string
		val terminal = TerminalKind.fromTitle(title)
		val menuType = packet.type.toString()
		val observerCapturedNs = System.nanoTime()
		synchronized(stateLock) {
			if (eventEpoch != worldEpoch) return
			val previous = activeSession
			if (previous != null) {
				previous.end = previous.end ?: EndTrace(
					EndKind.REPLACED_BY_SCREEN,
					now,
					tick,
					"another screen opened before a matching close was observed"
				)
				previous.reportAfterNs = maxOf(previous.reportAfterNs ?: 0L, now + REPORT_GRACE_NS)
				moveToPendingLocked(previous)
			}

			if (terminal == null) {
				packetScreenReplacements[packet] = previous
				return
			}

			activeSession = TerminalSession(
				terminal = terminal,
				containerId = packet.containerId,
				title = title,
				menuType = menuType,
				openPacket = packet,
				openDecodedNs = now,
				openTick = tick,
				openThread = Thread.currentThread().name,
				openBundlePosition = bundlePosition,
				openObserverStartedNs = observerStartedNs,
				openObserverCapturedNs = observerCapturedNs
			).also { packetOpens[packet] = OpenLink(it, previous) }
		}
	}

	private fun observeResponseDecoded(
		packet: Packet<*>,
		containerId: Int,
		kind: ResponseKind,
		now: Long,
		tick: Long,
		eventEpoch: Long,
		bundlePosition: String?
	) {
		val observerStartedNs = System.nanoTime()
		synchronized(stateLock) {
			if (eventEpoch != worldEpoch) return
			val current = findSessionForContainerLocked(containerId) ?: return
			if (containerId != current.containerId) {
				return
			}
			if (current.responses.size >= MAX_RESPONSE_EVENTS) {
				current.droppedResponses++
				return
			}

			val response = ResponseTrace(
				kind = kind,
				payload = snapshotResponsePayload(packet),
				decodedNs = now,
				decodedTick = tick,
				decodedThread = Thread.currentThread().name,
				bundlePosition = bundlePosition,
				observerStartedNs = observerStartedNs,
				observerCapturedNs = System.nanoTime()
			)
			current.responses.add(response)
			packetResponses[packet] = ResponseLink(current, response)
		}
	}

	private fun observeCursorDecoded(packet: ClientboundSetCursorItemPacket, now: Long, tick: Long, eventEpoch: Long, bundlePosition: String?) {
		val observerStartedNs = System.nanoTime()
		synchronized(stateLock) {
			if (eventEpoch != worldEpoch) return
			val current = activeSession ?: pendingSessions.lastOrNull() ?: return
			if (current.responses.size >= MAX_RESPONSE_EVENTS) {
				current.droppedResponses++
				return
			}
			val response = ResponseTrace(
				kind = ResponseKind.SET_CURSOR,
				payload = ResponsePayload.SetCursor(packet.contents().copy()),
				decodedNs = now,
				decodedTick = tick,
				decodedThread = Thread.currentThread().name,
				bundlePosition = bundlePosition,
				observerStartedNs = observerStartedNs,
				observerCapturedNs = System.nanoTime()
			)
			current.responses.add(response)
			packetResponses[packet] = ResponseLink(current, response)
		}
	}

	private fun observeCloseDecoded(packet: ClientboundContainerClosePacket, now: Long, tick: Long, eventEpoch: Long, bundlePosition: String?) {
		val observerStartedNs = System.nanoTime()
		synchronized(stateLock) {
			if (eventEpoch != worldEpoch) return
			val current = findSessionForContainerLocked(packet.containerId) ?: return
			if (packet.containerId != current.containerId) {
				return
			}
			val response = ResponseTrace(
				kind = ResponseKind.CLOSE,
				payload = ResponsePayload.Close(packet.containerId),
				decodedNs = now,
				decodedTick = tick,
				decodedThread = Thread.currentThread().name,
				bundlePosition = bundlePosition,
				observerStartedNs = observerStartedNs,
				observerCapturedNs = System.nanoTime()
			)
			current.responses.add(response)
			packetResponses[packet] = ResponseLink(current, response)
			current.serverCloseDecodedNs = now
			current.end = current.end ?: EndTrace(
				EndKind.SERVER_CLOSE,
				now,
				tick,
				"matching ClientboundContainerClosePacket"
			)
			current.reportAfterNs = maxOf(current.reportAfterNs ?: 0L, now + CLOSE_HANDLER_FALLBACK_NS)
		}
	}

	private fun observeCompletionChatDecoded(packet: ClientboundSystemChatPacket, now: Long, tick: Long, eventEpoch: Long, bundlePosition: String?) {
		val observerStartedNs = System.nanoTime()
		if (packet.overlay()) {
			return
		}
		val text = (ChatFormatting.stripFormatting(packet.content().string) ?: packet.content().string).trim()
		val match = TERMINAL_COMPLETION_PATTERN.matcher(text)
		if (!match.find()) {
			return
		}
		val actor = match.group(1).trim()
		val localName = Minecraft.getInstance().player?.gameProfile?.name
		val isLocal = localName != null && actor.substringAfterLast(' ').equals(localName, ignoreCase = true)
		if (!isLocal) {
			return
		}

		synchronized(stateLock) {
			if (eventEpoch != worldEpoch) return
			val endedCandidate = (pendingSessions.asSequence() + listOfNotNull(activeSession).asSequence())
				.filter { it.end != null && now - it.end!!.timestampNs in 0..COMPLETION_CORRELATION_NS }
				.maxByOrNull { it.end!!.timestampNs }
			val current = visibleSession ?: endedCandidate ?: activeSession ?: return
			if (current.responses.size >= MAX_RESPONSE_EVENTS) {
				current.droppedResponses++
				return
			}
			val response = ResponseTrace(
				kind = ResponseKind.COMPLETION_CHAT,
				payload = ResponsePayload.CompletionChat(text, actor, isLocal),
				decodedNs = now,
				decodedTick = tick,
				decodedThread = Thread.currentThread().name,
				bundlePosition = bundlePosition,
				observerStartedNs = observerStartedNs,
				observerCapturedNs = System.nanoTime()
			)
			current.responses.add(response)
			packetResponses[packet] = ResponseLink(current, response)
			current.localCompletionMessage = text
			current.reportAfterNs = current.reportAfterNs?.let { maxOf(it, now + REPORT_GRACE_NS) }
		}
	}

	private fun handlePacketSendRequest(packet: Packet<*>, eventEpoch: Long) {
		val now = System.nanoTime()
		val tick = clientTick.get()
		when (packet) {
			is ServerboundContainerClickPacket -> observeClickPacket(packet, now, tick, eventEpoch)
			is ServerboundContainerClosePacket -> observeClientClose(packet, now, tick, eventEpoch)
		}
	}

	private fun observeClickPacket(packet: ServerboundContainerClickPacket, now: Long, tick: Long, eventEpoch: Long) {
		synchronized(stateLock) {
			if (eventEpoch != worldEpoch) return
			val current = visibleSession?.takeIf { it.containerId == packet.containerId() }
				?: findSessionForContainerLocked(packet.containerId())
				?: return
			if (packet.containerId() != current.containerId) {
				return
			}

			val stackClick = inputStack.get().lastOrNull()?.click
			val click = stackClick
				?.takeIf {
					it.sessionId == current.id
						&& it.enqueueNs == null
						&& it.slot == packet.slotNum().toInt()
						&& it.button == packet.buttonNum().toInt()
						&& it.input == packet.containerInput()
				}
				?: ClickTrace(
					sessionId = current.id,
					ordinal = current.nextClickOrdinal++,
					containerId = packet.containerId(),
					slot = packet.slotNum().toInt(),
					button = packet.buttonNum().toInt(),
					input = packet.containerInput(),
					intentNs = null,
					intentTick = null,
					intentThread = null
				).also(current.clicks::add)

			click.enqueueNs = now
			click.enqueueTick = tick
			click.enqueueThread = Thread.currentThread().name
			packetClicks[packet] = click
		}
	}

	private fun observeClientClose(packet: ServerboundContainerClosePacket, now: Long, tick: Long, eventEpoch: Long) {
		synchronized(stateLock) {
			if (eventEpoch != worldEpoch) return
			val current = visibleSession?.takeIf { it.containerId == packet.containerId }
				?: findSessionForContainerLocked(packet.containerId)
				?: return
			if (packet.containerId != current.containerId) {
				return
			}
			current.clientCloseRequestNs = now
			current.clientCloseRequestTick = tick
			current.clientCloseRequestThread = Thread.currentThread().name
			current.clientClosePacket = packet
			if (current.end == null) {
				current.end = EndTrace(
					EndKind.CLIENT_CLOSE,
					now,
					tick,
					"matching ServerboundContainerClosePacket request; the packet contains no reason"
				)
				current.reportAfterNs = now + CLIENT_CLOSE_GRACE_NS
			}
		}
	}

	private fun handlePacketSendCancelled(packet: Packet<*>, eventEpoch: Long) {
		val now = System.nanoTime()
		synchronized(stateLock) {
			if (eventEpoch != worldEpoch) return
			val current = (sequenceOf(activeSession) + pendingSessions.asSequence())
				.filterNotNull()
				.firstOrNull { packet === it.clientClosePacket }
			if (current != null && packet === current.clientClosePacket) {
				current.clientCloseCancelledNs = now
				if (current.end?.kind == EndKind.CLIENT_CLOSE) {
					current.end = null
					current.reportAfterNs = null
				}
			}
			packetClicks.remove(packet)?.let {
				it.cancelledNs = now
				it.cancelledThread = Thread.currentThread().name
			}
		}
	}

	private fun handlePacketReceiveCancelled(packet: Packet<*>, eventEpoch: Long) {
		val now = System.nanoTime()
		val tick = clientTick.get()
		handlePacketReceiveCancelled(packet, now, tick, eventEpoch)
	}

	private fun handlePacketReceiveCancelled(packet: Packet<*>, now: Long, tick: Long, eventEpoch: Long) {
		if (packet is ClientboundBundlePacket) {
			packet.subPackets().forEach { handlePacketReceiveCancelled(it, now, tick, eventEpoch) }
			return
		}
		synchronized(stateLock) {
			if (eventEpoch != worldEpoch) return
			if (packetScreenReplacements.containsKey(packet)) {
				restoreDisplacedSessionLocked(packetScreenReplacements.remove(packet))
				return
			}
			val openLink = packetOpens[packet]
			if (openLink != null) {
				val current = openLink.session
				current.openCancelledNs = now
				current.end = EndTrace(
					EndKind.OPEN_CANCELLED,
					now,
					tick,
					"the terminal OpenScreen packet was cancelled locally before vanilla applied it"
				)
				current.reportAfterNs = now + REPORT_GRACE_NS
				restoreDisplacedSessionLocked(openLink.displacedSession)
				return
			}

			packetResponses[packet]?.let { link ->
				link.response.cancelledNs = now
				link.response.cancelledThread = Thread.currentThread().name
				if (link.response.kind == ResponseKind.CLOSE && link.session.end?.kind == EndKind.SERVER_CLOSE) {
					link.session.serverCloseDecodedNs = null
					link.session.end = null
					link.session.reportAfterNs = null
					if (visibleSession == null) {
						visibleSession = link.session
					}
				}
			}
		}
	}

	private fun handlePacketWriteHandoff(packet: Packet<*>, flush: Boolean, eventEpoch: Long) {
		val now = System.nanoTime()
		synchronized(stateLock) {
			if (eventEpoch != worldEpoch) return
			packetClicks[packet]?.let { click ->
				if (packet is ServerboundContainerClickPacket) {
					val snapshotStart = System.nanoTime()
					click.stateId = packet.stateId()
					click.changedSlots = packet.changedSlots().int2ObjectEntrySet()
						.map { ChangedSlotTrace(it.intKey, it.value) }
					click.carriedItem = packet.carriedItem()
					click.payloadObserverStartedNs = snapshotStart
					click.payloadCapturedNs = System.nanoTime()
				}
				click.handoffNs = now
				click.handoffTick = clientTick.get()
				click.handoffThread = Thread.currentThread().name
				click.flush = flush
			}
			findSessionForClosePacketLocked(packet)?.let { session ->
				session.clientCloseHandoffNs = now
				session.clientCloseHandoffTick = clientTick.get()
				session.clientCloseHandoffThread = Thread.currentThread().name
				session.clientCloseFlush = flush
			}
		}
	}

	private fun handlePacketWriteReturned(packet: Packet<*>, eventEpoch: Long) {
		val now = System.nanoTime()
		synchronized(stateLock) {
			if (eventEpoch != worldEpoch) return
			packetClicks.remove(packet)?.let {
				it.writeReturnNs = now
				it.writeReturnTick = clientTick.get()
				it.writeReturnThread = Thread.currentThread().name
			}
			findSessionForClosePacketLocked(packet)?.let { session ->
				session.clientCloseWriteReturnNs = now
				session.clientCloseWriteReturnTick = clientTick.get()
				session.clientCloseWriteReturnThread = Thread.currentThread().name
			}
		}
	}

	private fun observeInputStart(containerId: Int, slot: Int, button: Int, input: ContainerInput, eventEpoch: Long) {
		val now = System.nanoTime()
		val tick = clientTick.get()
		val click = synchronized(stateLock) {
			if (eventEpoch != worldEpoch) return@synchronized null
			val current = visibleSession?.takeIf { it.containerId == containerId }
				?: findSessionForContainerLocked(containerId)
			if (current == null || current.containerId != containerId) {
				null
			} else {
				ClickTrace(
					sessionId = current.id,
					ordinal = current.nextClickOrdinal++,
					containerId = containerId,
					slot = slot,
					button = button,
					input = input,
					intentNs = now,
					intentTick = tick,
					intentThread = Thread.currentThread().name
				).also(current.clicks::add)
			}
		}
		inputStack.get().addLast(InputToken(eventEpoch, click))
	}

	private fun observeInputEnd() {
		val stack = inputStack.get()
		if (stack.isEmpty()) {
			return
		}
		val token = stack.removeLast()
		val click = token.click ?: return
		synchronized(stateLock) {
			if (token.worldEpoch != worldEpoch) return
			click.inputReturnNs = System.nanoTime()
			click.inputReturnTick = clientTick.get()
			click.inputReturnThread = Thread.currentThread().name
		}
	}

	private fun handlePacketHandlerTail(packet: Packet<*>, eventEpoch: Long) {
		val now = System.nanoTime()
		val tick = clientTick.get()
		synchronized(stateLock) {
			if (eventEpoch != worldEpoch) return
			if (packetScreenReplacements.containsKey(packet)) {
				packetScreenReplacements.remove(packet)
				visibleSession = null
				return
			}
			packetOpens.remove(packet)?.let { openLink ->
				val current = openLink.session
				current.openHandlerTailNs = now
				current.openHandlerTailTick = tick
				current.openHandlerTailThread = Thread.currentThread().name
				current.openHandlerEvidence = openHandlerEvidence(current)
				if (current.openHandlerEvidence?.startsWith("matching container GUI was active") == true) {
					visibleSession = current
				}
				return
			}

			val link = packetResponses.remove(packet) ?: return
			val current = link.session
			val response = link.response
			response.handlerTailNs = now
			response.handlerTailTick = tick
			response.handlerTailThread = Thread.currentThread().name
			response.handlerEvidence = responseHandlerEvidence(current, response.payload)
			if (response.kind == ResponseKind.CLOSE) {
				current.serverCloseHandlerTailNs = now
				current.reportAfterNs = maxOf(current.reportAfterNs ?: 0L, now + REPORT_GRACE_NS)
				if (visibleSession === current) {
					visibleSession = null
				}
			}
		}
	}

	private fun openHandlerEvidence(session: TerminalSession): String {
		val screen = Minecraft.getInstance().screen as? AbstractContainerScreen<*>
		return if (screen?.menu?.containerId == session.containerId) {
			"matching container GUI was active at the handler tail hook"
		} else {
			"matching container GUI was not active at the handler tail hook"
		}
	}

	private fun responseHandlerEvidence(session: TerminalSession, payload: ResponsePayload): String {
		val menu = Minecraft.getInstance().player?.containerMenu
		return when (payload) {
			is ResponsePayload.SetSlot -> when {
				menu == null -> "no local player menu was available"
				menu.containerId != session.containerId -> "current menu container=${menu.containerId}; packet container=${session.containerId}, so no matching-menu application was verified"
				payload.slot !in menu.slots.indices -> "matching menu was active, but slot ${payload.slot} was outside its slot list"
				ItemStack.matches(menu.getSlot(payload.slot).item, payload.item) && menu.stateId == payload.stateId -> "matching menu revision and slot value were observed"
				else -> "matching menu was active, but revision/slot value did not match at the handler tail hook"
			}
			is ResponsePayload.SetContent -> when {
				menu == null -> "no local player menu was available"
				menu.containerId != session.containerId -> "current menu container=${menu.containerId}; packet container=${session.containerId}, so no matching-menu application was verified"
				menu.stateId != payload.stateId -> "matching menu was active, but revision ${menu.stateId} did not match packet revision ${payload.stateId}"
				payload.items.size > menu.slots.size -> "matching menu was active, but packet had more slots than the local menu"
				payload.items.indices.all { ItemStack.matches(menu.getSlot(it).item, payload.items[it]) } -> "matching menu revision and all packet slot values were observed"
				else -> "matching menu was active, but at least one slot value did not match at the handler tail hook"
			}
			is ResponsePayload.SetData -> if (menu?.containerId == session.containerId) {
				"matching menu was active; the private data-slot value was not independently read"
			} else {
				"matching menu was not active; data-slot application was not verified"
			}
			is ResponsePayload.SetCursor -> "cursor-item handler tail reached; no container id exists in this packet"
			is ResponsePayload.Close -> if (menu?.containerId != session.containerId) {
				"the terminal container was no longer the active local menu"
			} else {
				"the terminal container was still active; close application was not verified"
			}
			is ResponsePayload.CompletionChat -> "system-chat handler tail reached"
		}
	}

	private fun observeScreenFallback(current: TerminalSession, client: Minecraft, now: Long) {
		if (current.end != null) {
			return
		}
		val screen = client.screen as? AbstractContainerScreen<*>
		if (screen?.menu?.containerId == current.containerId) {
			if (current.screenObservedNs == null) {
				current.screenObservedNs = now
				current.screenObservedTick = clientTick.get()
			}
			current.screenGoneSinceNs = null
			return
		}

		if (current.openHandlerTailNs == null) {
			return
		}
		val goneSince = current.screenGoneSinceNs
		if (goneSince == null) {
			current.screenGoneSinceNs = now
		} else if (now - goneSince >= SCREEN_GONE_GRACE_NS) {
			current.end = EndTrace(
				EndKind.SCREEN_GONE,
				now,
				clientTick.get(),
				"the local container screen disappeared without a close packet"
			)
			current.reportAfterNs = now
		}
	}

	private fun finishSessionLocked(session: TerminalSession) {
		val wasActive = activeSession === session
		val wasPending = pendingSessions.remove(session)
		if (!wasActive && !wasPending) {
			return
		}
		readyReports.add(session.snapshot())
		if (wasActive) {
			activeSession = null
		}
		if (visibleSession === session) {
			visibleSession = null
		}
		packetOpens.entries.removeIf { it.value.session.id == session.id || it.value.displacedSession?.id == session.id }
		packetClicks.entries.removeIf { it.value.sessionId == session.id }
		packetResponses.entries.removeIf { it.value.session.id == session.id }
	}

	private fun moveToPendingLocked(session: TerminalSession) {
		if (activeSession === session) {
			activeSession = null
		}
		if (pendingSessions.none { it.id == session.id }) {
			pendingSessions.add(session)
		}
	}

	private fun findSessionForContainerLocked(containerId: Int): TerminalSession? =
		activeSession?.takeIf { it.containerId == containerId }
			?: pendingSessions.lastOrNull { it.containerId == containerId }

	private fun findSessionForClosePacketLocked(packet: Packet<*>): TerminalSession? =
		(sequenceOf(activeSession) + pendingSessions.asSequence())
			.filterNotNull()
			.firstOrNull { packet === it.clientClosePacket }

	private fun restoreDisplacedSessionLocked(session: TerminalSession?) {
		if (session == null) return
		pendingSessions.remove(session)
		if (activeSession == null || activeSession?.openCancelledNs != null) {
			activeSession = session
		}
		visibleSession = session
		session.end = session.end?.takeUnless { it.kind == EndKind.REPLACED_BY_SCREEN }
		if (session.end == null) session.reportAfterNs = null
	}

	private fun printReport(reports: List<TerminalReport>) {
		printReadableReport(reports)
	}

	@Suppress("unused")
	private fun printAdvancedReport(client: Minecraft, report: TerminalReport) {
		val sentClicks = report.clicks
			.filter { it.enqueueNs != null && it.cancelledNs == null }
			.sortedBy { it.enqueueNs }
		val intentClicks = report.clicks.filter { it.intentNs != null }.sortedBy { it.intentNs }
		val firstIntentClick = intentClicks.minByOrNull { it.intentNs!! }
		val lastSentClick = sentClicks.maxByOrNull { it.enqueueNs!! }
		val firstSentClick = sentClicks.minByOrNull { it.enqueueNs!! }
		val closeDecoded = report.serverCloseDecodedNs
		val closeHandled = report.serverCloseHandlerTailNs
		val clientClose = report.clientCloseRequestNs
		val ping = client.player?.uuid
			?.let { client.connection?.getPlayerInfo(it)?.latency }

		chat("${ChatFormatting.DARK_AQUA}${ChatFormatting.BOLD}Terminal Times${ChatFormatting.RESET}${ChatFormatting.GRAY} — ${report.terminal.displayName} (${report.title})")
		chat("${ChatFormatting.AQUA}${ChatFormatting.BOLD}CLIENT TIMELINE${ChatFormatting.RESET}${ChatFormatting.GRAY} — local System.nanoTime observation points")
		val openBundle = report.openBundlePosition?.let { ", bundle child $it (all children share the outer bundle delivery timestamp)" } ?: ""
		chat("${ChatFormatting.GRAY}Open inbound delivery observed ${at(report, report.openDecodedNs)} on '${report.openThread}', sampled client-tick counter ${report.openTick}$openBundle; menu=${report.menuType}, container=${report.containerId}")
		chat("${ChatFormatting.DARK_GRAY}Open observer traversal/capture: start=${at(report, report.openObserverStartedNs)}, captured=${at(report, report.openObserverCapturedNs)} (${duration(report.openObserverCapturedNs - report.openObserverStartedNs)} work; ${duration(report.openObserverStartedNs - report.openDecodedNs)} after delivery boundary)")
		report.openHandlerTailNs?.let {
			chat("${ChatFormatting.GRAY}OpenScreen handler tail reached ${at(report, it)} (inbound delivery→tail ${duration(it - report.openDecodedNs)}), client tick ${report.openHandlerTailTick}, thread='${report.openHandlerTailThread}'; ${report.openHandlerEvidence}")
		}
		if (report.screenObservedNs != null) {
			val prefix = if (report.openHandlerTailNs == null) ChatFormatting.YELLOW else ChatFormatting.GRAY
			val suffix = if (report.openHandlerTailNs == null) "; handler-tail hook was not observed" else ""
			chat("${prefix}Container screen first observed at client-tick start ${at(report, report.screenObservedNs)}, tick ${report.screenObservedTick}$suffix")
		}

		val firstData = report.responses.firstOrNull { it.kind == ResponseKind.SET_CONTENT || it.kind == ResponseKind.SET_SLOT }
		firstData?.let {
			chat("${ChatFormatting.GRAY}First authoritative container-data delivery observed ${at(report, it.decodedNs)} (${duration(it.decodedNs - report.openDecodedNs)} after open)")
		}

		var previousEnqueue: Long? = null
		for (click in report.clicks.sortedBy { it.intentNs ?: it.enqueueNs ?: Long.MAX_VALUE }) {
			val intentNs = click.intentNs
			val enqueueNs = click.enqueueNs
			val handoffNs = click.handoffNs
			val inputReturnNs = click.inputReturnNs
			val writeReturnNs = click.writeReturnNs
			val intent = intentNs?.let { at(report, it) } ?: "not observed"
			val enqueue = enqueueNs?.let { at(report, it) } ?: "not sent"
			val gap = if (enqueueNs != null && previousEnqueue != null) {
				", previous packet gap=${duration(enqueueNs - previousEnqueue)}"
			} else {
				""
			}
			val local = if (intentNs != null && enqueueNs != null) {
				", container-input dispatch→send request=${duration(enqueueNs - intentNs)}"
			} else {
				""
			}
			val queue = if (enqueueNs != null && handoffNs != null) {
				", send request→doSendPacket entry=${duration(handoffNs - enqueueNs)}"
			} else {
				""
			}
			val inputReturn = if (intentNs != null && inputReturnNs != null) {
				", container-input method=${duration(inputReturnNs - intentNs)}"
			} else {
				""
			}
			val writeReturn = if (handoffNs != null && writeReturnNs != null) {
				", doSendPacket entry→return site=${duration(writeReturnNs - handoffNs)}"
			} else {
				""
			}
			val result = when {
				click.cancelledNs != null -> " ${ChatFormatting.RED}[cancelled locally ${at(report, click.cancelledNs!!)} on '${click.cancelledThread}']"
				click.enqueueNs == null -> " ${ChatFormatting.YELLOW}[no packet observed]"
				click.handoffNs == null -> " ${ChatFormatting.YELLOW}[doSendPacket entry not observed]"
				else -> ""
			}
			val ticks = "ticks(intent/send/write-entry)=${click.intentTick ?: "?"}/${click.enqueueTick ?: "?"}/${click.handoffTick ?: "?"}"
			val threads = "threads=${click.intentThread ?: "?"}/${click.enqueueThread ?: "?"}/${click.handoffThread ?: "?"}"
			chat("${ChatFormatting.WHITE}Click #${click.ordinal} [slot=${click.slot}, button=${click.button}, input=${click.input}]: container-input dispatch=$intent, send request=$enqueue$local$queue$inputReturn$writeReturn$gap; $ticks; $threads$result")
			if (enqueueNs != null) {
				previousEnqueue = enqueueNs
			}
		}

		if (firstIntentClick != null) {
			val fromHandler = if (report.openHandlerTailNs != null) {
				"; open-handler tail→first dispatch: ${duration(firstIntentClick.intentNs!! - report.openHandlerTailNs)}"
			} else {
				""
			}
			val fromData = if (firstData != null) {
				"; first data delivery→first intent: ${duration(firstIntentClick.intentNs!! - firstData.decodedNs)}"
			} else {
				""
			}
			chat("${ChatFormatting.GREEN}Open→first observed container-input dispatch: ${duration(firstIntentClick.intentNs!! - report.openDecodedNs)}$fromHandler$fromData")
		}
		if (firstSentClick != null) {
			chat("${ChatFormatting.GREEN}Open→first packet send request: ${duration(firstSentClick.enqueueNs!! - report.openDecodedNs)}")
		}
		if (lastSentClick != null) {
			chat("${ChatFormatting.GREEN}Open→last packet send request: ${duration(lastSentClick.enqueueNs!! - report.openDecodedNs)}")
		}

		val gaps = sentClicks.mapNotNull { it.enqueueNs }.sorted().zipWithNext { a, b -> b - a }
		if (gaps.isNotEmpty()) {
			val average = gaps.average().toLong()
			val span = sentClicks.maxOf { it.enqueueNs!! } - sentClicks.minOf { it.enqueueNs!! }
			val cps = if (span > 0L) (sentClicks.size - 1) * 1_000_000_000.0 / span else 0.0
			chat("${ChatFormatting.GREEN}Packet send-request gaps: min=${duration(gaps.min())}, avg=${duration(average)}, max=${duration(gaps.max())}; span=${duration(span)}, interval CPS=${formatNumber(cps)}")
		}
		val intentGaps = intentClicks.mapNotNull { it.intentNs }.sorted().zipWithNext { a, b -> b - a }
		if (intentGaps.isNotEmpty()) {
			chat("${ChatFormatting.GREEN}Container-input dispatch gaps: ${intentGaps.joinToString { duration(it) }}")
		}

		if (closeDecoded != null) {
			val lastDelta = lastSentClick?.enqueueNs?.let { duration(closeDecoded - it) } ?: "n/a"
			chat("${ChatFormatting.GREEN}Server close inbound delivery observed ${at(report, closeDecoded)}; last send request→close=$lastDelta; open→close=${duration(closeDecoded - report.openDecodedNs)}")
		}
		if (closeHandled != null) {
			chat("${ChatFormatting.GREEN}Close handler tail reached ${at(report, closeHandled)} (inbound delivery→tail ${duration(closeHandled - closeDecoded!!)})")
		}
		if (clientClose != null) {
			val cancelled = report.clientCloseCancelledNs?.let { "; cancelled locally ${at(report, it)}" } ?: ""
			val toWrite = report.clientCloseHandoffNs?.let { "; request→doSendPacket=${duration(it - clientClose)} [tick=${report.clientCloseHandoffTick}, thread='${report.clientCloseHandoffThread}']" } ?: "; doSendPacket entry not observed"
			val writeCall = if (report.clientCloseHandoffNs != null && report.clientCloseWriteReturnNs != null) {
				"; entry→return site=${duration(report.clientCloseWriteReturnNs - report.clientCloseHandoffNs)} [tick=${report.clientCloseWriteReturnTick}, thread='${report.clientCloseWriteReturnThread}']"
			} else {
				""
			}
			chat("${ChatFormatting.GREEN}Client close send request ${at(report, clientClose)} [tick=${report.clientCloseRequestTick}, thread='${report.clientCloseRequestThread}']: container=${report.containerId}$toWrite$writeCall, flush=${report.clientCloseFlush ?: "unknown"}$cancelled; packet reason and async/remote outcome unknown.")
		}
		chat("${ChatFormatting.GREEN}Open→observed end boundary: ${duration(report.end.timestampNs - report.openDecodedNs)} (client tick ${report.end.tick})")
		chat("${ChatFormatting.GRAY}End boundary: ${report.end.kind.displayName} — ${report.end.detail}")
		if (report.localCompletionMessage != null) {
			chat("${ChatFormatting.GREEN}Local-player terminal-completion chat time-correlated to this session: '${report.localCompletionMessage}'. The chat has no container/session id, so this is supporting evidence, not proof of the association.")
		} else if (report.end.kind == EndKind.SERVER_CLOSE) {
			chat("${ChatFormatting.YELLOW}No local-player completion chat was observed before reporting; the close packet itself does not state why it closed.")
		}

		chat("${ChatFormatting.LIGHT_PURPLE}${ChatFormatting.BOLD}SERVERBOUND PAYLOAD / INBOUND SERVER RESPONSE${ChatFormatting.RESET}")
		chat("${ChatFormatting.YELLOW}Exact remote receive/process timestamps are unavailable: container clicks carry no timestamp/transaction ACK, and server ticks are not returned.")
		for (click in report.clicks.filter { it.enqueueNs != null }.sortedBy { it.enqueueNs }) {
			val changedSlots = click.changedSlots.sortedBy { it.index }
			val changedIndices = changedSlots.joinToString(prefix = "[", postfix = "]") { it.index.toString() }
			val packetLabel = when {
				click.cancelledNs != null -> "LOCAL-CANCELLED #${click.ordinal} (not server-visible)"
				click.handoffNs == null -> "C2S REQUEST #${click.ordinal} (doSendPacket entry not observed; visibility unknown)"
				else -> "C2S WRITE ATTEMPT #${click.ordinal} (async outcome and remote receipt unknown)"
			}
			chat("${ChatFormatting.LIGHT_PURPLE}$packetLabel: container=${click.containerId}, revision=${click.stateId}, slot=${click.slot}, button=${click.button}, input=${click.input}, predictedChanged=$changedIndices")
			if (changedSlots.isEmpty()) {
				chat("${ChatFormatting.DARK_GRAY}  predicted slot hashes: none")
			} else {
				for (changed in changedSlots) {
					chat("${ChatFormatting.DARK_GRAY}  predicted hashed stack at slot ${changed.index}: ${changed.value}")
				}
			}
			val capture = if (click.payloadObserverStartedNs != null && click.payloadCapturedNs != null) {
				"; payload snapshot=${duration(click.payloadCapturedNs!! - click.payloadObserverStartedNs!!)}"
			} else {
				""
			}
			chat("${ChatFormatting.DARK_GRAY}  predicted carried hashed stack: ${click.carriedItem ?: "unavailable"}; doSendPacket entry=${click.handoffNs?.let { at(report, it) } ?: "not observed"}, flush=${click.flush ?: "unknown"}, doSendPacket return site=${click.writeReturnNs?.let { at(report, it) } ?: "not observed"}; async outcome and remote receipt unknown$capture")
		}

		var previousResponseNs = report.openDecodedNs
		for ((index, response) in report.responses.withIndex()) {
			val clicksSincePrevious = sentClicks.filter {
				val enqueue = it.enqueueNs!!
				enqueue > previousResponseNs && enqueue <= response.decodedNs
			}
			val latest = sentClicks.lastOrNull { it.enqueueNs!! <= response.decodedNs }
			val relation = when {
				clicksSincePrevious.size == 1 -> "after click #${clicksSincePrevious[0].ordinal} by ${duration(response.decodedNs - clicksSincePrevious[0].enqueueNs!!)}; not an explicit ACK"
				clicksSincePrevious.size > 1 -> "after clicks #${clicksSincePrevious.first().ordinal}–#${clicksSincePrevious.last().ordinal}; latest by ${duration(response.decodedNs - clicksSincePrevious.last().enqueueNs!!)}, attribution ambiguous"
				latest != null -> "${duration(response.decodedNs - latest.enqueueNs!!)} after latest click #${latest.ordinal}; may be unrelated"
				else -> "before the first click"
			}
			val handled = when {
				response.cancelledNs != null -> " | ${ChatFormatting.RED}cancelled locally ${at(report, response.cancelledNs!!)} on '${response.cancelledThread}'"
				response.handlerTailNs != null -> " | handler tail reached ${at(report, response.handlerTailNs!!)} (inbound delivery→tail ${duration(response.handlerTailNs!! - response.decodedNs)}), tick=${response.handlerTailTick}, thread='${response.handlerTailThread}'; evidence: ${response.handlerEvidence}"
				else -> " | handler tail not observed; application unknown"
			}
			val bundle = response.bundlePosition?.let { ", bundle child $it" } ?: ""
			val capture = "observer start=${at(report, response.observerStartedNs)}, snapshot=${at(report, response.observerCapturedNs)} (${duration(response.observerCapturedNs - response.observerStartedNs)})"
			chat("${ChatFormatting.LIGHT_PURPLE}S2C ${index + 1} ${response.kind.displayName} ${at(report, response.decodedNs)} [sampled client tick=${response.decodedTick}, thread='${response.decodedThread}'$bundle]: ${responseDetail(response)} | $relation$handled | $capture")
			previousResponseNs = response.decodedNs
		}
		if (report.droppedResponses > 0) {
			chat("${ChatFormatting.RED}${report.droppedResponses} additional response events were omitted after the safety limit of $MAX_RESPONSE_EVENTS.")
		}
		chat("${ChatFormatting.GRAY}Latency indicator at report time: ${ping?.let { "$it ms tab-list ping" } ?: "unavailable"}; it is a sampled RTT indicator, not this terminal's one-way delay.")
		chat("${ChatFormatting.DARK_GRAY}Revision/stateId is inventory sync state, not a server tick or click acknowledgement. Sampled client-tick counters are context only. TCP preserves order, but remote click spacing/processing can differ.")
		val firstSummary = firstSentClick?.enqueueNs?.let { duration(it - report.openDecodedNs) } ?: "n/a"
		val lastSummary = lastSentClick?.enqueueNs?.let { duration(it - report.openDecodedNs) } ?: "n/a"
		val confirmation = if (report.localCompletionMessage != null) "server chat time-correlated (association unproven)" else "no time-correlated completion chat"
		chat("${ChatFormatting.AQUA}${ChatFormatting.BOLD}Summary:${ChatFormatting.RESET}${ChatFormatting.AQUA} ${sentClicks.size} non-cancelled send requests, first=$firstSummary, last=$lastSummary, observed end=${duration(report.end.timestampNs - report.openDecodedNs)}, ${report.responses.size} server-originated events; ${report.end.kind.displayName}, $confirmation.")
		chat("${ChatFormatting.DARK_AQUA}${ChatFormatting.BOLD}End Terminal Times${ChatFormatting.RESET}")
	}

	private fun printReadableReport(reports: List<TerminalReport>) {
		val orderedReports = reports.sortedBy { it.openDecodedNs }
		val firstReport = orderedReports.first()
		val lastReport = orderedReports.maxBy { it.end.timestampNs }
		val reportClicks = orderedReports
			.flatMap { report ->
				report.clicks
					.filter { it.enqueueNs != null && it.cancelledNs == null }
					.map { report to it }
			}
			.sortedBy { (_, click) -> click.intentNs ?: click.enqueueNs }
		val clicks = reportClicks.map { it.second }
		val clientClickTimes = clicks.mapNotNull { it.intentNs ?: it.enqueueNs }
		val serverEvents: List<Pair<Long, ResponsePayload?>> = buildList {
			orderedReports.forEachIndexed { reportIndex, report ->
				if (reportIndex > 0) {
					// A replacement OpenScreen is itself an authoritative server boundary.
					add(report.openDecodedNs to null)
				}
				report.responses
					.asSequence()
					.filter { it.cancelledNs == null }
					.filter {
						it.kind == ResponseKind.SET_SLOT
							|| it.kind == ResponseKind.SET_CONTENT
							|| it.kind == ResponseKind.CLOSE
					}
					.forEach { add(it.decodedNs to it.payload) }
			}
		}.sortedBy { it.first }
		val serverReplyTimes: List<Long?> = reportClicks.mapIndexed { index, (_, click) ->
			val sentNs = click.enqueueNs ?: return@mapIndexed null
			val nextSentNs = reportClicks.getOrNull(index + 1)?.second?.enqueueNs ?: Long.MAX_VALUE
			serverEvents.firstOrNull { (eventNs, payload) ->
				eventNs >= sentNs
					&& eventNs < nextSentNs
					&& isAuthoritativeReplyFor(click, payload)
			}?.first
		}

		val clientStartNs = firstReport.openHandlerTailNs ?: firstReport.screenObservedNs ?: firstReport.openDecodedNs
		val clientEndNs = lastReport.serverCloseHandlerTailNs
			?: lastReport.clientCloseRequestNs
			?: lastReport.end.timestampNs
		val serverEndNs = orderedReports.mapNotNull { it.serverCloseDecodedNs }.maxOrNull()
		val clientIntervals = intervals(clientClickTimes, clientStartNs)
		val serverIntervals = nullableIntervals(serverReplyTimes, firstReport.openDecodedNs)

		val lines = buildList {
			add("${ChatFormatting.DARK_AQUA}${ChatFormatting.BOLD}Terminal Times${ChatFormatting.RESET}${ChatFormatting.GRAY} — ${firstReport.terminal.displayName}")
			add("${ChatFormatting.GRAY}Client total: ${ChatFormatting.WHITE}${duration(clientEndNs - clientStartNs)}")
			add("${ChatFormatting.GRAY}Server reply total: ${ChatFormatting.WHITE}${serverEndNs?.let { duration(it - firstReport.openDecodedNs) } ?: "—"}")
			add("${ChatFormatting.GRAY}First click: ${ChatFormatting.WHITE}${clientIntervals.firstOrNull() ?: "—"}")
			add("${ChatFormatting.GRAY}First server reply: ${ChatFormatting.WHITE}${serverIntervals.firstOrNull() ?: "—"}")
			add("${ChatFormatting.GRAY}Clicks: ${ChatFormatting.WHITE}${clicks.size}")
			clientIntervals.forEachIndexed { index, clientInterval ->
				add(
					"${ChatFormatting.DARK_GRAY}Click ${index + 1}: ${ChatFormatting.GRAY}Client ${ChatFormatting.WHITE}$clientInterval" +
						"${ChatFormatting.GRAY} | Server reply ${ChatFormatting.WHITE}${serverIntervals.getOrNull(index) ?: "—"}"
				)
			}
		}
		chat(lines.joinToString("\n"))
	}

	private fun intervals(times: List<Long>, startNs: Long): List<String> =
		times.mapIndexed { index, timestamp ->
			duration(timestamp - if (index == 0) startNs else times[index - 1])
		}

	private fun nullableIntervals(times: List<Long?>, startNs: Long): List<String?> {
		var previousNs: Long? = startNs
		return times.map { timestamp ->
			val previous = previousNs
			val interval = if (timestamp != null && previous != null) {
				duration(timestamp - previous)
			} else {
				null
			}
			previousNs = timestamp
			interval
		}
	}

	private fun isAuthoritativeReplyFor(click: ClickTrace, payload: ResponsePayload?): Boolean =
		when (payload) {
			null -> true
			is ResponsePayload.SetSlot ->
				payload.slot == click.slot
			is ResponsePayload.SetContent,
			is ResponsePayload.Close -> true
			else -> false
		}

	private fun responseDetail(response: ResponseTrace): String {
		return when (val payload = response.payload) {
			is ResponsePayload.SetSlot ->
				"revision=${payload.stateId}, slot=${payload.slot}, item=${payload.item}"
			is ResponsePayload.SetContent -> {
				val nonEmpty = payload.items.withIndex()
					.filterNot { it.value.isEmpty }
					.joinToString(prefix = "[", postfix = "]") { "${it.index}=${it.value}" }
				"revision=${payload.stateId}, items=${payload.items.size}, nonEmpty=$nonEmpty, carried=${payload.carriedItem}"
			}
			is ResponsePayload.SetData ->
				"property=${payload.id}, value=${payload.value}"
			is ResponsePayload.SetCursor ->
				"item=${payload.item}"
			is ResponsePayload.Close ->
				"container=${payload.containerId}; the packet contains no completion reason"
			is ResponsePayload.CompletionChat ->
				"message='${payload.text}', actor='${payload.actor}', localPlayer=${payload.localPlayer}"
		}
	}

	private fun snapshotResponsePayload(packet: Packet<*>): ResponsePayload = when (packet) {
		is ClientboundContainerSetSlotPacket ->
			ResponsePayload.SetSlot(packet.stateId, packet.slot, packet.item.copy())
		is ClientboundContainerSetContentPacket ->
			ResponsePayload.SetContent(
				packet.stateId(),
				packet.items().map(ItemStack::copy),
				packet.carriedItem().copy()
			)
		is ClientboundContainerSetDataPacket ->
			ResponsePayload.SetData(packet.id, packet.value)
		else -> error("Unsupported container response payload: ${packet::class.java.name}")
	}

	private fun chat(message: String) {
		ChatUtils.chat(message)
	}

	private fun at(report: TerminalReport, timestamp: Long): String =
		"+${duration(timestamp - report.openDecodedNs)}"

	private fun duration(nanos: Long): String =
		"${formatNumber(nanos / 1_000_000.0)} ms"

	private fun formatNumber(number: Double): String =
		String.format(Locale.ROOT, "%.3f", number)

	private fun TerminalSession.snapshot(): TerminalReport =
		TerminalReport(
			terminal = terminal,
			containerId = containerId,
			title = title,
			menuType = menuType,
			openDecodedNs = openDecodedNs,
			openTick = openTick,
			openThread = openThread,
			openBundlePosition = openBundlePosition,
			openObserverStartedNs = openObserverStartedNs,
			openObserverCapturedNs = openObserverCapturedNs,
			openHandlerTailNs = openHandlerTailNs,
			openHandlerTailTick = openHandlerTailTick,
			openHandlerTailThread = openHandlerTailThread,
			openHandlerEvidence = openHandlerEvidence,
			screenObservedNs = screenObservedNs,
			screenObservedTick = screenObservedTick,
			clicks = clicks.map { it.copy(changedSlots = it.changedSlots.toList()) },
			responses = responses.map { it.copy() },
			serverCloseDecodedNs = serverCloseDecodedNs,
			serverCloseHandlerTailNs = serverCloseHandlerTailNs,
			clientCloseRequestNs = clientCloseRequestNs,
			clientCloseRequestTick = clientCloseRequestTick,
			clientCloseRequestThread = clientCloseRequestThread,
			clientCloseCancelledNs = clientCloseCancelledNs,
			clientCloseHandoffNs = clientCloseHandoffNs,
			clientCloseHandoffTick = clientCloseHandoffTick,
			clientCloseHandoffThread = clientCloseHandoffThread,
			clientCloseFlush = clientCloseFlush,
			clientCloseWriteReturnNs = clientCloseWriteReturnNs,
			clientCloseWriteReturnTick = clientCloseWriteReturnTick,
			clientCloseWriteReturnThread = clientCloseWriteReturnThread,
			localCompletionMessage = localCompletionMessage,
			end = end ?: EndTrace(
				EndKind.UNKNOWN,
				System.nanoTime(),
				clientTick.get(),
				"session ended without an observed close boundary"
			),
			droppedResponses = droppedResponses
		)

	private data class TerminalSession(
		val terminal: TerminalKind,
		val containerId: Int,
		val title: String,
		val menuType: String,
		val openPacket: Packet<*>,
		val openDecodedNs: Long,
		val openTick: Long,
		val openThread: String,
		val openBundlePosition: String?,
		val openObserverStartedNs: Long,
		val openObserverCapturedNs: Long,
		val id: Long = nextSessionId.incrementAndGet(),
		var openHandlerTailNs: Long? = null,
		var openHandlerTailTick: Long? = null,
		var openHandlerTailThread: String? = null,
		var openHandlerEvidence: String? = null,
		var screenObservedNs: Long? = null,
		var screenObservedTick: Long? = null,
		var screenGoneSinceNs: Long? = null,
		var openCancelledNs: Long? = null,
		var nextClickOrdinal: Int = 1,
		val clicks: MutableList<ClickTrace> = arrayListOf(),
		val responses: MutableList<ResponseTrace> = arrayListOf(),
		var droppedResponses: Int = 0,
		var clientCloseRequestNs: Long? = null,
		var clientCloseRequestTick: Long? = null,
		var clientCloseRequestThread: String? = null,
		var clientClosePacket: Packet<*>? = null,
		var clientCloseCancelledNs: Long? = null,
		var clientCloseHandoffNs: Long? = null,
		var clientCloseHandoffTick: Long? = null,
		var clientCloseHandoffThread: String? = null,
		var clientCloseFlush: Boolean? = null,
		var clientCloseWriteReturnNs: Long? = null,
		var clientCloseWriteReturnTick: Long? = null,
		var clientCloseWriteReturnThread: String? = null,
		var serverCloseDecodedNs: Long? = null,
		var serverCloseHandlerTailNs: Long? = null,
		var localCompletionMessage: String? = null,
		var end: EndTrace? = null,
		var reportAfterNs: Long? = null
	)

	private data class ClickTrace(
		val sessionId: Long,
		val ordinal: Int,
		val containerId: Int,
		val slot: Int,
		val button: Int,
		val input: ContainerInput,
		val intentNs: Long?,
		val intentTick: Long?,
		val intentThread: String?,
		var enqueueNs: Long? = null,
		var enqueueTick: Long? = null,
		var enqueueThread: String? = null,
		var stateId: Int? = null,
		var changedSlots: List<ChangedSlotTrace> = emptyList(),
		var carriedItem: HashedStack? = null,
		var payloadObserverStartedNs: Long? = null,
		var payloadCapturedNs: Long? = null,
		var handoffNs: Long? = null,
		var handoffTick: Long? = null,
		var handoffThread: String? = null,
		var flush: Boolean? = null,
		var writeReturnNs: Long? = null,
		var writeReturnTick: Long? = null,
		var writeReturnThread: String? = null,
		var inputReturnNs: Long? = null,
		var inputReturnTick: Long? = null,
		var inputReturnThread: String? = null,
		var cancelledNs: Long? = null,
		var cancelledThread: String? = null
	)

	private data class ChangedSlotTrace(val index: Int, val value: HashedStack)

	private data class ResponseTrace(
		val kind: ResponseKind,
		val payload: ResponsePayload,
		val decodedNs: Long,
		val decodedTick: Long,
		val decodedThread: String,
		val bundlePosition: String?,
		val observerStartedNs: Long,
		val observerCapturedNs: Long,
		var handlerTailNs: Long? = null,
		var handlerTailTick: Long? = null,
		var handlerTailThread: String? = null,
		var handlerEvidence: String? = null,
		var cancelledNs: Long? = null,
		var cancelledThread: String? = null
	)

	private data class ResponseLink(val session: TerminalSession, val response: ResponseTrace)
	private data class OpenLink(val session: TerminalSession, val displacedSession: TerminalSession?)

	private data class EndTrace(
		val kind: EndKind,
		val timestampNs: Long,
		val tick: Long,
		val detail: String
	)

	private data class TerminalReport(
		val terminal: TerminalKind,
		val containerId: Int,
		val title: String,
		val menuType: String,
		val openDecodedNs: Long,
		val openTick: Long,
		val openThread: String,
		val openBundlePosition: String?,
		val openObserverStartedNs: Long,
		val openObserverCapturedNs: Long,
		val openHandlerTailNs: Long?,
		val openHandlerTailTick: Long?,
		val openHandlerTailThread: String?,
		val openHandlerEvidence: String?,
		val screenObservedNs: Long?,
		val screenObservedTick: Long?,
		val clicks: List<ClickTrace>,
		val responses: List<ResponseTrace>,
		val serverCloseDecodedNs: Long?,
		val serverCloseHandlerTailNs: Long?,
		val clientCloseRequestNs: Long?,
		val clientCloseRequestTick: Long?,
		val clientCloseRequestThread: String?,
		val clientCloseCancelledNs: Long?,
		val clientCloseHandoffNs: Long?,
		val clientCloseHandoffTick: Long?,
		val clientCloseHandoffThread: String?,
		val clientCloseFlush: Boolean?,
		val clientCloseWriteReturnNs: Long?,
		val clientCloseWriteReturnTick: Long?,
		val clientCloseWriteReturnThread: String?,
		val localCompletionMessage: String?,
		val end: EndTrace,
		val droppedResponses: Int
	)

	private data class InputToken(val worldEpoch: Long, val click: ClickTrace?)

	private sealed interface ResponsePayload {
		data class SetSlot(val stateId: Int, val slot: Int, val item: ItemStack) : ResponsePayload
		data class SetContent(val stateId: Int, val items: List<ItemStack>, val carriedItem: ItemStack) : ResponsePayload
		data class SetData(val id: Int, val value: Int) : ResponsePayload
		data class SetCursor(val item: ItemStack) : ResponsePayload
		data class Close(val containerId: Int) : ResponsePayload
		data class CompletionChat(val text: String, val actor: String, val localPlayer: Boolean) : ResponsePayload
	}

	private enum class ResponseKind(val displayName: String) {
		SET_SLOT("SetSlot"),
		SET_CONTENT("SetContent"),
		SET_DATA("SetData"),
		SET_CURSOR("SetCursor"),
		CLOSE("Close"),
		COMPLETION_CHAT("CompletionChat")
	}

	private enum class EndKind(val displayName: String) {
		SERVER_CLOSE("server-originated close (reason unknown)"),
		CLIENT_CLOSE("client-originated close request (reason unknown)"),
		REPLACED_BY_SCREEN("screen replaced (completion unknown)"),
		OPEN_CANCELLED("terminal open cancelled locally"),
		SCREEN_GONE("local screen disappeared (completion unknown)"),
		WORLD_CHANGE("connection/world changed (completion unknown)"),
		UNKNOWN("unknown")
	}

	private enum class TerminalKind(val displayName: String, val titlePrefix: String) {
		ORDER("Order", "Click in order!"),
		SELECT("Select", "Select all the"),
		STARTS_WITH("Starts With", "What starts with:"),
		RUBIX("Rubix", "Change all to same color!"),
		PANES("Panes", "Correct all the panes!"),
		MAZE("Maze", "Navigate the maze!"),
		MELODY("Melody", "Click the button on time!");

		companion object {
			fun fromTitle(title: String): TerminalKind? =
				entries.firstOrNull { title.startsWith(it.titlePrefix) }
		}
	}

	companion object {
		private val nextSessionId = AtomicLong()
		private var instance: TerminalTimes? = null
		private const val MAX_RESPONSE_EVENTS = 512
		private const val REPORT_GRACE_NS = 250_000_000L
		private const val CLOSE_HANDLER_FALLBACK_NS = 500_000_000L
		private const val CLIENT_CLOSE_GRACE_NS = 250_000_000L
		private const val SCREEN_GONE_GRACE_NS = 100_000_000L
		private const val VISIT_EXIT_GRACE_NS = 750_000_000L
		private const val COMPLETION_CORRELATION_NS = 2_000_000_000L
		private val TERMINAL_COMPLETION_PATTERN = Pattern.compile("^(.*?) (?:activated|completed) a terminal! \\((\\d+)/(\\d+)\\)")

		@JvmStatic
		fun observeDecodedPacket(packet: Packet<*>) {
			instance?.withObservation { epoch -> handleDecodedPacket(packet, epoch) }
		}

		@JvmStatic
		fun observePacketSendRequest(packet: Packet<*>) {
			instance?.withObservation { epoch -> handlePacketSendRequest(packet, epoch) }
		}

		@JvmStatic
		fun observePacketSendCancelled(packet: Packet<*>) {
			instance?.withObservation { epoch -> handlePacketSendCancelled(packet, epoch) }
		}

		@JvmStatic
		fun observePacketReceiveCancelled(packet: Packet<*>) {
			instance?.withObservation { epoch -> handlePacketReceiveCancelled(packet, epoch) }
		}

		@JvmStatic
		fun observePacketWriteHandoff(packet: Packet<*>, flush: Boolean) {
			instance?.withObservation { epoch -> handlePacketWriteHandoff(packet, flush, epoch) }
		}

		@JvmStatic
		fun observePacketWriteReturned(packet: Packet<*>) {
			instance?.withObservation { epoch -> handlePacketWriteReturned(packet, epoch) }
		}

		@JvmStatic
		fun observeContainerInputStart(containerId: Int, slot: Int, button: Int, input: ContainerInput) {
			instance?.withObservation { epoch -> observeInputStart(containerId, slot, button, input, epoch) }
		}

		@JvmStatic
		fun observeContainerInputEnd() {
			instance?.observeInputEnd()
		}

		@JvmStatic
		fun observePacketHandlerTail(packet: Packet<*>) {
			instance?.withObservation { epoch -> handlePacketHandlerTail(packet, epoch) }
		}
	}
}
