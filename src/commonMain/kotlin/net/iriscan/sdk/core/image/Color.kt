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