package cgc.cgc.module.impl.dungeon

import cgc.cgc.data.Colour
import cgc.cgc.data.Keybind
import cgc.cgc.data.Phase7
import cgc.cgc.location.Floor
import cgc.cgc.location.Island
import cgc.cgc.location.Location
import cgc.cgc.module.BlockChangeModule
import cgc.cgc.module.CgcModule
import cgc.cgc.module.ChatMessageModule
import cgc.cgc.module.ClientTickModule
import cgc.cgc.module.HudRenderModule
import cgc.cgc.module.ModuleCategory
import cgc.cgc.module.WorldLoadModule
import cgc.cgc.module.WorldRenderExtractModule
import cgc.cgc.module.setting.BooleanSetting
import cgc.cgc.module.setting.ColourSetting
import cgc.cgc.module.setting.KeybindSetting
import cgc.cgc.module.setting.NumberSetting
import cgc.cgc.runtime.CgcRenderPrimitives
import cgc.cgc.utils.DungeonUtils
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.ButtonBlock
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.BooleanOp
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.max
import kotlin.math.sqrt

class SSTriggerBot : CgcModule(
	id = "SSTriggerBot",
	displayName = "SS Trigger-bot",
	category = ModuleCategory.DUNGEONS,
	description = "Clicks spectator safe Simon Says buttons when you hover the correct one.",
	defaultEnabled = false
), ClientTickModule, WorldRenderExtractModule, HudRenderModule, WorldLoadModule, ChatMessageModule, BlockChangeModule {
	private val resetKey = KeybindSetting("Reset Key", Keybind(action = this::SSR))
	private val autoStart = BooleanSetting("Auto start", true)
	private val forceSkyblock = BooleanSetting("Force Skyblock", false)
	private val autoStartDelay = NumberSetting("Auto start delay (MS)", 10.0, 500.0, 120.0, 10.0)
	private val aimTolerance = NumberSetting("Aim Tolerance", 0.0, 1.0, 0.08, 0.01)
	private val donePopup = BooleanSetting("Done Popup", true)
	private val firstButtonColor = ColourSetting("First Button Color", Colour(85, 255, 85))
	private val secondButtonColor = ColourSetting("Second Button Color", Colour(255, 213, 79))
	private val restButtonColor = ColourSetting("Rest Button Color", Colour(80, 190, 255))

	private val clicks = arrayListOf<BlockPos>()

	private var lastClickTime = System.currentTimeMillis()
	private var currentClickDelayMs = randomClickDelayMs()
	private var state = 0
	private var doneFirst = false
	private var doingSS = false
	private var startClicksRemaining = 0
	private var nextStartClickAt = 0L
	private var patternSettledAt = 0L
	private var donePopupUntil = 0L

	init {
		registerProperty(
			resetKey,
			autoStart,
			forceSkyblock,
			autoStartDelay,
			aimTolerance,
			donePopup,
			firstButtonColor,
			secondButtonColor,
			restButtonColor
		)
	}

	override fun onClientTick(client: Minecraft) {
		val player = client.player
		val level = client.level
		if (!areaCheck() || player == null || level == null) {
			resetState()
			return
		}

		if (!doingSS || player.distanceToSqr(START_BUTTON) > 25.0) {
			return
		}

		val now = System.currentTimeMillis()
		if (startClicksRemaining > 0) {
			if (now >= nextStartClickAt && clickButton(client, startButtonPos(), requireAimTolerance = false)) {
				startClicksRemaining = max(0, startClicksRemaining - 1)
				nextStartClickAt = if (startClicksRemaining > 0) {
					System.currentTimeMillis() + randomStartClickDelayMs()
				} else {
					0L
				}
			}
			return
		}

		if (now - lastClickTime < currentClickDelayMs) {
			return
		}

		if (!level.getBlockState(DETECT).`is`(Blocks.STONE_BUTTON)) {
			return
		}

		if (now < patternSettledAt) {
			return
		}

		if (!doneFirst && clicks.size == 3) {
			clicks.removeAt(0)
		}

		doneFirst = true
		if (state >= clicks.size) {
			return
		}

		val next = clicks[state]
		if (level.getBlockState(next).`is`(Blocks.STONE_BUTTON) && clickButton(client, next, requireAimTolerance = true)) {
			state++
			if (state >= clicks.size && clicks.size >= FINAL_SEQUENCE_LENGTH) {
				finishSimonSays()
			}
		}
	}

	override fun onWorldRenderExtract(context: LevelRenderContext) {
		val client = Minecraft.getInstance()
		if (!areaCheck() || client.player == null || client.level == null) {
			return
		}

		if (!doingSS) {
			return
		}

		if (startClicksRemaining > 0) {
			val startButton = startButtonPos()
			if (client.level!!.getBlockState(startButton).`is`(Blocks.STONE_BUTTON)) {
				renderButtonWireframe(context, client.level!!, startButton, firstButtonColor.value)
			}
		}

		clicks.forEachIndexed { index, button ->
			if (client.level!!.getBlockState(button).`is`(Blocks.STONE_BUTTON)) {
				renderButtonWireframe(context, client.level!!, button, getButtonColor(index))
			}
		}
	}

	override fun onHudRender(gfx: GuiGraphicsExtractor) {
		if (!donePopup.value || System.currentTimeMillis() > donePopupUntil) {
			return
		}

		val client = Minecraft.getInstance()
		val text = "SS Done"
		val centerX = gfx.guiWidth() / 2
		val centerY = gfx.guiHeight() / 2
		val textWidth = client.font.width(text)
		val boxPaddingX = 12
		val boxPaddingY = 7
		gfx.fill(
			centerX - textWidth / 2 - boxPaddingX,
			centerY - client.font.lineHeight / 2 - boxPaddingY,
			centerX + textWidth / 2 + boxPaddingX,
			centerY + client.font.lineHeight / 2 + boxPaddingY,
			0xAA000000.toInt()
		)
		gfx.centeredText(client.font, text, centerX, centerY - client.font.lineHeight / 2, 0xFF55FF55.toInt())
	}

	override fun onWorldLoad() {
		resetState()
	}

	override fun onChatMessage(message: String) {
		if (areaCheck() && autoStart.value && message == "[BOSS] Goldor: Who dares trespass into my domain?") {
			start()
		}
	}

	override fun onBlockChange(pos: BlockPos, oldState: BlockState?, newState: BlockState) {
		if (!doingSS
			|| startClicksRemaining > 0
			|| !areaCheck()
			|| !newState.`is`(Blocks.SEA_LANTERN)
		) {
			return
		}

		if (pos.x == 111 && pos.y >= 120 && pos.y <= 123 && pos.z >= 92 && pos.z <= 95) {
			val button = BlockPos(110, pos.y, pos.z)
			markPatternChanged()
			if (clicks.size == 2 && clicks.first() == button && !doneFirst) {
				doneFirst = true
				clicks.removeAt(0)
			}

			if (!clicks.contains(button)) {
				state = 0
				clicks.add(button)
			}
		}
	}

	fun SSR() {
		if (areaCheck()) {
			start()
		}
	}

	override fun onEnable() {
		resetState()
		resetKey.register()
	}

	override fun onDisable() {
		resetKey.unregister()
		resetState()
	}

	override fun reset() {
		resetState()
	}

	private fun start() {
		val client = Minecraft.getInstance()
		val player = client.player
		val level = client.level
		if (player == null
			|| level == null
			|| player.distanceToSqr(START_BUTTON) > 25.0
			|| !level.getBlockState(startButtonPos()).`is`(Blocks.STONE_BUTTON)
			|| !isCrosshairOn(client, startButtonPos())
		) {
			return
		}

		chat("Starting spectator safe SS trigger-bot!")
		resetState()
		doingSS = true
		startClicksRemaining = 3
		nextStartClickAt = System.currentTimeMillis() + randomAutoStartDelayMs()
	}

	private fun clickButton(client: Minecraft, button: BlockPos, requireAimTolerance: Boolean): Boolean {
		val player = client.player ?: return false
		val gameMode = client.gameMode ?: return false
		if (player.distanceToSqr(Vec3.atCenterOf(button)) > MAX_BUTTON_DISTANCE_SQ) {
			return false
		}

		val hit = getHoverHit(client, button, requireAimTolerance) ?: return false
		lastClickTime = System.currentTimeMillis()
		currentClickDelayMs = randomClickDelayMs()
		gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit)
		player.swing(InteractionHand.MAIN_HAND)
		return true
	}

	private fun getHoverHit(client: Minecraft, expected: BlockPos, requireAimTolerance: Boolean): BlockHitResult? {
		val hit = client.hitResult as? BlockHitResult ?: return null
		if (hit.type == HitResult.Type.MISS || hit.blockPos != expected) {
			return null
		}

		if (requireAimTolerance) {
			val level = client.level ?: return null
			if (!isHitWithinAimTolerance(level, expected, hit.location)) {
				return null
			}
		}

		return hit
	}

	private fun isCrosshairOn(client: Minecraft, expected: BlockPos): Boolean =
		getHoverHit(client, expected, requireAimTolerance = false) != null

	private fun isHitWithinAimTolerance(level: ClientLevel, pos: BlockPos, hitPoint: Vec3): Boolean {
		val state = level.getBlockState(pos)
		val shape = state.getShape(level, pos)
		val box = if (shape.isEmpty) AABB(pos) else shape.bounds().move(pos)
		val center = box.center
		val tolerance = getDouble(aimTolerance)
		val xDepth = box.xsize <= box.ysize && box.xsize <= box.zsize
		val yDepth = box.ysize < box.xsize && box.ysize <= box.zsize
		val dx = if (xDepth) 0.0 else hitPoint.x - center.x
		val dy = if (yDepth) 0.0 else hitPoint.y - center.y
		val dz = if (!xDepth && !yDepth) 0.0 else hitPoint.z - center.z
		return sqrt(dx * dx + dy * dy + dz * dz) <= tolerance
	}

	private fun getButtonColor(index: Int): Colour =
		when (index) {
			0 -> firstButtonColor.value
			1 -> secondButtonColor.value
			else -> restButtonColor.value
		}

	private fun renderButtonWireframe(context: LevelRenderContext, level: ClientLevel, pos: BlockPos, color: Colour) {
		val state = level.getBlockState(pos)
		val shape: VoxelShape = getVanillaButtonShape(state) ?: return
		if (!shape.isEmpty) {
			val camera = Minecraft.getInstance().gameRenderer.mainCamera.position()
			val box = shape.bounds().move(pos)
			val matrices = context.poseStack()
			matrices.pushPose()
			matrices.translate(-camera.x, -camera.y, -camera.z)
			CgcRenderPrimitives.lineBox(
				matrices,
				context.bufferSource().getBuffer(RenderTypes.lines()),
				box,
				color.red / 255.0f,
				color.green / 255.0f,
				color.blue / 255.0f,
				color.alpha / 255.0f
			)
			matrices.popPose()
		}
	}

	private fun getVanillaButtonShape(state: BlockState): VoxelShape? {
		val baseShape = VANILLA_BUTTON_BASE_SHAPES[state.getValue(FaceAttachedHorizontalDirectionalBlock.FACE)]
			?.get(state.getValue(HorizontalDirectionalBlock.FACING))
			?: return null
		val cutout = if (state.getValue(ButtonBlock.POWERED)) POWERED_BUTTON_CUTOUT else UNPOWERED_BUTTON_CUTOUT
		return Shapes.join(baseShape, cutout, BooleanOp.ONLY_FIRST)
	}

	private fun markPatternChanged() {
		patternSettledAt = System.currentTimeMillis() + randomClickDelayMs()
	}

	private fun areaCheck(): Boolean =
		forceSkyblock.value ||
			Location.area.isArea(Island.DUNGEON) &&
			(Location.floor == Floor.F7 || Location.floor == Floor.M7) &&
			DungeonUtils.isPhase(Phase7.P3)

	private fun resetState() {
		clicks.clear()
		startClicksRemaining = 0
		nextStartClickAt = 0L
		patternSettledAt = 0L
		state = 0
		doneFirst = false
		doingSS = false
		donePopupUntil = 0L
	}

	private fun finishSimonSays() {
		doingSS = false
		donePopupUntil = System.currentTimeMillis() + DONE_POPUP_MS
	}

	private fun getLong(setting: NumberSetting): Long =
		setting.value.toLong()

	private fun getDouble(setting: NumberSetting): Double =
		setting.value.toDouble()

	private fun randomAutoStartDelayMs(): Long {
		val baseDelay = getLong(autoStartDelay)
		return ThreadLocalRandom.current().nextLong(baseDelay, baseDelay + AUTO_START_DELAY_RANDOM_EXTRA_MS + 1L)
	}

	private fun chat(message: String) {
		Minecraft.getInstance().player?.sendSystemMessage(Component.literal(message))
	}

	private companion object {
		private val START_BUTTON = Vec3(110.875, 121.5, 91.5)
		private val DETECT = BlockPos(110, 123, 92)
		private const val MAX_BUTTON_DISTANCE_SQ = 36.0
		private const val FINAL_SEQUENCE_LENGTH = 5
		private const val DONE_POPUP_MS = 1500L
		private const val MIN_CLICK_DELAY_MS = 25L
		private const val MAX_CLICK_DELAY_MS = 34L
		private const val MIN_START_CLICK_DELAY_MS = 105L
		private const val MAX_START_CLICK_DELAY_MS = 130L
		private const val AUTO_START_DELAY_RANDOM_EXTRA_MS = 40L
		private val VANILLA_BUTTON_BASE_SHAPES = Shapes.rotateAttachFace(Block.boxZ(6.0, 4.0, 8.0, 16.0))
		private val POWERED_BUTTON_CUTOUT = Block.cube(14.0)
		private val UNPOWERED_BUTTON_CUTOUT = Block.cube(12.0)

		private fun startButtonPos(): BlockPos =
			BlockPos.containing(START_BUTTON.x, START_BUTTON.y, START_BUTTON.z)

		private fun randomClickDelayMs(): Long =
			ThreadLocalRandom.current().nextLong(MIN_CLICK_DELAY_MS, MAX_CLICK_DELAY_MS + 1L)

		private fun randomStartClickDelayMs(): Long =
			ThreadLocalRandom.current().nextLong(MIN_START_CLICK_DELAY_MS, MAX_START_CLICK_DELAY_MS + 1L)
	}
}
