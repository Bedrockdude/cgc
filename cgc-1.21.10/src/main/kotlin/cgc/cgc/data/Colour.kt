package cgc.cgc.data

data class Colour(
	val red: Int,
	val green: Int,
	val blue: Int,
	val alpha: Int = 255
) {
	fun argb(): Int =
		((alpha and 255) shl 24) or ((red and 255) shl 16) or ((green and 255) shl 8) or (blue and 255)

	fun copyColour(): Colour =
		copy()
}
