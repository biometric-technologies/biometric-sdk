package net.iriscan.sdk.io.image

import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.image.ImageColorType
import net.iriscan.sdk.core.io.CRC32
import net.iriscan.sdk.io.exception.IOException
import net.iriscan.sdk.io.image.ImageFixtures.PALETTE
import net.iriscan.sdk.io.image.ImageFixtures.red
import net.iriscan.sdk.io.image.ImageFixtures.rgb
import net.iriscan.sdk.io.image.serializers.PNG
import net.iriscan.sdk.io.image.serializers.paethPredictor
import net.iriscan.sdk.testutil.hex
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PngTest {

    private val width = 7
    private val height = 5

    private fun assertPixels(image: Image, type: ImageColorType, expected: (Int, Int) -> Int) {
        assertEquals(width, image.width)
        assertEquals(height, image.height)
        assertEquals(type, image.colorType)
        for (y in 0 until height) for (x in 0 until width) {
            assertEquals(expected(x, y), image[x, y], "pixel ($x, $y)")
        }
    }

    @Test
    fun readsGrayscaleWithAllFiveFilters() =
        assertPixels(PNG.read(hex(ImageFixtures.PNG_GRAY)), ImageColorType.GRAY, ::red)

    @Test
    fun readsRgbSplitOverSeveralIdatChunksInRgbOrder() =
        assertPixels(PNG.read(hex(ImageFixtures.PNG_RGB)), ImageColorType.RGB, ::rgb)

    @Test
    fun readsGrayscaleWithAlphaAndDropsAlpha() =
        assertPixels(PNG.read(hex(ImageFixtures.PNG_GRAY_ALPHA)), ImageColorType.GRAY, ::red)

    @Test
    fun readsRgbWithAlphaAndDropsAlpha() =
        assertPixels(PNG.read(hex(ImageFixtures.PNG_RGB_ALPHA)), ImageColorType.RGB, ::rgb)

    @Test
    fun readsPaletteImages() =
        assertPixels(PNG.read(hex(ImageFixtures.PNG_PALETTE)), ImageColorType.RGB) { x, y -> PALETTE[(x + y) % 4] }

    @Test
    fun recognisesTheSignature() {
        assertTrue(PNG.canRead(hex(ImageFixtures.PNG_RGB)))
        assertFalse(PNG.canRead(hex(ImageFixtures.BMP_24)))
        assertFalse(PNG.canRead(hex("89504e47")))
    }

    @Test
    fun rgbRoundTrip() {
        val image = Image(37, 21, ImageColorType.RGB, IntArray(37 * 21) { (it * 0x01F3A7) and 0xFFFFFF })
        val back = PNG.read(PNG.write(image))
        assertEquals(ImageColorType.RGB, back.colorType)
        assertContentEquals(image.colors, back.colors)
    }

    @Test
    fun grayAndBinaryAreWrittenAsGrayscale() {
        val gray = Image(9, 4, ImageColorType.GRAY, IntArray(36) { it * 7 })
        assertContentEquals(gray.colors, PNG.read(PNG.write(gray)).colors)
        val binary = Image(3, 1, ImageColorType.BINARY, intArrayOf(0, 1, 0))
        assertContentEquals(intArrayOf(0, 255, 0), PNG.read(PNG.write(binary)).colors)
    }

    @Test
    fun writesChunksWithValidCrcs() {
        val bytes = PNG.write(Image(2, 2, ImageColorType.RGB, intArrayOf(0xFF0000, 0x00FF00, 0x0000FF, 0xFFFFFF)))
        var offset = 8
        val types = mutableListOf<String>()
        while (offset < bytes.size) {
            val length = (bytes[offset].toInt() and 0xFF shl 24) or (bytes[offset + 1].toInt() and 0xFF shl 16) or
                    (bytes[offset + 2].toInt() and 0xFF shl 8) or (bytes[offset + 3].toInt() and 0xFF)
            val typeAndData = bytes.copyOfRange(offset + 4, offset + 8 + length)
            val crcOffset = offset + 8 + length
            val storedCrc = (bytes[crcOffset].toInt() and 0xFF shl 24) or (bytes[crcOffset + 1].toInt() and 0xFF shl 16) or
                    (bytes[crcOffset + 2].toInt() and 0xFF shl 8) or (bytes[crcOffset + 3].toInt() and 0xFF)
            assertEquals(CRC32.compute(typeAndData), storedCrc)
            types += typeAndData.copyOf(4).decodeToString()
            offset = crcOffset + 4
        }
        assertEquals(listOf("IHDR", "IDAT", "IEND"), types)
    }

    @Test
    fun rejectsDamagedFiles() {
        val good = hex(ImageFixtures.PNG_RGB)
        val badCrc = good.copyOf().also { it[29] = (it[29] + 1).toByte() } // first byte of the IHDR CRC
        val truncated = good.copyOf(good.size - 20)
        val sixteenBit = good.copyOf().also { it[24] = 16 }                 // IHDR bit depth
        assertFailsWith<IOException>("CRC") { PNG.read(badCrc) }
        assertFailsWith<IOException>("truncated") { PNG.read(truncated) }
        assertFailsWith<IOException>("16-bit") { PNG.read(fixCrc(sixteenBit, chunkStart = 8)) }
        assertFailsWith<IOException>("not a PNG") { PNG.read(hex(ImageFixtures.BMP_24)) }
    }

    /** Recomputes the CRC of the chunk at [chunkStart] after a test changed its data. */
    private fun fixCrc(bytes: ByteArray, chunkStart: Int): ByteArray {
        val length = bytes[chunkStart + 3].toInt() and 0xFF
        val crc = CRC32.compute(bytes.copyOfRange(chunkStart + 4, chunkStart + 8 + length))
        val at = chunkStart + 8 + length
        for (i in 0 until 4) bytes[at + i] = (crc ushr (24 - 8 * i)).toByte()
        return bytes
    }

    @Test
    fun paethPredictorPicksTheClosestNeighbour() {
        // arguments: left a, above b, upper-left c; the estimate is a + b - c
        assertEquals(10, paethPredictor(10, 20, 20)) // estimate 10: a is closest
        assertEquals(20, paethPredictor(30, 20, 40)) // estimate 10: b is 10 away, a 20, c 30
        assertEquals(30, paethPredictor(10, 50, 30)) // estimate 30: c is closest
        assertEquals(20, paethPredictor(20, 20, 0))  // a and b equally close: a wins
    }
}
