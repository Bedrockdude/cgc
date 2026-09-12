package cgc.cgc.module.impl.dungeon.autopuzzles

import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult

object AutoPuzzleInteraction {
	fun useHeldItem(context: AutoPuzzleContext): Boolean {
		val gameMode = context.client.gameMode ?: return false
		gameMode.useItem(context.player, InteractionHand.MAIN_HAND)
		context.player.swing(InteractionHand.MAIN_HAND)
		return true
	}

	fun clickLever(context: AutoPuzzleContext, expected: BlockPos, reach: Double = 5.0): Boolean {
		if (!context.level.isLoaded(expected) || !context.level.getBlockState(expected).`is`(Blocks.LEVER)) return false
		val hit = context.player.pick(reach, 0.0f, false) as? BlockHitResult ?: return false
		if (hit.type != HitResult.Type.BLOCK || hit.blockPos != expected) return false
		val gameMode = context.client.gameMode ?: return false
		gameMode.useItemOn(context.player, InteractionHand.MAIN_HAND, hit)
		context.player.swing(InteractionHand.MAIN_HAND)
		return true
	}
}
