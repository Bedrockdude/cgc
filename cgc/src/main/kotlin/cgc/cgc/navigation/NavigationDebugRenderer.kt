package cgc.cgc.navigation

import cgc.cgc.data.Colour
import cgc.cgc.runtime.CgcRenderer3D
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

object NavigationDebugRenderer {
	fun enqueue() {
		val state = NavigationService.status()
		val route = state.route ?: return
		for ((index, action) in route.actions.withIndex()) {
			val colour = when (action.type) {
				NavigationActionType.WALK, NavigationActionType.ASCEND, NavigationActionType.DESCEND -> WALK
				NavigationActionType.ETHERWARP -> ETHERWARP
			}
			val from = Vec3(action.start.x + .5, action.start.y + .15, action.start.z + .5)
			val to = Vec3(action.end.x + .5, action.end.y + .15, action.end.z + .5)
			CgcRenderer3D.lineList(listOf(from, to), colour, colour, depth = false)
			if (index == state.actionIndex) {
				CgcRenderer3D.outlineBox(AABB.ofSize(to.add(0.0, 0.9, 0.0), 0.8, 1.8, 0.8), ACTIVE, depth = false)
				CgcRenderer3D.worldText(action.type.name, to.add(0.0, 2.0, 0.0), ACTIVE, depth = false)
			}
		}
	}

	private val WALK = Colour(80, 220, 110)
	private val ETHERWARP = Colour(205, 90, 255)
	private val ACTIVE = Colour(255, 225, 70)
}
