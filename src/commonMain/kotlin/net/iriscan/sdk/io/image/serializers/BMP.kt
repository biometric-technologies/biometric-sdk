package net.iriscan.sdk.io.image.serializers

import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.image.ImageColorType
import net.iriscan.sdk.core.image.toRgb
import net.iriscan.sdk.core.io.MemoryStream
import net.iriscan.sdk.core.io.buildBytes
import net.iriscan.sdk.io.exception.IOException
import net.iriscan.sdk.io.image.ImageFormat
import net.iriscan.sdk.io.image.ImageSerializer

/**
 * Windows bitmap (BMP) reader and writer.
 *
 * Reads uncompressed (BI_RGB) bitmaps with 8 bits per pixel and a palette, 24 bits (BGR) or 32 bits
 * (BGRX) per pixel, stored bottom-up (positive height) or top-down (negative height), with any
 * info header from BITMAPINFOHEADER (40 bytes) onwards. An 8-bit bitmap whose palette is all gray,
 * as iris cameras and datasets usually write, is returned as [ImageColorType.GRAY]; all others as
 * [ImageColorType.RGB] (0xRRGGBB per pixel).
 *
 * Writes 24-bit bottom-up bitmaps with a BITMAPINFOHEADER.
 *
 * Layout: a 14-byte file header ("BM", file size, reserved, offset of the pixels), an info header
 * (its size, width, height, planes, bits per pixel, compression, ...), an optional palette, then
 * the pixel rows. All numbers are little-endian, and every row is padded to a multiple of 4 bytes.
 *
 * @author Slava Gornostal
 */
internal object BMP : ImageSerializer {

    private const val SIGNATURE = "BM"
    private const val FILE_HEADER_BYTES = 14

    /** BITMAPINFOHEADER; later versions (V4: 108, V5: 124 bytes) only add fields at the end. */
    private const val INFO_HEADER_BYTES = 40

    private const val PIXEL_DATA_OFFSET = FILE_HEADER_BYTES + INFO_HEADER_BYTES

    /** Compression value for uncompressed pixels. */
    private const val BI_RGB = 0

    private const val PLANES = 1
    private const val PALETTE_ENTRY_BYTES = 4 // blue, green, red, reserved
    private const val MAX_PALETTE_ENTRIES = 256
    private const val ROW_ALIGNMENT_BYTES = 4

    private const val BITS_PER_PIXEL_PALETTE = 8
    private const val BITS_PER_PIXEL_BGR = 24
    private const val BITS_PER_PIXEL_BGRX = 32

    override val format: ImageFormat
        get() = ImageFormat.BMP

    override fun canRead(data: ByteArray): Boolean =
        data.size > PIXEL_DATA_OFFSET && data[0] == 'B'.code.toByte() && data[1] == 'M'.code.toByte()

