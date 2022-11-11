package net.iriscan.sdk.io.image.serializers

import com.soywiz.korio.compression.deflate.ZLib
import com.soywiz.korio.compression.uncompress
import com.soywiz.korio.stream.*
import net.iriscan.sdk.io.exception.IOException
import kotlin.math.abs

/**
 * @author Slava Gornostal
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
    val length = stream.readS32LE()
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
    val length = stream.readS32LE()
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
    data.width = stream.readS32LE()
    data.height = stream.readS32LE()
    // bitDepth
    stream.skip(1)
    data.colorType = stream.readS8()
    // compressionMethod, filterMethod, interlaceMethod
    stream.skip(3)
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