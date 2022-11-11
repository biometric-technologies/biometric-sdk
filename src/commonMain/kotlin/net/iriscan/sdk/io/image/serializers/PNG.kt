@file:OptIn(ExperimentalUnsignedTypes::class)

package net.iriscan.sdk.io.image.serializers

import com.soywiz.kmem.readS24LE
import com.soywiz.kmem.readS32LE
import com.soywiz.korio.stream.*
import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.image.ImageColorType
import net.iriscan.sdk.io.exception.IOException
import net.iriscan.sdk.io.image.ImageFormat
import net.iriscan.sdk.io.image.ImageSerializer
import kotlin.math.floor

/**
 * @author Slava Gornostal
 */
internal object PNG : ImageSerializer {

    private val MAGIC = ubyteArrayOf(137u, 80u, 78u, 71u, 13u, 10u, 26u, 10u)

    override val format: ImageFormat
        get() = ImageFormat.PNG

    override fun canRead(data: ByteArray): Boolean =
        data.size > 8 && MemorySyncStream(data).readUByteArray(8)
            .contentEquals(MAGIC)

    override fun read(data: ByteArray): Image {
        val stream = MemorySyncStream(data)
        val pngData = readPngChunks(stream)
        val bytesPerPixel = when (pngData.colorType) {
            2 -> 3
            6 -> 4
            else -> throw IOException("Unsupported color type ${pngData.colorType}")
        }
        val dataStream = MemorySyncStream(pngData.data)
        var previous: UByteArray = ubyteArrayOf()
        var current: UByteArray
        val rowWidth = pngData.width * bytesPerPixel
        val colors = IntArray(pngData.width * pngData.height)
        for (y in 0 until pngData.height) {
            val filterType = dataStream.readU8()
            current = dataStream.readUByteArray(rowWidth)
            when (filterType) {
                0 -> Unit
                1 -> for (n in bytesPerPixel until current.size) current[n] =
                    (current[n] + current[n - bytesPerPixel]).toUByte()

                2 -> for (n in current.indices) current[n] = (current[n] + previous[n]).toUByte()
                3 -> {
                    for (n in 0 until bytesPerPixel) {
                        val a = current[n].toInt()
                        val b = previous[n].toInt()
                        current[n] = (a + b / 2).toUByte()
                    }
                    for (n in bytesPerPixel until current.size) {
                        val a = current[n].toInt()
                        val b = current[n - bytesPerPixel].toInt()
                        val c = previous[n].toInt()
                        current[n] = (a + floor((b + c) / 2.0)).toUInt().toUByte()
                    }
                }

                4 -> {
                    for (n in 0 until bytesPerPixel) current[n] = (current[n] + previous[n]).toUByte()
                    for (n in bytesPerPixel until current.size) current[n] = (current[n].toInt() + paethPredictor(
                        current[n - bytesPerPixel].toInt(),
                        previous[n].toInt(),
                        previous[n - bytesPerPixel].toInt()
                    )).toUByte()
                }
            }
            previous = current.copyOf()
            for ((x, off) in (current.indices step bytesPerPixel).withIndex()) {
                colors[y * pngData.width + x] = when (bytesPerPixel) {
                    3 -> current.asByteArray().readS24LE(off)
                    4 -> current.asByteArray().readS32LE(off)
                    else -> throw IllegalStateException("Bytes per pixel $bytesPerPixel is not supported")
                }
            }
        }
        return Image(
            width = pngData.width,
            height = pngData.height,
            colorType = ImageColorType.RGB,
            colors = colors
        )
    }

    override fun write(image: Image): ByteArray = MemorySyncStreamToByteArray {
        TODO("Not yet implemented")
    }

}