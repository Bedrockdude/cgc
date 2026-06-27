package cgc.cgc.module.impl.movement

import cgc.cgc.data.Colour
import cgc.cgc.dungeon.DungeonState
import cgc.cgc.location.Floor
import cgc.cgc.location.Island
import cgc.cgc.location.Location
import cgc.cgc.module.CgcModule
import cgc.cgc.module.ClientTickModule
import cgc.cgc.module.ModuleCategory
import cgc.cgc.module.PacketPostReceiveModule
import cgc.cgc.module.PacketSendModule
import cgc.cgc.module.SubModule
import cgc.cgc.module.WorldLoadModule
import cgc.cgc.module.WorldRenderExtractModule
import cgc.cgc.module.setting.BooleanSetting
import cgc.cgc.module.setting.ColourSetting
import cgc.cgc.module.setting.ModeSetting
import cgc.cgc.module.setting.NumberSetting
import cgc.cgc.module.setting.group.GroupSetting
import cgc.cgc.runtime.CgcRenderer3D
import cgc.cgc.utils.ItemUtils
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

class Ether : CgcModule(
	id = "Ether",
	displayName = "Ether",
	category = ModuleCategory.MOVEMENT,
	description = "Etherwarp helper rendering and selected teleport no-rotate behavior.",
	defaultEnabled = false
), ClientTickModule, WorldRenderExtractModule, PacketSendModule, PacketPostReceiveModule, WorldLoadModule {
	private val singleplayer = BooleanSetting("Singleplayer", false)
	private val helper = BooleanSetting("Helper Enabled", false)
	private val correctColour = ColourSetting("Correct", Colour(0, 255, 0, 90))
	private val correctOutline = ColourSetting("Correct Outline", Colour(0, 255, 0))
	private val failColour = ColourSetting("Fail", Colour(255, 0, 0, 90))
	private val failOutline = ColourSetting("Fail Outline", Colour(255, 0, 0))
	private val renderMode = ModeSetting("Render Mode", "Filled Outline", listOf("Outline", "Filled Outline", "Filled"))
	private val depth = BooleanSetting("Depth", true)
	private val serverPosition = BooleanSetting("Server Position", true)
	private val fullBlock = BooleanSetting("Full Block", false)
	private val alwaysShow = BooleanSetting("Show While Unsneaked", false)
	private val noRotate = BooleanSetting("No Rotate Enabled", false)
	private val teleportItem = BooleanSetting("Teleport Items", true)
	private val outbounds = BooleanSetting("Outbounds", false)
	private val alwaysNoRotate = BooleanSetting("Always No Rotate", false)
	private val noRotateFromPackets = BooleanSetting("From Packets", false)
	private val timeout = NumberSetting("Timeout", 250.0, 2000.0, 1000.0, 25.0, " ms")
	private val zpew = BooleanSetting("Zpew Etherwarp", false)
	private val zptp = BooleanSetting("Zpew Teleport", false)
	private val zpInteract = BooleanSetting("Zero Ping Interact", false)
	private val assumeCancelInteract = BooleanSetting("Assume Cancel Interact", false)
	private val helperGroup = GroupSetting("Helper", SubModule(this, "Helper", true))
	private val noRotateGroup = GroupSetting("No Rotate", SubModule(this, "No Rotate", true))
	private val zpewGroup = GroupSetting("Zpew", SubModule(this, "Zpew", true))

	private val noRotateSent = arrayListOf<NoRotateRecord>()

	init {
		helperGroup.add(
			helper,
			correctColour,
			correctOutline,
			failColour,
			failOutline,
			renderMode,
			depth,
			serverPosition,
			fullBlock,
			alwaysShow
		)
		noRotateGroup.add(
			noRotate,
			teleportItem,
			outbounds,
			alwaysNoRotate,
			noRotateFromPackets,
			timeout
		)
		zpewGroup.add(
			zpew,
			zptp,
			zpInteract,
			assumeCancelInteract
		)
		registerProperty(
			singleplayer,
			helperGroup,
			noRotateGroup,
			zpewGroup
		)
	}

	override fun onClientTick(client: Minecraft) {
		val now = System.currentTimeMillis()
		noRotateSent.removeIf { now - it.time >= timeout.value.toLong() }
	}

	override fun onWorldRenderExtract(context: LevelRenderContext) {
		val client = Minecraft.getInstance()
		val player = client.player ?: return
		client.level ?: return
		if (client.screen != null || !helper.value || (!player.isShiftKeyDown && !alwaysShow.value)) {
			return
		}

		val held = player.mainHandItem
		if (!ItemUtils.isEtherwarp(held)) {
			return
		}

		val origin = player.eyePosition
		val distance = 57 + ItemUtils.tunerDistance(held)
		val target = findEtherTarget(origin, player.lookAngle, distance) ?: return
		val canTeleport = canStandAt(target.above())
		val fillColour = if (canTeleport) correctColour.value else failColour.value
		val outlineColour = if (canTeleport) correctOutline.value else failOutline.value
		renderTarget(target, fillColour, outlineColour)
	}

	override fun onPacketSend(packet: Packet<*>): Boolean {
		if (!noRotate.value || !teleportItem.value) {
			return false
		}

		val client = Minecraft.getInstance()
		val player = client.player ?: return false
		if (DungeonState.inBoss && (Location.floor == Floor.F7 || Location.floor == Floor.M7)) {
			return false
		}

		val hand = when (packet) {
			is ServerboundUseItemPacket -> packet.hand
			is ServerboundUseItemOnPacket -> packet.hand
			else -> return false
		}

		if (!ItemUtils.isTeleportItem(player.getItemInHand(hand))) {
			return false
		}

		if (packet is ServerboundUseItemOnPacket) {
			val hit = packet.hitResult
			if (isIgnoredInteractBlock(hit.blockPos)) {
				return false
			}
		} else if (client.hitResult is BlockHitResult && isIgnoredInteractBlock((client.hitResult as BlockHitResult).blockPos)) {
			return false
		}

		noRotateSent.add(NoRotateRecord(System.currentTimeMillis(), player.yRot, player.xRot))
		return false
	}

	override fun onPacketPostReceive(packet: Packet<*>) {
		if (packet !is ClientboundPlayerPositionPacket || !noRotate.value || !shouldNoRotate()) {
			return
		}

		val player = Minecraft.getInstance().player ?: return
		val record = noRotateSent.removeFirstOrNull()
		if (record != null) {
			player.yRot = record.yaw
			player.xRot = record.pitch
		}
	}

	override fun onWorldLoad() {
		reset()
	}

	override fun reset() {
		noRotateSent.clear()
	}

	private fun shouldNoRotate(): Boolean {
		val now = System.currentTimeMillis()
		noRotateSent.removeIf { now - it.time >= timeout.value.toLong() }
		return alwaysNoRotate.value || noRotateSent.isNotEmpty() || (outbounds.value && !DungeonState.started && Location.area.isArea(Island.DUNGEON))
	}

	private fun findEtherTarget(origin: Vec3, look: Vec3, distance: Int): BlockPos? {
		val client = Minecraft.getInstance()
		val level = client.level ?: return null
		val player = client.player ?: return null
		val end = origin.add(look.normalize().scale(distance.toDouble()))
		val hit = level.clip(ClipContext(origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
		return if (hit.type == HitResult.Type.MISS) null else hit.blockPos
	}

	private fun canStandAt(pos: BlockPos): Boolean {
		val level = Minecraft.getInstance().level ?: return false
		val feet = level.getBlockState(pos)
		val head = level.getBlockState(pos.above())
		return feet.getCollisionShape(level, pos).isEmpty && head.getCollisionShape(level, pos.above()).isEmpty
	}

	private fun isIgnoredInteractBlock(pos: BlockPos): Boolean {
		val block = Minecraft.getInstance().level?.getBlockState(pos)?.block ?: return false
		if (assumeCancelInteract.value) {
			return block == Blocks.CHEST || block == Blocks.ENDER_CHEST || block == Blocks.TRAPPED_CHEST
		}
		return block == Blocks.CHEST
			|| block == Blocks.ENDER_CHEST
			|| block == Blocks.TRAPPED_CHEST
			|| block == Blocks.HOPPER
			|| block == Blocks.ANVIL
			|| block == Blocks.CHIPPED_ANVIL
			|| block == Blocks.DAMAGED_ANVIL
	}

	private fun renderTarget(pos: BlockPos, fillColour: Colour, outlineColour: Colour) {
		val aabb = AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0).move(pos)
		when {
			renderMode.isMode("Outline") -> CgcRenderer3D.outlineBox(aabb, outlineColour, depth.value)
			renderMode.isMode("Filled Outline") -> CgcRenderer3D.filledOutlineBox(aabb, fillColour, outlineColour, depth.value)
			else -> CgcRenderer3D.filledBox(aabb, fillColour, depth.value)
		}
	}

	private data class NoRotateRecord(val time: Long, val yaw: Float, val pitch: Float)
}
