package net.iriscan.sdk.io.image

import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.image.ImageColorType
import net.iriscan.sdk.io.exception.IOException
import net.iriscan.sdk.io.image.ImageFixtures.red
import net.iriscan.sdk.io.image.ImageFixtures.rgb
import net.iriscan.sdk.io.image.serializers.BMP
import net.iriscan.sdk.io.image.serializers.PNG
import net.iriscan.sdk.testutil.hex
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BmpTest {

    private fun assertPixels(image: Image, width: Int, height: Int, type: ImageColorType, expected: (Int, Int) -> Int) {
        assertEquals(width, image.width)
        assertEquals(height, image.height)
        assertEquals(type, image.colorType)
        for (y in 0 until height) for (x in 0 until width) {
            assertEquals(expected(x, y), image[x, y], "pixel ($x, $y)")
        }
    }

    @Test
    fun reads24BitBottomUpWithRowPadding() =
        assertPixels(BMP.read(hex(ImageFixtures.BMP_24)), 5, 3, ImageColorType.RGB, ::rgb)

    @Test
    fun reads24BitTopDown() =
        assertPixels(BMP.read(hex(ImageFixtures.BMP_24_TOP_DOWN)), 3, 4, ImageColorType.RGB, ::rgb)

    @Test
    fun reads32Bit() =
        assertPixels(BMP.read(hex(ImageFixtures.BMP_32)), 4, 3, ImageColorType.RGB, ::rgb)

    @Test
    fun reads8BitGrayAsGrayImage() =
        assertPixels(BMP.read(hex(ImageFixtures.BMP_8_GRAY)), 5, 3, ImageColorType.GRAY, ::red)

    @Test
    fun reads8BitColorPalette() {
        val palette = intArrayOf(0xFF0000, 0x00FF00, 0x123456)
        assertPixels(BMP.read(hex(ImageFixtures.BMP_8_PALETTE)), 3, 2, ImageColorType.RGB) { x, y -> palette[(x + y) % 3] }
    }

    @Test
    fun recognisesTheSignature() {
        assertTrue(BMP.canRead(hex(ImageFixtures.BMP_24)))
        assertFalse(BMP.canRead(hex(ImageFixtures.PNG_RGB)))
        assertFalse(BMP.canRead("BM".encodeToByteArray()))
    }

    @Test
    fun roundTripsEveryRowPadding() {
        // widths 1..4 need 1, 2, 3 and 0 bytes of padding per row at 3 bytes per pixel
        for (width in 1..4) {
            val image = Image(width, 3, ImageColorType.RGB, IntArray(width * 3) { (it * 0x2B7F31 + 0x0A0B0C) and 0xFFFFFF })
            val back = BMP.read(BMP.write(image))
            assertEquals(ImageColorType.RGB, back.colorType)
            assertContentEquals(image.colors, back.colors, "width $width")
        }
    }

    @Test
    fun writesAFileOtherReadersAccept() {
        // the written file has exactly the layout of the 24-bit fixture except for the resolution fields
        val reference = hex(ImageFixtures.BMP_24)
        val written = BMP.write(BMP.read(reference))
        assertEquals(reference.size, written.size)
        assertContentEquals(reference.copyOfRange(0, 38), written.copyOfRange(0, 38))    // headers up to the resolution
        assertContentEquals(reference.copyOfRange(46, reference.size), written.copyOfRange(46, written.size)) // palette size, pixels
    }

    @Test
    fun grayAndBinaryImagesAreWrittenAsGrayRgb() {
        val gray = Image(2, 1, ImageColorType.GRAY, intArrayOf(0x10, 0xF0))
        assertContentEquals(intArrayOf(0x101010, 0xF0F0F0), BMP.read(BMP.write(gray)).colors)
        val binary = Image(2, 1, ImageColorType.BINARY, intArrayOf(1, 0))
        assertContentEquals(intArrayOf(0xFFFFFF, 0), BMP.read(BMP.write(binary)).colors)
    }

    @Test
    fun pngAndBmpAgreeOnColors() {
        val fromBmp = BMP.read(hex(ImageFixtures.BMP_24))
        assertContentEquals(fromBmp.colors, PNG.read(PNG.write(fromBmp)).colors)
    }

    @Test
    fun rejectsUnsupportedOrDamagedFiles() {
        val good = hex(ImageFixtures.BMP_24)
        val compressed = good.copyOf().also { it[30] = 1 }      // BI_RLE8
        val sixteenBit = good.copyOf().also { it[28] = 16 }     // bits per pixel
        val truncated = good.copyOf(good.size - 10)
        assertFailsWith<IOException>("compressed") { BMP.read(compressed) }
        assertFailsWith<IOException>("16-bit") { BMP.read(sixteenBit) }
        assertFailsWith<IOException>("truncated") { BMP.read(truncated) }
        assertFailsWith<IOException>("not a BMP") { BMP.read(hex(ImageFixtures.PNG_RGB)) }
    }
}
