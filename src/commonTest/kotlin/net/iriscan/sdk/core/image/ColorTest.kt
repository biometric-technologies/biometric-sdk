package net.iriscan.sdk.core.image

import kotlin.test.Test
import kotlin.test.assertEquals

class ColorTest {

    @Test
    fun rgbPixelsKeepTheirColor() {
        assertEquals(0x123456, toRgb(0x123456, ImageColorType.RGB))
        // convert(Bitmap) on Android keeps the opaque alpha of the bitmap in the top byte
        assertEquals(0x123456, toRgb(0xFF123456.toInt(), ImageColorType.RGB))
    }

    @Test
    fun grayLevelsBecomeTheSameLevelInEveryChannel() {
        for (level in 0..255) {
            val rgb = toRgb(level, ImageColorType.GRAY)
            assertEquals(level, rgb.red(), "red of gray $level")
            assertEquals(level, rgb.green(), "green of gray $level")
            assertEquals(level, rgb.blue(), "blue of gray $level")
            assertEquals(0, rgb ushr 24, "no bits above the color of gray $level")
        }
    }

    @Test
    fun grayOutsideTheRangeIsClamped() {
        // iris().extractor().extract() marks masked texture pixels with -1
        assertEquals(0x000000, toRgb(-1, ImageColorType.GRAY))
        assertEquals(0xFFFFFF, toRgb(256, ImageColorType.GRAY))
    }

    @Test
    fun binaryPixelsBecomeBlackOrWhite() {
        assertEquals(0x000000, toRgb(0, ImageColorType.BINARY))
        assertEquals(0xFFFFFF, toRgb(1, ImageColorType.BINARY))
    }
}
