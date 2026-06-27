package cgc.cgc.module.impl.dungeon

import cgc.cgc.data.Colour
import cgc.cgc.data.Keybind
import cgc.cgc.data.Pos
import cgc.cgc.dungeon.DungeonState
import cgc.cgc.location.Floor
import cgc.cgc.location.Island
import cgc.cgc.location.Location
import cgc.cgc.module.CgcModule
import cgc.cgc.module.ClientTickModule
import cgc.cgc.module.ModuleCategory
import cgc.cgc.module.PacketPostReceiveModule
import cgc.cgc.module.WorldLoadModule
import cgc.cgc.module.WorldRenderExtractModule
import cgc.cgc.module.setting.BooleanSetting
import cgc.cgc.module.setting.ColourSetting
import cgc.cgc.module.setting.KeybindSetting
import cgc.cgc.module.setting.NumberSetting
import cgc.cgc.module.setting.SaveSetting
import cgc.cgc.runtime.CgcRenderer3D
import cgc.cgc.utils.ChatUtils
import cgc.cgc.utils.ItemUtils
import com.google.gson.reflect.TypeToken
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

class BreakerAura : CgcModule(
	id = "BreakerAura",
	displayName = "Breaker Aura",
	category = ModuleCategory.DUNGEONS,
	description = "Mines configured Dungeonbreaker aura blocks in F7/M7 boss.",
	defaultEnabled = false
), ClientTickModule, WorldRenderExtractModule, PacketPostReceiveModule, WorldLoadModule {
	private val edit = BooleanSetting("Edit Mode", false)
	private val addBlockBind = KeybindSetting("Add Block Bind", Keybind(action = this::addOrRemoveBlock))
	private val swap = BooleanSetting("Auto Swap", true)
	private val renderBlocks = BooleanSetting("Render Blocks", true)
	private val colour = ColourSetting("Colour", Colour(255, 255, 0, 170))
	private val zeroTick = BooleanSetting("Zero Tick", false)
	private val timeout = NumberSetting("Timeout", 0.0, 1000.0, 500.0, 10.0, " ms")
	private val data = SaveSetting(
		name = "Aura Blocks",
		path = "dungeon/breaker",
		defaultFile = "breaker_aura.json",
		factory = { linkedSetOf<Pos>() },
		valueType = object : TypeToken<LinkedHashSet<Pos>>() {}.type,
		allowEdits = true
	)

	private var charges = 20
	private val nextMineAttempt = hashMapOf<Pos, Long>()

	init {
		registerProperty(edit, addBlockBind, swap, renderBlocks, colour, zeroTick, timeout, data)
	}

	override fun onEnable() {
		addBlockBind.register()
	}

	override fun onDisable() {
		addBlockBind.unregister()
		reset()
	}

	override fun onClientTick(client: Minecraft) {
		val player = client.player ?: return
		val level = client.level ?: return
		if (!areaCheck()
			|| data.value.isEmpty()
			|| edit.value
			|| charges <= 0
		) {
			return
		}

		val now = System.currentTimeMillis()
		if (zeroTick.value) {
			val targets = data.value.filter { canMine(level.getBlockState(it.asBlockPos()), it, now) }
			if (targets.isEmpty() || !ensureDungeonBreakerHeld()) {
				return
			}

			for (pos in targets) {
				markMineAttempt(pos, now)
				breakBlock(pos)
				charges--
				if (charges <= 0) {
					return
				}
			}
		} else {
			val closest = data.value
				.filter { canMine(level.getBlockState(it.asBlockPos()), it, now) }
				.minByOrNull { it.asVec3().distanceTo(player.eyePosition) }
				?: return

			if (ensureDungeonBreakerHeld()) {
				markMineAttempt(closest, now)
				breakBlock(closest)
				charges--
			}
		}
	}

	override fun onWorldRenderExtract(context: LevelRenderContext) {
		val client = Minecraft.getInstance()
		val level = client.level ?: return
		if (!areaCheck() || !renderBlocks.value || data.value.isEmpty()) {
			return
		}

		for (pos in data.value) {
			val bp = pos.asBlockPos()
			val state = level.getBlockState(bp)
			val shape = state.getShape(level, bp)
			if (!shape.isEmpty) {
				CgcRenderer3D.outlineBox(shape.bounds().move(bp), colour.value, depth = true)
			}
		}
	}

	override fun onPacketPostReceive(packet: Packet<*>) {
		if (packet is ClientboundContainerSetSlotPacket
			&& Location.area.isArea(Island.DUNGEON)
			&& ItemUtils.skyBlockId(packet.item) == DUNGEONBREAKER_ID
		) {
			charges = ItemUtils.dungeonBreakerCharges(packet.item).first
		}
	}

	override fun onWorldLoad() {
		reset()
	}

	override fun reset() {
		charges = 20
		nextMineAttempt.clear()
	}

	fun addOrRemoveBlock() {
		if (!areaCheck()) {
			return
		}

		val hit = Minecraft.getInstance().hitResult
		if (hit !is BlockHitResult || hit.type == HitResult.Type.MISS) {
			ChatUtils.chat("${ChatFormatting.RED}Not looking at a block")
			return
		}

		val pos = Pos(hit.blockPos)
		if (data.value.contains(pos)) {
			data.value.remove(pos)
			nextMineAttempt.remove(pos)
			ChatUtils.chat("${ChatFormatting.RED}Removed ${pos.toChatString()}")
		} else {
			data.value.add(pos)
			ChatUtils.chat("${ChatFormatting.GREEN}Added ${pos.toChatString()}")
		}

		data.save()
	}

	private fun areaCheck(): Boolean {
		val server = Minecraft.getInstance().currentServer
		val p3Sim = server != null && server.ip.equals("hypixelp3sim.zapto.org", ignoreCase = true)
		return p3Sim || (Location.area.isArea(Island.DUNGEON)
			&& DungeonState.inBoss
			&& (Location.floor == Floor.M7 || Location.floor == Floor.F7))
	}

	private fun canMine(state: BlockState, pos: Pos, now: Long): Boolean {
		val level = Minecraft.getInstance().level ?: return false
		val bp = pos.asBlockPos()
		if (state.getShape(level, bp).isEmpty || !DungeonBreaker.canInstantMine(state) || !canRetryMine(pos, now)) {
			return false
		}

		val player = Minecraft.getInstance().player ?: return false
		return faceDistance(pos.asVec3(), player.eyePosition) <= BLOCK_RANGE_SQ
	}

	private fun ensureDungeonBreakerHeld(): Boolean {
		val player = Minecraft.getInstance().player ?: return false
		if (ItemUtils.skyBlockId(player.inventory.selectedItem) == DUNGEONBREAKER_ID) {
			return true
		}

		if (!swap.value) {
			return false
		}

		for (slot in 0..8) {
			if (ItemUtils.skyBlockId(player.inventory.getItem(slot)) == DUNGEONBREAKER_ID) {
				player.inventory.selectedSlot = slot
				return true
			}
		}

		return false
	}

	private fun breakBlock(pos: Pos) {
		val client = Minecraft.getInstance()
		val player = client.player ?: return
		val gameMode = client.gameMode ?: return
		val direction = closestFace(pos.asVec3(), player.eyePosition)
		gameMode.startDestroyBlock(pos.asBlockPos(), direction)
		player.swing(InteractionHand.MAIN_HAND)
	}

	private fun canRetryMine(pos: Pos, now: Long): Boolean =
		nextMineAttempt.getOrDefault(pos, 0L) <= now

	private fun markMineAttempt(pos: Pos, now: Long) {
		nextMineAttempt[pos] = now + timeout.value.toLong()
	}

	private fun faceDistance(pos: Vec3, player: Vec3): Double {
		var minDist = Double.MAX_VALUE
		for (face in Direction.entries) {
			val faceVec = getFaceVec(face, pos)
			val dist = player.distanceToSqr(faceVec)
			if (dist < minDist) {
				minDist = dist
			}
		}
		return minDist
	}

	private fun closestFace(pos: Vec3, player: Vec3): Direction {
		var minDist = Double.MAX_VALUE
		var closest = Direction.UP
		for (face in Direction.entries) {
			val faceVec = getFaceVec(face, pos)
			val dist = player.distanceToSqr(faceVec)
			if (dist < minDist) {
				minDist = dist
				closest = face
			}
		}
		return closest
	}

	private fun getFaceVec(direction: Direction, pos: Vec3): Vec3 {
		val offset = when (direction) {
			Direction.DOWN -> Vec3(0.5, 0.0, 0.5)
			Direction.UP -> Vec3(0.5, 1.0, 0.5)
			Direction.NORTH -> Vec3(0.5, 0.5, 0.0)
			Direction.SOUTH -> Vec3(0.5, 0.5, 1.0)
			Direction.WEST -> Vec3(0.0, 0.5, 0.5)
			Direction.EAST -> Vec3(1.0, 0.5, 0.5)
		}
		return pos.add(offset)
	}

	private companion object {
		private const val DUNGEONBREAKER_ID = "DUNGEONBREAKER"
		private const val BLOCK_RANGE_SQ = 25.0
	}
}