    override fun read(data: ByteArray): Image {
        if (!canRead(data)) throw IOException("Not a BMP file")
        val stream = MemoryStream(data)
        // file header: signature (2), file size (4), reserved (4), pixel data offset (4)
        stream.skip(SIGNATURE.length + 4 + 4)
        val pixelDataOffset = stream.readS32LE()
        // info header
        val infoHeaderSize = stream.readS32LE()
        if (infoHeaderSize < INFO_HEADER_BYTES) throw IOException("Unsupported BMP: OS/2 header of $infoHeaderSize bytes")
        val width = stream.readS32LE()
        val signedHeight = stream.readS32LE()
        stream.skip(2) // planes, always 1
        val bitsPerPixel = stream.readU16LE()
        val compression = stream.readS32LE()
        stream.skip(4 + 4 + 4) // image size (may be 0 for BI_RGB), horizontal and vertical resolution
        val paletteSize = stream.readS32LE()
        stream.skip(4) // important colors
        if (compression != BI_RGB) throw IOException("Unsupported BMP compression $compression, only uncompressed is supported")
        if (width <= 0 || signedHeight == 0) throw IOException("Invalid BMP size ${width}x$signedHeight")

        val palette = if (bitsPerPixel == BITS_PER_PIXEL_PALETTE) {
            stream.position = FILE_HEADER_BYTES + infoHeaderSize
            val entries = if (paletteSize in 1..MAX_PALETTE_ENTRIES) paletteSize else MAX_PALETTE_ENTRIES
            IntArray(entries) {
                val blue = stream.readU8()
                val green = stream.readU8()
                val red = stream.readU8()
                stream.skip(1)
                (red shl 16) or (green shl 8) or blue
            }
        } else null

        val height = kotlin.math.abs(signedHeight)
        val isTopDown = signedHeight < 0
        val bytesPerPixel = when (bitsPerPixel) {
            BITS_PER_PIXEL_PALETTE -> 1
            BITS_PER_PIXEL_BGR -> 3
            BITS_PER_PIXEL_BGRX -> 4
            else -> throw IOException("Unsupported BMP: $bitsPerPixel bits per pixel")
        }
        val rowBytes = paddedRowBytes(width, bitsPerPixel)
        if (pixelDataOffset < 0 || pixelDataOffset.toLong() + rowBytes.toLong() * height > data.size) {
            throw IOException("Unexpected end of BMP data")
        }
        stream.position = pixelDataOffset

        val colors = IntArray(width * height)
        for (fileRow in 0 until height) {
            val y = if (isTopDown) fileRow else height - 1 - fileRow
            val rowStart = stream.position
            for (x in 0 until width) {
                colors[y * width + x] = when (bytesPerPixel) {
                    1 -> {
                        val index = stream.readU8()
                        if (palette == null || index >= palette.size) throw IOException("Invalid BMP: palette index $index")
                        palette[index]
                    }
                    else -> {
                        val bgr = stream.readU24LE() // blue in the low byte: 0xRRGGBB
                        if (bytesPerPixel == 4) stream.skip(1)
                        bgr
                    }
                }
            }
            stream.position = rowStart + rowBytes
        }
        val isGrayPalette = palette != null && palette.all { (it ushr 16) == (it and 0xFF) && ((it ushr 8) and 0xFF) == (it and 0xFF) }
        if (isGrayPalette) {
            for (i in colors.indices) colors[i] = colors[i] and 0xFF
            return Image(width, height, ImageColorType.GRAY, colors)
        }
        return Image(width, height, ImageColorType.RGB, colors)
    }

    override fun write(image: Image): ByteArray = buildBytes {
        val rowBytes = paddedRowBytes(image.width, BITS_PER_PIXEL_BGR)
        val pixelBytes = rowBytes * image.height
        val padding = ByteArray(rowBytes - image.width * 3)
        // file header
        write8(SIGNATURE[0].code)
        write8(SIGNATURE[1].code)
        write32LE(PIXEL_DATA_OFFSET + pixelBytes)
        write32LE(0) // reserved
        write32LE(PIXEL_DATA_OFFSET)
        // BITMAPINFOHEADER
        write32LE(INFO_HEADER_BYTES)
        write32LE(image.width)
        write32LE(image.height) // positive: rows stored bottom-up
        write16LE(PLANES)
        write16LE(BITS_PER_PIXEL_BGR)
        write32LE(BI_RGB)
        write32LE(pixelBytes)
        write32LE(0) // horizontal resolution, not specified
        write32LE(0) // vertical resolution, not specified
        write32LE(0) // palette size: no palette
        write32LE(0) // important colors: all
        for (y in image.height - 1 downTo 0) {
            for (x in 0 until image.width) write24LE(toRgb(image[x, y], image.colorType))
            writeBytes(padding)
        }
    }

    /** Bytes per stored row: the pixels rounded up to a multiple of 4 bytes. */
    private fun paddedRowBytes(width: Int, bitsPerPixel: Int): Int {
        val bytes = (width * bitsPerPixel + 7) / 8
        return (bytes + ROW_ALIGNMENT_BYTES - 1) / ROW_ALIGNMENT_BYTES * ROW_ALIGNMENT_BYTES
    }
}
