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

    /**
     * A `TYPE_INT_RGB` copy of [image]. `GRAY` and `BINARY` images give gray and black-and-white
     * pictures; the masked pixels (-1) of an iris texture come out black, as in the PNG writer.
     */
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
        val pixels = IntArray(image.size) { toRgb(image[it], image.colorType) }
        bufferedImage.setRGB(0, 0, image.width, image.height, pixels, 0, image.width)
        return bufferedImage
    }

}