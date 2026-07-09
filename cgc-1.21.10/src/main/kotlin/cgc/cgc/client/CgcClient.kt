package cgc.cgc.client

import cgc.cgc.client.gui.CgcConfigScreen
import cgc.cgc.client.gui.CgcUiScreen
import cgc.cgc.config.CgcConfigStore
import cgc.cgc.module.CgcModules
import cgc.cgc.runtime.CgcRenderer3D
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.gui.screens.Screen
import net.minecraft.resources.Identifier

object CgcClient : ClientModInitializer {
	private var pendingScreenDelayTicks = -1
	private var pendingScreen: (() -> Screen)? = null

	override fun onInitializeClient() {
		CgcModules.bootstrap()
		CgcConfigStore.loadAll()
		Runtime.getRuntime().addShutdownHook(Thread { CgcConfigStore.saveAll() })

		ClientTickEvents.START_CLIENT_TICK.register { client ->
			CgcModules.clientTickStart(client)
		}

		ClientTickEvents.END_CLIENT_TICK.register { client ->
			CgcModules.clientTick(client)

			if (pendingScreenDelayTicks < 0) return@register
			if (pendingScreenDelayTicks > 0) {
				pendingScreenDelayTicks--
				return@register
			}

			pendingScreenDelayTicks = -1
			pendingScreen?.let { client.setScreen(it()) }
			pendingScreen = null
		}

		ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
			CgcModules.worldLoad()
		}

		ClientSendMessageEvents.ALLOW_CHAT.register { message ->
			CgcCommandRegistry.handlePrefixedChat(message)
		}

		LevelRenderEvents.START_MAIN.register { _ ->
			CgcModules.worldRenderStart()
		}

		LevelRenderEvents.END_MAIN.register { context ->
			CgcModules.worldRenderExtract(context)
			CgcRenderer3D.render(context)
		}

		HudElementRegistry.attachElementBefore(VanillaHudElements.SLEEP, Identifier.fromNamespaceAndPath("cgc", "hud")) { gfx, _ ->
			CgcModules.hudRender(gfx)
		}

		ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
			CgcCommandRegistry.registerFabricCommands(
				dispatcher,
				openConfig = { openScreenLater { CgcConfigScreen() } },
				openUi = { openScreenLater { CgcUiScreen() } }
			)
		}
	}

	fun openScreenLater(factory: () -> Screen) {
		pendingScreenDelayTicks = 2
		pendingScreen = factory
	}
}
