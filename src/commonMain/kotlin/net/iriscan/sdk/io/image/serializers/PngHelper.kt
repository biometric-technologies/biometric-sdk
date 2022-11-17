package net.iriscan.sdk.io.image.serializers

import com.soywiz.korio.compression.compress
import com.soywiz.korio.compression.deflate.ZLib
import com.soywiz.korio.compression.uncompress
import com.soywiz.korio.lang.toByteArray
import com.soywiz.korio.stream.*
import com.soywiz.korio.util.checksum.CRC32
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

internal fun readPngChunks(stream: SyncStream): PngChunkData {
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

private fun readChunks(stream: SyncStream, data: PngChunkData) {
    val length = stream.readS32BE()
    when (stream.readStringz(4)) {
        "PLTE" -> readPLTE(stream, length, data)
        "IDAT" -> readIDAT(stream, length, data)
        "IEND" -> return
        else -> stream.skip(length)
    }
    // crc
    stream.skip(4)
    readChunks(stream, data)
}


private fun readIHDR(stream: SyncStream, data: PngChunkData) {
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

private fun readPLTE(stream: SyncStream, length: Int, data: PngChunkData) {
    data.palette = stream.readBytes(length)
}

private fun readIDAT(stream: SyncStream, length: Int, data: PngChunkData) {
    data.data = stream.readBytes(length).uncompress(ZLib)
}

internal fun paethPredictor(a: Int, b: Int, c: Int): Int {
    val p = a + b - c
    val pa = abs(p - a)
    val pb = abs(p - b)
    val pc = abs(p - c)
    return if ((pa <= pb) && (pa <= pc)) a else if (pb <= pc) b else c
}

internal fun writeChunk(stream: SyncStream, name: String, image: Image) {
    var crc = CRC32.initialValue

    when (name) {
        "IHDR" -> {
            stream.write32BE(13)
            stream.writeBytes("IHDR".toByteArray())
            stream.write32BE(image.width)
            stream.write32BE(image.height)
            stream.write8(8) // bitDepth
            stream.write8(2) // colorType
            stream.write8(0) // compressionMethod
            stream.write8(0) // filterMethod
            stream.write8(0) // interlaceMethod

            crc = CRC32.update(crc, name.toByteArray(), 0, name.toByteArray().size)
            val writtenData = stream.toByteArray()
            val data = writtenData.sliceArray(writtenData.size - 13 until writtenData.size)
            crc = CRC32.update(crc, data, 0, data.size)
        }
        "IDAT" -> {
            var compressedData = MemorySyncStreamToByteArray{
                val colors = image.colors.toList().chunked(image.width).map { it.toIntArray() }
                for (y in 0 until image.height) {
                    write8(0) //no filter
                    colors[y].forEach { write24LE(it) }
                }
            }
            compressedData = compressedData.compress(ZLib)
            stream.write32BE(compressedData.size)
            stream.writeBytes("IDAT".toByteArray())
            stream.writeBytes(compressedData)
            crc = CRC32.update(crc, name.toByteArray(), 0, name.toByteArray().size)
            crc = CRC32.update(crc, compressedData, 0, compressedData.size)
        }
        "IEND" -> {
            stream.write32BE(0)
            stream.writeBytes("IEND".toByteArray())
            crc = CRC32.update(crc, name.toByteArray(), 0, name.toByteArray().size)
            crc = CRC32.update(crc, byteArrayOf(), 0, 0)
        }
        else -> throw IOException("Unknown chunk")
    }

    stream.write32BE(crc)
}