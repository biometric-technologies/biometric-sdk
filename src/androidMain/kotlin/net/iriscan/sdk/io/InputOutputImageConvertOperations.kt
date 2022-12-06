package net.iriscan.sdk.io

import android.graphics.Bitmap
import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.image.ImageColorType
import net.iriscan.sdk.core.image.createColor
import net.iriscan.sdk.core.utils.createImg

/**
 * @author Slava Gornostal
 */
actual interface InputOutputImageConvertOperations {
    fun convert(image: Bitmap): Image
}

internal actual class InputOutputImageConvertOperationsImpl actual constructor() : InputOutputImageConvertOperations {
    override fun convert(image: Bitmap): Image =
        createImg(
            width = image.width,
            height = image.height,
            type = ImageColorType.RGB,
        ) { x, y ->
            val raw = image.getPixel(x, y)
            createColor(raw shr 16 and 0xff, raw shr 8 and 0xff, raw and 0xff)
        }
}