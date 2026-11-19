package net.iriscan.sdk.io

import net.iriscan.sdk.core.image.*
import net.iriscan.sdk.core.utils.createImg
import java.awt.Color
import java.awt.color.ColorSpace
import java.awt.image.BufferedImage

/**
 * @author Slava Gornostal
 */
actual interface InputOutputImageConvertOperations {
    fun convert(image: BufferedImage): Image
    fun convertToBufferedImage(image: Image): BufferedImage
}

internal actual class InputOutputImageConvertOperationsImpl actual constructor() : InputOutputImageConvertOperations {

    override fun convert(image: BufferedImage): Image {
        if (image.colorModel.colorSpace.type == ColorSpace.TYPE_GRAY) return convertGray(image)
        return createImg(
            width = image.width,
            height = image.height,
            type = ImageColorType.RGB
        ) { x, y ->
            val rawColor = Color(image.getRGB(x, y))
            createColor(rawColor.red, rawColor.green, rawColor.blue)
        }
    }

    /**
     * Gray images (TYPE_BYTE_GRAY, TYPE_USHORT_GRAY, gray PNGs from ImageIO) are read from the raster.
     * Java treats their gray space as linear, so getRGB converts it to sRGB and brightens the image:
     * a stored 20 comes back as 79, 64 as 137, 128 as 188. The pixel values are what the camera wrote,
     * so we take them as they are. 16-bit samples are reduced to 8 bits.
     */
    private fun convertGray(image: BufferedImage): Image {
        val raster = image.raster
        val shift = (image.sampleModel.getSampleSize(0) - 8).coerceAtLeast(0)
        return createImg(
            width = image.width,
            height = image.height,
            type = ImageColorType.RGB
        ) { x, y ->
            val v = raster.getSample(x, y, 0) shr shift
            createColor(v, v, v)
        }
    }

    override fun convertToBufferedImage(image: Image): BufferedImage {
        val bufferedImage = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        val data = image.colors.flatMap { listOf(it.red(), it.green(), it.blue()) }.toIntArray()
        bufferedImage.raster.setPixels(0, 0, image.width, image.height, data)
        return bufferedImage
    }

}