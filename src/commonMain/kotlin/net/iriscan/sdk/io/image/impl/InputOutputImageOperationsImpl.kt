package net.iriscan.sdk.io.image.impl

import com.soywiz.korio.async.runBlockingNoJs
import com.soywiz.korio.file.VfsOpenMode
import com.soywiz.korio.file.std.localVfs
import com.soywiz.korio.stream.readAll
import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.io.InputOutputImageOperations
import net.iriscan.sdk.io.exception.IOException
import net.iriscan.sdk.io.exception.UnknownFormatException
import net.iriscan.sdk.io.image.ImageFormat
import net.iriscan.sdk.io.image.ImageSerializer
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

    override fun readImage(filePath: String): Image {
        val bytes = runBlockingNoJs {
            try {
                localVfs(filePath).open().readAll()
            } catch (th: Throwable) {
                throw IOException("Could not read file $filePath", th)
            }
        }
        return readImage(bytes)
    }

    override fun writeImage(image: Image, filePath: String, format: ImageFormat) {
        val bytes = writeAsByteArrayImage(image, format)
        runBlockingNoJs {
            localVfs(filePath).open(VfsOpenMode.WRITE).write(bytes)
        }
    }

    override fun writeAsByteArrayImage(image: Image, format: ImageFormat): ByteArray {
        val serializer = serializers.firstOrNull { it.format == format }
            ?: throw UnknownFormatException("Unsupported image format")
        return serializer.write(image)
    }
}