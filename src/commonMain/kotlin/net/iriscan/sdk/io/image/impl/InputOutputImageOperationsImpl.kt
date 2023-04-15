package net.iriscan.sdk.io.image.impl

import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.image.NativeImage
import net.iriscan.sdk.core.image.internalReadNativeImage
import net.iriscan.sdk.core.image.internalWriteNativeImage
import net.iriscan.sdk.core.io.DataBytes
import net.iriscan.sdk.io.InputOutputImageOperations
import net.iriscan.sdk.io.exception.UnknownFormatException
import net.iriscan.sdk.io.image.ImageFormat
import net.iriscan.sdk.io.image.serializers.BMP
import net.iriscan.sdk.io.image.serializers.PNG

/**
 * @author Slava Gornostal
 */
internal class InputOutputImageOperationsImpl : InputOutputImageOperations {

    private val serializers = listOf(BMP, PNG)

    override fun readImage(data: ByteArray): Image {
        val serializer = serializers.firstOrNull { it.canRead(data) }
            ?: throw UnknownFormatException("Unknown image format")
        return serializer.read(data)
    }

    override fun readNativeImage(data: DataBytes): NativeImage =
        internalReadNativeImage(data)

    override fun writeImage(image: Image, format: ImageFormat): ByteArray {
        val serializer = serializers.firstOrNull { it.format == format }
            ?: throw UnknownFormatException("Unsupported image format")
        return serializer.write(image)
    }

    override fun writeNativeImage(image: NativeImage, format: ImageFormat): DataBytes =
        internalWriteNativeImage(image, format)
}