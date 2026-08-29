package cgc.cgc.client

import com.mojang.brigadier.CommandDispatcher
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import kotlin.test.Test
import kotlin.test.assertNull

class CgcCommandRegistryTest {
	@Test
	fun `both command paths hide the retired termcal command`() {
		val local = CgcCommandRegistry.getDispatcher().root.getChild("cgc")?.getChild("termcal")
		assertNull(local)

		val fabric = CommandDispatcher<FabricClientCommandSource>()
		CgcCommandRegistry.registerFabricCommands(fabric, openConfig = {}, openUi = {})
		val fabricTermcal = fabric.root.getChild("cgc")?.getChild("termcal")
		assertNull(fabricTermcal)
	}
}
