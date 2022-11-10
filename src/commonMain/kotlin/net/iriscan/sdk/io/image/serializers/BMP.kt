package net.iriscan.sdk.io.image.serializers

import com.soywiz.korio.stream.*
import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.image.ImageColorType
import net.iriscan.sdk.io.exception.IOException
import net.iriscan.sdk.io.image.ImageFormat
import net.iriscan.sdk.io.image.ImageSerializer

/**
 * @author Slava Gornostal
 */
object BMP : ImageSerializer {

    override val format: ImageFormat
        get() = ImageFormat.BMP

    override fun canRead(data: ByteArray): Boolean =
        MemorySyncStream(data).readStringz(2) == "BMP"

    override fun read(data: ByteArray): Image {
        val stream = MemorySyncStream(data)
        if (stream.availableRead < 54) {
            throw IOException("Unexpected EOF")
        }
        // skip header, file size, reserved
        stream.skip(10)
        val dataOffset = stream.readS32LE()
        // skip header size
        stream.skip(4)
        val height = stream.readS32LE()
        val width = stream.readS32LE()
        // skip planes
        stream.skip(2)
        val bitsPerPixel = stream.readS16LE()
        val compression = stream.readS32LE()
        if (compression != 0) throw IOException("Unsupported BMP compression $compression")
        val imageSize = stream.readS32LE()
        // skip pixelsPerMeterX, pixelsPerMeterY, usedColors, importantColors
        stream.skip(16)
        if (dataOffset > 54) {
            stream.skip(dataOffset - 54)
        }
        if (stream.availableRead < imageSize) {
            throw IOException("Unexpected EOF")
        }
        val colors = readColors(stream, bitsPerPixel, width, height)
        return Image(width, height, ImageColorType.RGB, colors)
    }

    private fun readColors(stream: SyncStream, bitsPerPixel: Int, width: Int, height: Int): IntArray {
        val skip = when (val padding = 4 - (width * 24 / 8 % 4)) {
            4 -> 0
            else -> padding
        }
        val result = IntArray(height * width)
        when (bitsPerPixel) {
            24, 32 -> {
                for (y in height - 1 downTo 0) {
                    for (x in 0 until width) {
                        result[y * width + x] = stream.readS24LE()
                    }
                    stream.skip(skip)
                }
            }

            else -> throw IOException("Unsupported bits per pixel $bitsPerPixel")
        }
        return result
    }

    override fun write(image: Image): ByteArray = MemorySyncStreamToByteArray {
        writeStringz("BM")
        val imageSize = image.size * 24 / 8
        val fileSize = 54 + imageSize
        // file size, reserved
        write32LE(fileSize)
        write16LE(0)
        write16LE(0)
        // header size
        write32LE(54)
        // info header size
        write32LE(40)
        write32LE(image.width)
        write32LE(image.height)
        // planes, bits per pixel, compression,
        write16LE(1)
        write16LE(24)
        write32LE(0)
        write32LE(imageSize)
        // pixelsPerMeterX, pixelsPerMeterY, usedColors, importantColors
        write32LE(0)
        write32LE(0)
        write32LE(0)
        write32LE(0)
        for (y in image.height - 1 downTo 0) {
            for (x in 0 until image.width) {
                write24LE(image[x, y])
            }
        }
    }

}