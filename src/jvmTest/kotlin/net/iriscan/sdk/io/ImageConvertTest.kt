package net.iriscan.sdk.io

import net.iriscan.sdk.core.image.ImageColorType
import net.iriscan.sdk.core.image.createColor
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ImageConvertTest {
    private val convert = InputOutputImageConvertOperationsImpl()

    private fun gray(type: Int, values: IntArray): BufferedImage {
        val image = BufferedImage(values.size, 1, type)
        values.forEachIndexed { x, v -> image.raster.setSample(x, 0, 0, v) }
        return image
    }

    private val values = intArrayOf(0, 20, 64, 128, 200, 255)
    private val expected = values.map { createColor(it, it, it) }.toIntArray()

    @Test
    fun grayPixelsKeepTheirValues() {
        // getRGB would give 0, 79, 137, 188, 228, 255 here
        val image = convert.convert(gray(BufferedImage.TYPE_BYTE_GRAY, values))
        assertEquals(ImageColorType.RGB, image.colorType)
        assertContentEquals(expected, image.colors)
    }

    @Test
    fun sixteenBitGrayIsReducedToEightBits() {
        val image = convert.convert(gray(BufferedImage.TYPE_USHORT_GRAY, values.map { it * 257 }.toIntArray()))
        assertContentEquals(expected, image.colors)
    }

    @Test
    fun grayPngReadByImageIoKeepsItsValues() {
        val bytes = ByteArrayOutputStream().also { ImageIO.write(gray(BufferedImage.TYPE_BYTE_GRAY, values), "png", it) }.toByteArray()
        val image = convert.convert(ImageIO.read(ByteArrayInputStream(bytes)))
        assertContentEquals(expected, image.colors)
    }

    @Test
    fun colorImagesAreUnchanged() {
        val source = BufferedImage(3, 1, BufferedImage.TYPE_INT_RGB)
        val colors = intArrayOf(0x102030, 0x7f8081, 0xff00aa)
        colors.forEachIndexed { x, c -> source.setRGB(x, 0, c) }
        assertContentEquals(colors, convert.convert(source).colors)
    }
}
