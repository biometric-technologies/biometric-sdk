package net.iriscan.sdk.io

import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.image.ImageColorType
import net.iriscan.sdk.io.image.ImageFixtures
import net.iriscan.sdk.io.image.serializers.PNG
import net.iriscan.sdk.testutil.hex
import java.awt.image.BufferedImage
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Conversion of SDK images to `BufferedImage`, which an application uses to show or save them:
 * an image of any color type must give the picture the PNG writer gives. GRAY and BINARY values
 * used to be taken as 0xRRGGBB, which filled only the blue channel.
 */
class ImageConvertOperationsTest {

    private val operations = InputOutputImageConvertOperationsImpl()

    /** 0xRRGGBB of every pixel, row by row. */
    private fun BufferedImage.pixels(): IntArray = IntArray(width * height) { getRGB(it % width, it / width) and 0xFFFFFF }

    private fun gray(level: Int) = (level shl 16) or (level shl 8) or level

    @Test
    fun grayImageGivesGrayPixels() {
        // what readImage() returns for a grayscale PNG
        val image = PNG.read(hex(ImageFixtures.PNG_GRAY))
        assertEquals(ImageColorType.GRAY, image.colorType)
        val converted = operations.convertToBufferedImage(image)
        assertEquals(BufferedImage.TYPE_INT_RGB, converted.type)
        assertEquals(image.width, converted.width)
        assertEquals(image.height, converted.height)
        val expected = IntArray(image.size) { gray(ImageFixtures.red(it % image.width, it / image.width)) }
        assertContentEquals(expected, converted.pixels())
    }

    @Test
    fun maskedIrisTexturePixelsComeOutBlack() {
        // iris().extractor().extract() marks masked texture pixels with -1
        val texture = Image(4, 1, ImageColorType.GRAY, intArrayOf(-1, 0, 128, 255))
        assertContentEquals(intArrayOf(0, 0, gray(128), gray(255)), operations.convertToBufferedImage(texture).pixels())
    }

    @Test
    fun binaryImageGivesBlackAndWhitePixels() {
        val binary = Image(3, 2, ImageColorType.BINARY, intArrayOf(0, 1, 0, 1, 1, 0))
        val white = 0xFFFFFF
        assertContentEquals(intArrayOf(0, white, 0, white, white, 0), operations.convertToBufferedImage(binary).pixels())
    }

    @Test
    fun grayAndBinaryImagesLookAsThePngWriterWritesThem() {
        for (image in listOf(
            Image(4, 1, ImageColorType.GRAY, intArrayOf(-1, 77, 255, 300)),
            Image(2, 1, ImageColorType.BINARY, intArrayOf(1, 0)),
        )) {
            val levels = PNG.read(PNG.write(image))
            assertEquals(ImageColorType.GRAY, levels.colorType)
            val expected = IntArray(levels.size) { gray(levels[it]) }
            assertContentEquals(expected, operations.convertToBufferedImage(image).pixels(), "${image.colorType}")
        }
    }

    @Test
    fun rgbImageComesBackUnchanged() {
        val random = Random(20261102)
        val image = Image(17, 5, ImageColorType.RGB, IntArray(17 * 5) { random.nextInt(0x1000000) })
        val converted = operations.convertToBufferedImage(image)
        assertContentEquals(image.colors, converted.pixels())
        val back = operations.convert(converted)
        assertEquals(ImageColorType.RGB, back.colorType)
        assertContentEquals(image.colors, back.colors)
    }
}
