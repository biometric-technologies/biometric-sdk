package net.iriscan.sdk.io

import android.graphics.Bitmap
import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.image.ImageColorType

/**
 * @author Slava Gornostal
 */
actual interface InputOutputImageConvertOperations {
    fun convert(image: Bitmap): Image
    fun convertToBitmap(image: Image): Bitmap
}

internal actual class InputOutputImageConvertOperationsImpl actual constructor() : InputOutputImageConvertOperations {
    override fun convert(image: Bitmap): Image {
        val pixels = IntArray(image.width * image.height)
        image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
        return Image(
            width = image.width,
            height = image.height,
            colorType = ImageColorType.RGB,
            colors = pixels
        )
    }

    override fun convertToBitmap(image: Image): Bitmap {
        val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.RGB_565)
        bitmap.setPixels(image.colors, 0, image.width, 0, 0, image.width, image.height)
        return bitmap
    }
}