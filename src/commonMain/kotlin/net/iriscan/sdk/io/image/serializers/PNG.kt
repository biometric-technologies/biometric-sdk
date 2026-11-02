package net.iriscan.sdk.io.image.serializers

import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.image.ImageColorType
import net.iriscan.sdk.core.io.CRC32
import net.iriscan.sdk.core.io.MemoryStream
import net.iriscan.sdk.core.io.ZLib
import net.iriscan.sdk.core.io.buildBytes
import net.iriscan.sdk.io.exception.IOException
import net.iriscan.sdk.io.image.ImageFormat
import net.iriscan.sdk.io.image.ImageSerializer
import kotlin.math.abs

/**
 * PNG reader and writer (ISO/IEC 15948, https://www.w3.org/TR/png/).
 *
 * Reads non-interlaced images with 8 bits per sample in all five color types: grayscale,
 * grayscale with alpha, RGB, RGB with alpha and palette. Grayscale images are returned as
 * [ImageColorType.GRAY] (one value 0..255 per pixel), the others as [ImageColorType.RGB]
 * (0xRRGGBB per pixel); alpha is dropped.
 *
 * Writes GRAY and BINARY images as 8-bit grayscale and RGB images as 8-bit RGB.
 *
 * A PNG file is the 8-byte signature followed by chunks. Every chunk is a 4-byte big-endian
 * length, a 4-letter type, the data and a CRC-32 of type and data. The pixels are in one zlib
 * stream that may be split over several IDAT chunks; every row of the stream starts with a filter
 * type byte that says how the row was predicted from its neighbours.
 *
 * @author Slava Gornostal
 * @author Anton Kurinnoy
 */
internal object PNG : ImageSerializer {

    /** \x89 P N G \r \n \x1A \n: detects the format and catches files damaged by text-mode transfers. */
    private val SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    private const val CHUNK_HEADER = "IHDR"
    private const val CHUNK_PALETTE = "PLTE"
    private const val CHUNK_IMAGE_DATA = "IDAT"
    private const val CHUNK_END = "IEND"

    /** IHDR: width (4), height (4), bit depth, color type, compression, filter, interlace (1 each). */
    private const val HEADER_DATA_BYTES = 13

    private const val CHUNK_TYPE_BYTES = 4

    /** The only bit depth supported here; 1, 2, 4 and 16 bits per sample are rare for photos. */
    private const val SUPPORTED_BIT_DEPTH = 8

    /** Compression method 0 is zlib/deflate, filter method 0 the five adaptive filters; no others are defined. */
    private const val COMPRESSION_METHOD_DEFLATE = 0
    private const val FILTER_METHOD_ADAPTIVE = 0
    private const val INTERLACE_NONE = 0

    /** PNG color types (section 11.2.2) and the number of samples each pixel has. */
    private enum class ColorType(val code: Int, val samplesPerPixel: Int) {
        GRAYSCALE(0, 1),
        RGB(2, 3),
        PALETTE(3, 1),
        GRAYSCALE_ALPHA(4, 2),
        RGB_ALPHA(6, 4);

        companion object {
            fun of(code: Int) = entries.firstOrNull { it.code == code }
                ?: throw IOException("Invalid PNG: unknown color type $code")
        }
    }

    /** Row filter types (section 9.2): each byte is stored as its difference from a prediction. */
    private const val FILTER_NONE = 0      // no prediction
    private const val FILTER_SUB = 1       // the byte one pixel to the left
    private const val FILTER_UP = 2        // the byte above
    private const val FILTER_AVERAGE = 3   // the mean of left and above
    private const val FILTER_PAETH = 4     // left, above or upper-left, whichever is closest to left + above - upper-left

    override val format: ImageFormat
        get() = ImageFormat.PNG

    override fun canRead(data: ByteArray): Boolean =
        data.size > SIGNATURE.size && data.copyOfRange(0, SIGNATURE.size).contentEquals(SIGNATURE)

