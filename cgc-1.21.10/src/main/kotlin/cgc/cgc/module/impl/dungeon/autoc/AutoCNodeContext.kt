package cgc.cgc.module.impl.dungeon.autoc

import cgc.cgc.data.DungeonClass
import cgc.cgc.data.Pos
import cgc.cgc.module.impl.dungeon.autoc.nodes.RecordFrame
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3

interface AutoCNodeContext {
	fun smoothLook(yaw: Float, pitch: Float)

	fun smoothLookAtBlock(yaw: Float, pitch: Float, block: BlockPos)

	fun startWalk(yaw: Float, pitch: Float, activeForSeconds: Double = 0.0)

	fun startStrafe(direction: AutoCStrafeDirection, activeForSeconds: Double = 0.0)

	fun warp(yaw: Float, pitch: Float)

	fun etherwarp(yaw: Float, pitch: Float, block: BlockPos, target: Vec3, exactlyPos: Boolean)
	fun movingEtherwarp(yaw: Float, pitch: Float, block: BlockPos, target: Vec3)

	fun interact(yaw: Float, pitch: Float, block: BlockPos, hit: Vec3, await: Boolean)

	fun useItem(yaw: Float, pitch: Float, skyBlockId: String, itemId: String, displayName: String)

	fun bonzo(yaw: Float, pitch: Float)

	fun crouch(seconds: Double)

	fun startWait(seconds: Double)

	fun startTrack(seconds: Double, block: BlockPos)

	fun runCommand(command: String)

	fun jump()

	fun edge()

	fun leap(clazz: DungeonClass)

	fun stopActions(except: Set<String> = emptySet())

	fun breakBlocks(blocks: List<Pos>, zeroTick: Boolean, notMoving: Boolean): Boolean

	fun playRecording(frames: List<RecordFrame>): Boolean
}
