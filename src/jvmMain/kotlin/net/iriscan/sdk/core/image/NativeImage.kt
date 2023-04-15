package net.iriscan.sdk.core.image

import net.iriscan.sdk.core.io.DataBytes
import net.iriscan.sdk.io.image.ImageFormat
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * @author Slava Gornostal
 */
actual typealias NativeImage = BufferedImage

internal actual fun internalReadNativeImage(dataBytes: DataBytes): NativeImage =
    ImageIO.read(ByteArrayInputStream(dataBytes))

internal actual fun internalWriteNativeImage(
    image: NativeImage,
    format: ImageFormat
): DataBytes {
    val writeFormat = when (format) {
        ImageFormat.BMP -> "bmp"
        ImageFormat.PNG -> "png"
        ImageFormat.JPEG -> "jpg"
    }
    val outs = ByteArrayOutputStream()
    ImageIO.write(image, writeFormat, outs)
    return outs.toByteArray()
}