    override fun read(data: ByteArray): Image {
        if (!canRead(data)) throw IOException("Not a PNG file")
        val stream = MemoryStream(data)
        stream.skip(SIGNATURE.size)

        val header = readChunk(stream)
        if (header.type != CHUNK_HEADER || header.data.size != HEADER_DATA_BYTES) {
            throw IOException("Invalid PNG: the first chunk must be IHDR")
        }
        val ihdr = MemoryStream(header.data)
        val width = ihdr.readS32BE()
        val height = ihdr.readS32BE()
        val bitDepth = ihdr.readU8()
        val colorType = ColorType.of(ihdr.readU8())
        val compression = ihdr.readU8()
        val filterMethod = ihdr.readU8()
        val interlace = ihdr.readU8()
        if (width <= 0 || height <= 0) throw IOException("Invalid PNG: size ${width}x$height")
        if (bitDepth != SUPPORTED_BIT_DEPTH) throw IOException("Unsupported PNG: $bitDepth bits per sample, only 8 are supported")
        if (compression != COMPRESSION_METHOD_DEFLATE || filterMethod != FILTER_METHOD_ADAPTIVE) {
            throw IOException("Invalid PNG: unknown compression or filter method")
        }
        if (interlace != INTERLACE_NONE) throw IOException("Unsupported PNG: interlaced (Adam7) images are not supported")

        var palette: ByteArray? = null
        val compressed = MemoryStream()
        while (true) {
            val chunk = readChunk(stream)
            when (chunk.type) {
                CHUNK_PALETTE -> palette = chunk.data
                CHUNK_IMAGE_DATA -> compressed.writeBytes(chunk.data)
                CHUNK_END -> break
                // ancillary chunks (gAMA, pHYs, tEXt ...) do not change the pixels
            }
        }
        if (colorType == ColorType.PALETTE && palette == null) throw IOException("Invalid PNG: palette image without PLTE")

        val bytesPerPixel = colorType.samplesPerPixel // one byte per sample at 8 bits
        val rowBytes = width * bytesPerPixel
        val raw = ZLib.uncompress(compressed.toByteArray())
        if (raw.size < (rowBytes + 1) * height) throw IOException("Invalid PNG: image data too short")

        val colors = IntArray(width * height)
        var previous = ByteArray(rowBytes)
        var current = ByteArray(rowBytes)
        for (y in 0 until height) {
            val rowStart = y * (rowBytes + 1)
            val filter = raw[rowStart].toInt() and 0xFF
            raw.copyInto(current, 0, rowStart + 1, rowStart + 1 + rowBytes)
            unfilter(filter, current, previous, bytesPerPixel)
            for (x in 0 until width) colors[y * width + x] = pixel(current, x * bytesPerPixel, colorType, palette)
            val swap = previous; previous = current; current = swap
        }
        val isGray = colorType == ColorType.GRAYSCALE || colorType == ColorType.GRAYSCALE_ALPHA
        return Image(width, height, if (isGray) ImageColorType.GRAY else ImageColorType.RGB, colors)
    }

    /** Undoes the row filter in place (section 9.2); bytes of the row above are in [above]. */
    private fun unfilter(filter: Int, row: ByteArray, above: ByteArray, bytesPerPixel: Int) {
        fun u(b: Byte) = b.toInt() and 0xFF
        for (i in row.indices) {
            val left = if (i >= bytesPerPixel) u(row[i - bytesPerPixel]) else 0
            val up = u(above[i])
            val upperLeft = if (i >= bytesPerPixel) u(above[i - bytesPerPixel]) else 0
            val prediction = when (filter) {
                FILTER_NONE -> 0
                FILTER_SUB -> left
                FILTER_UP -> up
                FILTER_AVERAGE -> (left + up) / 2
                FILTER_PAETH -> paethPredictor(left, up, upperLeft)
                else -> throw IOException("Invalid PNG: unknown filter type $filter")
            }
            row[i] = (u(row[i]) + prediction).toByte()
        }
    }

