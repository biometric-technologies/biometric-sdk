package net.iriscan.sdk.io

import net.iriscan.sdk.core.image.*
import net.iriscan.sdk.core.utils.createImg
import java.awt.Color
import java.awt.image.BufferedImage

/**
 * @author Slava Gornostal
 */
actual interface InputOutputImageConvertOperations {
    fun convert(image: BufferedImage): Image
    fun convertToBufferedImage(image: Image): BufferedImage
}

internal actual class InputOutputImageConvertOperationsImpl actual constructor() : InputOutputImageConvertOperations {

    override fun convert(image: BufferedImage): Image =
        createImg(
            width = image.width,
            height = image.height,
            type = ImageColorType.RGB
        ) { x, y ->
            val rawColor = Color(image.getRGB(x, y))
            createColor(rawColor.red, rawColor.green, rawColor.blue)
        }

    override fun convertToBufferedImage(image: Image): BufferedImage {
        val bufferedImage = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        val data = image.colors.flatMap { listOf(it.red(), it.green(), it.blue()) }.toIntArray()
        bufferedImage.raster.setPixels(0, 0, image.width, image.height, data)
        return bufferedImage
    }

}