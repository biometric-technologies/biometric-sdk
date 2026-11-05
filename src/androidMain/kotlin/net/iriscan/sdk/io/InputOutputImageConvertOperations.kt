package net.iriscan.sdk.io

import android.graphics.Bitmap
import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.image.ImageColorType
import net.iriscan.sdk.core.image.toRgb

/** Alpha of an opaque pixel in the ARGB colors that [Bitmap.setPixels] takes. */
private const val OPAQUE = 0xFF shl 24

/**
 * @author Slava Gornostal
 */
actual interface InputOutputImageConvertOperations {
    fun convert(image: Bitmap): Image

    /**
     * An opaque `ARGB_8888` bitmap of [image]. `GRAY` and `BINARY` images give gray and
     * black-and-white pictures; the masked pixels (-1) of an iris texture come out black, as in the
     * PNG writer.
     */
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
        val pixels = IntArray(image.size) { OPAQUE or toRgb(image[it], image.colorType) }
        // 8 bits per channel: RGB_565 keeps 5 bits of red and blue and 6 of green, so a gray level
        // would not stay gray and the channels would not come back unchanged through convert()
        val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
        return bitmap
    }
}