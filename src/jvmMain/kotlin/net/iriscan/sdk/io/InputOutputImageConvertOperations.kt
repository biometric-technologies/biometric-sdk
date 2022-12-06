package net.iriscan.sdk.io

import net.iriscan.sdk.core.image.*
import net.iriscan.sdk.core.utils.createImg
import java.awt.image.BufferedImage

/**
 * @author Slava Gornostal
 */
actual interface InputOutputImageConvertOperations {
    fun convert(image: BufferedImage): Image
}

internal actual class InputOutputImageConvertOperationsImpl actual constructor() : InputOutputImageConvertOperations {

    override fun convert(image: BufferedImage): Image =
        createImg(
            width = image.width,
            height = image.height,
            type = ImageColorType.RGB
        ) { x, y ->
            val rawColor = image.getRGB(x, y)
            createColor(rawColor.red(), rawColor.green(), rawColor.blue())
        }

}