package cgc.cgc.shitterlist

import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal class ShitterListRelay(
	private val repository: ShitterListRepository,
	private val acceptRemote: (ShitterListUpdate) -> Boolean
) {
	private val scheduler = Executors.newSingleThreadScheduledExecutor { task ->
		Thread(task, "CGC Shitter List Relay").apply { isDaemon = true }
	}
	private val client = MqttAsyncClient(
		BROKER_URI,
		"cgc-${UUID.randomUUID().toString().replace("-", "").take(18)}",
		MemoryPersistence()
	)
	private val started = AtomicBoolean(false)
	private val connecting = AtomicBoolean(false)
	@Volatile private var stopped = false
	@Volatile private var subscribed = false

	val connected: Boolean
		get() = client.isConnected && subscribed

	init {
		client.setCallback(object : MqttCallbackExtended {
			override fun connectComplete(reconnect: Boolean, serverURI: String?) {
				connecting.set(false)
				subscribe()
			}

			override fun connectionLost(cause: Throwable?) {
				connecting.set(false)
				subscribed = false
				scheduleConnectionCheck()
			}

			override fun messageArrived(topic: String, message: MqttMessage) {
				val update = ShitterListRelayCodec.decode(topic, message.payload) ?: return
				if (acceptRemote(update)) repository.applyRemote(update)
			}

			override fun deliveryComplete(token: org.eclipse.paho.client.mqttv3.IMqttDeliveryToken?) {
			}
		})
	}

	fun start() {
		if (!started.compareAndSet(false, true)) return
		connect()
		scheduler.scheduleAtFixedRate(
			{ if (client.isConnected) subscribe() else connect() },
			REFRESH_SECONDS,
			REFRESH_SECONDS,
			TimeUnit.SECONDS
		)
	}

	fun publish(update: ShitterListUpdate) {
		if (!client.isConnected) {
			connect()
			return
		}

		val message = MqttMessage(ShitterListRelayCodec.encode(update)).apply {
			qos = 1
			isRetained = true
		}
		runCatching {
			client.publish(ShitterListRelayCodec.topic(update), message, update, object : IMqttActionListener {
				override fun onSuccess(asyncActionToken: IMqttToken?) {
					repository.acknowledge(update)
					scheduleRefresh()
				}

				override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
					scheduleConnectionCheck()
				}
			})
		}.onFailure { scheduleConnectionCheck() }
	}

	fun shutdown() {
		stopped = true
		repository.saveNow()
		runCatching {
			if (client.isConnected) client.disconnectForcibly(500, 500, false)
		}
		runCatching { client.close() }
		scheduler.shutdownNow()
	}

	private fun connect() {
		if (stopped || client.isConnected || !connecting.compareAndSet(false, true)) return
		subscribed = false
		val options = MqttConnectOptions().apply {
			isAutomaticReconnect = true
			isCleanSession = true
			connectionTimeout = 8
			keepAliveInterval = 30
		}
		runCatching {
			client.connect(options, null, object : IMqttActionListener {
				override fun onSuccess(asyncActionToken: IMqttToken?) {
					connecting.set(false)
				}

				override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
					connecting.set(false)
					scheduleConnectionCheck()
				}
			})
		}.onFailure {
			connecting.set(false)
			scheduleConnectionCheck()
		}
	}

	private fun subscribe() {
		if (stopped || !client.isConnected) return
		runCatching {
			client.subscribe(ShitterListRelayCodec.topicFilter, 1, null, object : IMqttActionListener {
				override fun onSuccess(asyncActionToken: IMqttToken?) {
					subscribed = true
					repository.pendingUpdates().forEach(::publish)
				}

				override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
					subscribed = false
					scheduleConnectionCheck()
				}
			})
		}.onFailure { scheduleConnectionCheck() }
	}

	private fun scheduleConnectionCheck() {
		if (stopped) return
		runCatching {
			scheduler.schedule({ connect() }, RETRY_SECONDS, TimeUnit.SECONDS)
		}
	}

	private fun scheduleRefresh() {
		if (stopped) return
		runCatching {
			scheduler.schedule({ if (client.isConnected) subscribe() }, POST_PUBLISH_REFRESH_SECONDS, TimeUnit.SECONDS)
		}
	}

	private companion object {
		private const val BROKER_URI = "ssl://broker.emqx.io:8883"
		private const val RETRY_SECONDS = 30L
		private const val POST_PUBLISH_REFRESH_SECONDS = 1L
		private const val REFRESH_SECONDS = 600L
	}
}
