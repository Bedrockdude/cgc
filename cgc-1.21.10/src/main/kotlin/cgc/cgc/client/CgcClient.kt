package cgc.cgc.client

import cgc.cgc.client.gui.CgcConfigScreen
import cgc.cgc.config.CgcConfigStore
import cgc.cgc.module.CgcModules
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents
import net.minecraft.resources.ResourceLocation

object CgcClient : ClientModInitializer {
	private var openConfigDelayTicks = -1

	override fun onInitializeClient() {
		CgcModules.bootstrap()
		CgcConfigStore.loadAll()
		Runtime.getRuntime().addShutdownHook(Thread { CgcConfigStore.saveAll() })

		ClientTickEvents.END_CLIENT_TICK.register { client ->
			CgcModules.clientTick(client)

			if (openConfigDelayTicks < 0) return@register
			if (openConfigDelayTicks > 0) {
				openConfigDelayTicks--
				return@register
			}

			openConfigDelayTicks = -1
			if (client.screen !is CgcConfigScreen) {
				client.setScreen(CgcConfigScreen())
			}
		}

		ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register { _, _ ->
			CgcModules.worldLoad()
		}

		WorldRenderEvents.START_MAIN.register {
			CgcModules.worldRenderStart()
		}

		WorldRenderEvents.END_MAIN.register { context ->
			CgcModules.worldRenderExtract(context)
		}

		HudElementRegistry.attachElementBefore(VanillaHudElements.SLEEP, ResourceLocation.fromNamespaceAndPath("cgc", "hud")) { gfx, _ ->
			CgcModules.hudRender(gfx)
		}

		ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
			dispatcher.register(
				literal("cgc").executes {
					openConfigDelayTicks = 2
					1
				}
			)
		}
	}
}
