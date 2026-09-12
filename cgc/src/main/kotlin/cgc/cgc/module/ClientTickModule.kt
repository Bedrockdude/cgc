package cgc.cgc.module

import net.minecraft.client.Minecraft

interface ClientTickStartModule {
	fun onClientTickStart(client: Minecraft)
}

interface ClientTickModule {
	fun onClientTick(client: Minecraft)
}
