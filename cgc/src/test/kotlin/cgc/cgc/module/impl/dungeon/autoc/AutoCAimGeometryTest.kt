package cgc.cgc.module.impl.dungeon.autoc

import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AutoCAimGeometryTest {
	@Test
	fun `vertical target preserves current yaw`() {
		val rotation = AutoCNodeUtils.rotationTo(Vec3(1.0, 2.0, 3.0), Vec3(1.0, 8.0, 3.0), 137.0f)

		assertEquals(137.0f, rotation.yaw, 0.0001f)
		assertEquals(-90.0f, rotation.pitch, 0.0001f)
	}

	@Test
	fun `horizontal target still calculates required yaw`() {
		val rotation = AutoCNodeUtils.rotationTo(Vec3.ZERO, Vec3(1.0, 0.0, 0.0), 137.0f)

		assertEquals(-90.0f, rotation.yaw, 0.0001f)
	}

	@Test
	fun `etherwarp correction increases linearly with range`() {
		assertEquals(0.0, etherwarpCorrectionScale(8.0), 0.0001)
		assertEquals(0.5, etherwarpCorrectionScale(28.0), 0.0001)
		assertEquals(1.0, etherwarpCorrectionScale(48.0), 0.0001)
		assertEquals(1.0, etherwarpCorrectionScale(61.0), 0.0001)
	}
}
