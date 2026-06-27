package cgc.cgc.runtime

import net.minecraft.network.protocol.Packet
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

	fun onTickStart() {
		execute(State.START)
	}

	fun onPacketReceive(packet: Packet<*>) {
		synchronized(receiveListeners) {
			receiveListeners.removeIf { it.test(packet) }
		}
	}

	fun onPacketSend(packet: Packet<*>) {
		val name = packet.javaClass.simpleName
		if (name.contains("UseItem", ignoreCase = true) || name.contains("Interact", ignoreCase = true)) {
			execute(State.ITEM_USE)
		}
		if (name.contains("PlayerAction", ignoreCase = true) || name.contains("Action", ignoreCase = true)) {
			execute(State.ATTACK)
		}
	}

	fun clear() {
		actions.clear()
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
}