    private fun pixel(row: ByteArray, offset: Int, colorType: ColorType, palette: ByteArray?): Int {
        fun u(i: Int) = row[i].toInt() and 0xFF
        return when (colorType) {
            ColorType.GRAYSCALE, ColorType.GRAYSCALE_ALPHA -> u(offset)
            ColorType.RGB, ColorType.RGB_ALPHA -> (u(offset) shl 16) or (u(offset + 1) shl 8) or u(offset + 2)
            ColorType.PALETTE -> {
                val entry = 3 * u(offset)
                if (palette == null || entry + 2 >= palette.size) throw IOException("Invalid PNG: palette index out of range")
                ((palette[entry].toInt() and 0xFF) shl 16) or
                        ((palette[entry + 1].toInt() and 0xFF) shl 8) or
                        (palette[entry + 2].toInt() and 0xFF)
            }
        }
    }

    override fun write(image: Image): ByteArray {
        val isGray = image.colorType != ImageColorType.RGB
        val colorType = if (isGray) ColorType.GRAYSCALE else ColorType.RGB
        val header = buildBytes {
            write32BE(image.width)
            write32BE(image.height)
            write8(SUPPORTED_BIT_DEPTH)
            write8(colorType.code)
            write8(COMPRESSION_METHOD_DEFLATE)
            write8(FILTER_METHOD_ADAPTIVE)
            write8(INTERLACE_NONE)
        }
        // every row is written unfiltered; the compressor still finds the repeats within rows
        val raw = buildBytes {
            for (y in 0 until image.height) {
                write8(FILTER_NONE)
                for (x in 0 until image.width) {
                    val c = image[x, y]
                    when (image.colorType) {
                        ImageColorType.RGB -> { write8(c ushr 16); write8(c ushr 8); write8(c) }
                        ImageColorType.GRAY -> write8(c.coerceIn(0, 255))
                        ImageColorType.BINARY -> write8(if (c != 0) 255 else 0)
                    }
                }
            }
        }
        return buildBytes {
            writeBytes(SIGNATURE)
            writeChunk(this, CHUNK_HEADER, header)
            writeChunk(this, CHUNK_IMAGE_DATA, ZLib.compress(raw))
            writeChunk(this, CHUNK_END, ByteArray(0))
        }
    }

    private class Chunk(val type: String, val data: ByteArray)

    /** Reads one chunk and checks its CRC. */
    private fun readChunk(stream: MemoryStream): Chunk {
        val length = stream.readS32BE()
        if (length < 0) throw IOException("Invalid PNG: chunk length $length")
        val typeBytes = stream.readBytes(CHUNK_TYPE_BYTES)
        val data = stream.readBytes(length)
        val storedCrc = stream.readS32BE()
        val crc = CRC32.update(CRC32.update(CRC32.initialValue, typeBytes), data)
        val type = typeBytes.decodeToString()
        if (crc != storedCrc) throw IOException("Invalid PNG: CRC mismatch in chunk $type")
        return Chunk(type, data)
    }

    private fun writeChunk(stream: MemoryStream, type: String, data: ByteArray) {
        val typeBytes = type.encodeToByteArray()
        stream.write32BE(data.size)
        stream.writeBytes(typeBytes)
        stream.writeBytes(data)
        stream.write32BE(CRC32.update(CRC32.update(CRC32.initialValue, typeBytes), data))
    }
}

/**
 * The Paeth predictor (PNG section 9.4): of the left [a], upper [b] and upper-left [c] bytes, the
 * one closest to a + b - c; ties go to a, then b.
 */
internal fun paethPredictor(a: Int, b: Int, c: Int): Int {
    val estimate = a + b - c
    val distanceA = abs(estimate - a)
    val distanceB = abs(estimate - b)
    val distanceC = abs(estimate - c)
    return if (distanceA <= distanceB && distanceA <= distanceC) a else if (distanceB <= distanceC) b else c
}
