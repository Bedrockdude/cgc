package cgc.cgc

import cgc.cgc.module.CgcModules
import net.fabricmc.api.ModInitializer
import net.minecraft.resources.Identifier
import org.slf4j.LoggerFactory

object Cgc : ModInitializer {
	const val MOD_ID: String = "cgc"

	private val LOGGER = LoggerFactory.getLogger(MOD_ID)

	override fun onInitialize() {
		CgcModules.bootstrap()
		LOGGER.info("Initialized CGC.")
	}

	fun id(path: String): Identifier
		= Identifier.fromNamespaceAndPath(MOD_ID, path)
}
