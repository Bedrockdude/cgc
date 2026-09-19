package cgc.cgc.runtime

import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Predicate

object PacketOrderManager {
	enum class State {
		START,
		ITEM_USE,
		ATTACK
	}

	private val actions = ConcurrentHashMap<State, MutableList<() -> Unit>>()
	private val receiveListeners = arrayListOf<Predicate<Packet<*>>>()
	private val protectedPackets = OneActionPerTickQueue<Packet<*>>()
	private var vanillaUseDepth = 0
	@Volatile
	private var flushingProtectedPacket: Packet<*>? = null

	fun register(state: State, action: () -> Unit) {
		val list = actions.computeIfAbsent(state) { mutableListOf() }
		synchronized(list) {
			list.add(action)
		}
	}

	fun registerReceiveListener(listener: Predicate<Packet<*>>) {
		synchronized(receiveListeners) {
			receiveListeners.add(listener)
		}
	}

	fun onTickStart(client: Minecraft) {
		flushNextProtectedPacket(client)
		execute(State.START)
	}

	fun onPacketReceive(packet: Packet<*>) {
		synchronized(receiveListeners) {
			receiveListeners.removeIf { it.test(packet) }
		}
	}

	fun onPacketSend(packet: Packet<*>): Boolean {
		val protectedAction = isProtectedAction(packet)
		if (protectedAction && packet !== flushingProtectedPacket) {
			if (vanillaUseDepth > 0) {
				protectedPackets.markUsed()
			} else if (protectedPackets.shouldDelay(packet)) {
				return true
			}
		}

		if (isItemUse(packet)) {
			execute(State.ITEM_USE)
		}
		val name = packet.javaClass.simpleName
		if (name.contains("PlayerAction", ignoreCase = true) || name.contains("Action", ignoreCase = true)) {
			execute(State.ATTACK)
		}
		return false
	}

	/**
	 * Runs [action] while atomically holding the current tick's immediate packet
	 * slot. The packet emitted by [action] re-enters [onPacketSend] on the same
	 * thread, where it consumes that slot normally. If the slot is already used,
	 * [action] is not run and no packet is created for the deferred queue.
	 */
	fun tryRunProtectedActionImmediately(action: () -> Unit): Boolean =
		protectedPackets.tryRunImmediately(action)

	fun beginVanillaUse() {
		vanillaUseDepth++
	}

	fun endVanillaUse() {
		vanillaUseDepth = (vanillaUseDepth - 1).coerceAtLeast(0)
	}

	fun clear() {
		actions.clear()
		protectedPackets.clear()
		vanillaUseDepth = 0
		flushingProtectedPacket = null
		synchronized(receiveListeners) {
			receiveListeners.clear()
		}
	}

	private fun execute(state: State) {
		val list = actions[state] ?: return
		val copy = synchronized(list) {
			if (list.isEmpty()) return
			val copy = list.toList()
			list.clear()
			copy
		}
		copy.forEach { it.invoke() }
	}

	private fun flushNextProtectedPacket(client: Minecraft) {
		val connection = client.connection?.connection
		protectedPackets.beginTick(connection != null)?.let { packet ->
			flushingProtectedPacket = packet
			try {
				connection?.send(packet)
			} finally {
				flushingProtectedPacket = null
			}
		}
	}

	private fun isProtectedAction(packet: Packet<*>): Boolean =
		packet is ServerboundSetCarriedItemPacket || isItemUse(packet)

	private fun isItemUse(packet: Packet<*>): Boolean =
		packet is ServerboundUseItemPacket || packet is ServerboundUseItemOnPacket
}

internal class OneActionPerTickQueue<T> {
	private val pending = ArrayDeque<T>()
	private var actionUsedThisTick = false

	@Synchronized
	fun shouldDelay(action: T): Boolean {
		if (!actionUsedThisTick) {
			actionUsedThisTick = true
			return false
		}

		pending.addLast(action)
		return true
	}

	@Synchronized
	fun tryRunImmediately(action: () -> Unit): Boolean {
		if (actionUsedThisTick) {
			return false
		}
		action()
		return true
	}

	@Synchronized
	fun markUsed() {
		actionUsedThisTick = true
	}

	@Synchronized
	fun beginTick(canFlush: Boolean = true): T? {
		actionUsedThisTick = false
		if (!canFlush || pending.isEmpty()) {
			return null
		}

		actionUsedThisTick = true
		return pending.removeFirst()
	}

	@Synchronized
	fun clear() {
		pending.clear()
		actionUsedThisTick = false
	}

	@Synchronized
	fun pendingCount(): Int = pending.size
}
