package cgc.cgc.runtime

data class InputCommand(
	var ticks: Int,
	val yaw: Float? = null,
	val pitch: Float? = null,
	val forward: Boolean = false,
	val back: Boolean = false,
	val left: Boolean = false,
	val right: Boolean = false,
	val jump: Boolean = false,
	val sneak: Boolean = false,
	val sprint: Boolean = false
)
