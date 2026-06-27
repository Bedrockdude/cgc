package cgc.cgc.module.impl.dungeon

import cgc.cgc.location.Island
import cgc.cgc.location.Location
import cgc.cgc.module.CgcModule
import cgc.cgc.module.ModuleCategory
import cgc.cgc.utils.ItemUtils
import net.minecraft.tags.BlockTags
import net.minecraft.tags.TagKey
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.BushBlock
import net.minecraft.world.level.block.CauldronBlock
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.level.block.HopperBlock
import net.minecraft.world.level.block.LeverBlock
import net.minecraft.world.level.block.RedstoneTorchBlock
import net.minecraft.world.level.block.SkullBlock
import net.minecraft.world.level.block.state.BlockState
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

class DungeonBreaker : CgcModule(
	id = "DungeonBreaker",
	displayName = "ZPDB",
	category = ModuleCategory.DUNGEONS,
	description = "Adjusts Dungeonbreaker mining speed for valid dungeon blocks.",
	defaultEnabled = false
) {
	init {
		instance = this
	}

	override fun reset() {
		charges = 20
	}

	companion object {
		private val BLACKLIST = listOf(
			Blocks.BARRIER,
			Blocks.COMMAND_BLOCK,
			Blocks.IRON_BLOCK,
			Blocks.BEDROCK,
			Blocks.PISTON,
			Blocks.PISTON_HEAD,
			Blocks.MOVING_PISTON,
			Blocks.STICKY_PISTON,
			Blocks.TNT,
			Blocks.END_PORTAL,
			Blocks.END_PORTAL_FRAME,
			Blocks.END_GATEWAY,
			Blocks.NETHER_PORTAL,
			Blocks.CHEST,
			Blocks.ENDER_CHEST,
			Blocks.TRAPPED_CHEST
		)
		private val TAGS: List<TagKey<Block>> = listOf(BlockTags.BUTTONS, BlockTags.COPPER_CHESTS)
		private val CLASSES = listOf(
			LeverBlock::class.java,
			RedstoneTorchBlock::class.java,
			BushBlock::class.java,
			CauldronBlock::class.java,
			SkullBlock::class.java,
			ChestBlock::class.java,
			HopperBlock::class.java,
			BaseEntityBlock::class.java
		)

		private var instance: DungeonBreaker? = null
		private var charges = 20

		@JvmStatic
		fun handleDigSpeed(state: BlockState, held: ItemStack, cir: CallbackInfoReturnable<Float>) {
			if (Location.area.isArea(Island.DUNGEON)
				&& ItemUtils.skyBlockId(held) == "DUNGEONBREAKER"
				&& instance?.enabled == true
			) {
				cir.returnValue = if (canInstantMine(state)) 1500.0f else 0.0f
			}
		}

		@JvmStatic
		fun canInstantMine(state: BlockState): Boolean =
			!BLACKLIST.contains(state.block)
				&& TAGS.none { state.`is`(it) }
				&& CLASSES.none { it.isInstance(state.block) }
	}
}
