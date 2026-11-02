package net.iriscan.sdk.core.image

/**
 * @author Slava Gornostal
 *
 * Color operations
 * @see ImageColorType
 */
typealias Color = Int

fun createColor(r: Int, g: Int, b: Int) = (r shl 16) or (g shl 8) or b or 0x000000

fun Color.red() = this ushr 16 and 0xFF
fun Color.green() = this ushr 8 and 0xFF
fun Color.blue() = this and 0xFF

operator fun Color.component1() = red()
operator fun Color.component2() = green()
operator fun Color.component3() = blue()

/** Highest gray level, and highest value of a red, green or blue channel. */
private const val MAX_LEVEL = 0xFF

/** The red, green and blue bits of an RGB pixel. */
private const val RGB_BITS = 0xFFFFFF

private const val BLACK: Color = 0x000000
private const val WHITE: Color = 0xFFFFFF

/**
 * A pixel of an image of [type] as 0xRRGGBB, for code that needs RGB whatever the color type: the
 * BMP writer and the conversions to platform images (`Bitmap`, `CGImage`, `BufferedImage`). A GRAY
 * or BINARY value taken as 0xRRGGBB would fill only the blue channel.
 *
 * A GRAY value becomes the same level in red, green and blue, clamped to 0..255, so the -1 that
 * marks masked pixels in iris textures becomes black, as in the PNG writer. BINARY 0 becomes black
 * and any other value white. An RGB value keeps its low 24 bits: pixels taken from Android bitmaps
 * carry their alpha above them.
 */
internal fun toRgb(color: Color, type: ImageColorType): Color = when (type) {
    ImageColorType.RGB -> color and RGB_BITS
    ImageColorType.GRAY -> color.coerceIn(0, MAX_LEVEL).let { createColor(it, it, it) }
    ImageColorType.BINARY -> if (color != 0) WHITE else BLACK
}
