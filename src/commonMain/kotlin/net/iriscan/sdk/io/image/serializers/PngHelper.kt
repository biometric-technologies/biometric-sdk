package net.iriscan.sdk.io.image.serializers

import net.iriscan.sdk.core.io.MemoryStream
import net.iriscan.sdk.core.io.buildBytes
import net.iriscan.sdk.core.io.CRC32
import net.iriscan.sdk.core.io.ZLib

import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.io.exception.IOException
import kotlin.math.abs

/**
 * @author Slava Gornostal
 * @author Anton Kurinnoy
 */
internal class PngChunkData(
    var width: Int = -1,
    var height: Int = -1,
    var colorType: Int = -1,
    var palette: ByteArray = byteArrayOf(),
    var data: ByteArray = byteArrayOf()
)

internal fun readPngChunks(stream: MemoryStream): PngChunkData {
    val data = PngChunkData()
    val length = stream.readS32BE()
    if (stream.availableRead < length) {
        throw IOException("Invalid png file, unexpected EOF on IHDR read")
    }
    val type = stream.readStringz(4)
    if (type != "IHDR") {
        throw IOException("Invalid png file, no IHDR chunk available")
    }
    readIHDR(stream, data)
    readChunks(stream, data)
    return data
}

private fun readChunks(stream: MemoryStream, data: PngChunkData) {
    // Image data may be split over several IDAT chunks; it is one zlib stream.
    val idat = MemoryStream()
    while (true) {
        val length = stream.readS32BE()
        when (stream.readStringz(4)) {
            "PLTE" -> readPLTE(stream, length, data)
            "IDAT" -> idat.writeBytes(stream.readBytes(length))
            "IEND" -> break
            else -> stream.skip(length)
        }
        // crc
        stream.skip(4)
    }
    data.data = ZLib.uncompress(idat.toByteArray())
}


private fun readIHDR(stream: MemoryStream, data: PngChunkData) {
    data.width = stream.readS32BE()
    data.height = stream.readS32BE()
    // bitDepth
    stream.skip(1)
    data.colorType = stream.readS8()
    // compressionMethod, filterMethod, interlaceMethod
    stream.skip(3)
    // crc
    stream.skip(4)
}

private fun readPLTE(stream: MemoryStream, length: Int, data: PngChunkData) {
    data.palette = stream.readBytes(length)
}

internal fun paethPredictor(a: Int, b: Int, c: Int): Int {
    val p = a + b - c
    val pa = abs(p - a)
    val pb = abs(p - b)
    val pc = abs(p - c)
    return if ((pa <= pb) && (pa <= pc)) a else if (pb <= pc) b else c
}

internal fun writeChunk(stream: MemoryStream, name: String, image: Image) {
    var crc = CRC32.initialValue

    when (name) {
        "IHDR" -> {
            stream.write32BE(13)
            stream.writeBytes("IHDR".encodeToByteArray())
            stream.write32BE(image.width)
            stream.write32BE(image.height)
            stream.write8(8) // bitDepth
            stream.write8(2) // colorType
            stream.write8(0) // compressionMethod
            stream.write8(0) // filterMethod
            stream.write8(0) // interlaceMethod

            crc = CRC32.update(crc, name.encodeToByteArray(), 0, name.encodeToByteArray().size)
            val writtenData = stream.toByteArray()
            val data = writtenData.sliceArray(writtenData.size - 13 until writtenData.size)
            crc = CRC32.update(crc, data, 0, data.size)
        }
        "IDAT" -> {
            var compressedData = buildBytes{
                val colors = image.colors.toList().chunked(image.width).map { it.toIntArray() }
                for (y in 0 until image.height) {
                    write8(0) //no filter
                    colors[y].forEach { write24LE(it) }
                }
            }
            compressedData = ZLib.compress(compressedData)
            stream.write32BE(compressedData.size)
            stream.writeBytes("IDAT".encodeToByteArray())
            stream.writeBytes(compressedData)
            crc = CRC32.update(crc, name.encodeToByteArray(), 0, name.encodeToByteArray().size)
            crc = CRC32.update(crc, compressedData, 0, compressedData.size)
        }
        "IEND" -> {
            stream.write32BE(0)
            stream.writeBytes("IEND".encodeToByteArray())
            crc = CRC32.update(crc, name.encodeToByteArray(), 0, name.encodeToByteArray().size)
            crc = CRC32.update(crc, byteArrayOf(), 0, 0)
        }
        else -> throw IOException("Unknown chunk")
    }

    stream.write32BE(crc)
}