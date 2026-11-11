package net.iriscan.sdk.finger.impl

import net.iriscan.sdk.finger.impl.afis.DoubleAngle
import net.iriscan.sdk.finger.impl.afis.FeatureMinutia
import net.iriscan.sdk.finger.impl.afis.FeatureTemplate
import net.iriscan.sdk.finger.impl.afis.FloatAngle
import net.iriscan.sdk.finger.impl.afis.IntPoint
import net.iriscan.sdk.finger.impl.afis.MinutiaType
import net.iriscan.sdk.finger.impl.afis.SearchTemplate
import net.iriscan.sdk.finger.impl.afis.jround

/**
 * ISO/IEC 19794-2 finger minutiae records, the template format of the finger module, written and
 * read as SourceAFIS writes and reads them.
 *
 * Export writes the 2005 version, the one most systems read, byte for byte as
 * SourceAFIS `FingerprintCompatibility.exportTemplates(ISO_19794_2_2005, ...)`: one finger view,
 * finger position unknown, live-scan plain, finger quality 100, minutia quality 0, 500 dpi
 * (197 pixels per cm), no extended data.
 *
 * Import reads the 2005 and the 2011 versions, one template per finger view. Positions are
 * converted to 500 dpi, minutiae of type "other" are read as ridge endings, extended data (ridge
 * counts, cores and deltas) is skipped.
 */
internal object FingerIso {
    private val MAGIC_2005 = byteArrayOf('F'.code.toByte(), 'M'.code.toByte(), 'R'.code.toByte(), 0, ' '.code.toByte(), '2'.code.toByte(), '0'.code.toByte(), 0)
    private val MAGIC_2011 = byteArrayOf('F'.code.toByte(), 'M'.code.toByte(), 'R'.code.toByte(), 0, '0'.code.toByte(), '3'.code.toByte(), '0'.code.toByte(), 0)
    private const val PIXELS_PER_CM_AT_500_DPI = 197 // round(500 / 2.54)

    private const val TYPE_OTHER = 0
    private const val TYPE_ENDING = 1
    private const val TYPE_BIFURCATION = 2

    fun isIsoRecord(bytes: ByteArray) = startsWith(bytes, MAGIC_2005) || startsWith(bytes, MAGIC_2011)

    fun export2005(template: SearchTemplate): ByteArray {
        val minutiae = template.minutiae
        require(minutiae.size <= 255) { "ISO/IEC 19794-2 allows at most 255 minutiae" }
        val width = template.width.toInt()
        val height = template.height.toInt()
        val out = Writer(24 + 6 + 6 * minutiae.size)
        out.bytes(MAGIC_2005)
        out.u32(24 + 6 + 6 * minutiae.size)
        out.u16(0) // capture equipment: not certified, device type unknown
        out.u16(width)
        out.u16(height)
        out.u16(PIXELS_PER_CM_AT_500_DPI)
        out.u16(PIXELS_PER_CM_AT_500_DPI)
        out.u8(1) // finger views
        out.u8(0) // reserved
        out.u8(0) // finger position: unknown
        out.u8(0) // view 0, impression type: live-scan plain
        out.u8(100) // finger quality
        out.u8(minutiae.size)
        for (m in minutiae) {
            require(m.x in 0 until width && m.y in 0 until height && width < 0x4000 && height < 0x4000) {
                "Minutia outside the image"
            }
            val type = if (m.type == MinutiaType.BIFURCATION) TYPE_BIFURCATION else TYPE_ENDING
            out.u16((type shl 14) or m.x.toInt())
            out.u16(m.y.toInt())
            out.u8(encodeAngle(m.direction))
            out.u8(0) // minutia quality
        }
        out.u16(0) // extended data length
        return out.result()
    }

    fun import(bytes: ByteArray): List<SearchTemplate> = when {
        startsWith(bytes, MAGIC_2005) -> import2005(bytes)
        startsWith(bytes, MAGIC_2011) -> import2011(bytes)
        else -> throw IllegalArgumentException("Not an ISO/IEC 19794-2 record (2005 or 2011)")
    }

    private fun import2005(bytes: ByteArray): List<SearchTemplate> {
        val input = Reader(bytes)
        input.skip(8)
        val length = input.u32()
        require(length >= 24 && length <= bytes.size) { "Wrong record length" }
        input.skip(2) // capture equipment
        val width = input.u16()
        val height = input.u16()
        val resolutionX = input.u16()
        val resolutionY = input.u16()
        val views = input.u8()
        input.skip(1)
        return List(views) {
            input.skip(3) // finger position, view and impression type, finger quality
            val count = input.u8()
            val minutiae = List(count) { readMinutia(input, withQuality = true, resolutionX, resolutionY) }
            input.skip(input.u16()) // extended data
            template(width, height, resolutionX, resolutionY, minutiae)
        }
    }

    private fun import2011(bytes: ByteArray): List<SearchTemplate> {
        val input = Reader(bytes)
        input.skip(8)
        val length = input.u32()
        require(length >= 15 && length <= bytes.size) { "Wrong record length" }
        val views = input.u16()
        val certified = input.u8() != 0
        return List(views) {
            val start = input.position
            val representationLength = input.u32()
            input.skip(9) // capture date and time
            input.skip(5) // device technology, vendor, type
            input.skip(5 * input.u8()) // quality blocks
            if (certified) input.skip(3 * input.u8()) // certification blocks
            input.skip(2) // finger position, representation number
            val resolutionX = input.u16()
            val resolutionY = input.u16()
            input.skip(1) // impression type
            val width = input.u16()
            val height = input.u16()
            val minutiaBytes = input.u8() ushr 4
            require(minutiaBytes == 5 || minutiaBytes == 6) { "Minutia field length must be 5 or 6" }
            val count = input.u8()
            val minutiae = List(count) { readMinutia(input, minutiaBytes == 6, resolutionX, resolutionY) }
            input.skip(input.u16()) // extended data
            require(input.position - start == representationLength) { "Wrong representation length" }
            template(width, height, resolutionX, resolutionY, minutiae)
        }
    }

    private fun readMinutia(input: Reader, withQuality: Boolean, resolutionX: Int, resolutionY: Int): FeatureMinutia {
        val xWord = input.u16()
        val y = input.u16() and 0x3FFF
        val angle = input.u8()
        if (withQuality) input.skip(1)
        val type = if (xWord ushr 14 == TYPE_BIFURCATION) MinutiaType.BIFURCATION else MinutiaType.ENDING
        return FeatureMinutia(
            IntPoint(toPixels500(xWord and 0x3FFF, resolutionX), toPixels500(y, resolutionY)),
            decodeAngle(angle),
            type,
        )
    }

    private fun template(width: Int, height: Int, resolutionX: Int, resolutionY: Int, minutiae: List<FeatureMinutia>): SearchTemplate {
        require(resolutionX > 0 && resolutionY > 0) { "Resolution must not be zero" }
        return SearchTemplate(
            FeatureTemplate(IntPoint(toPixels500(width, resolutionX), toPixels500(height, resolutionY)), minutiae.toMutableList())
        )
    }

    /** SourceAFIS DpiConverter: pixels at the record resolution (pixels per cm) to pixels at 500 dpi. */
    private fun toPixels500(value: Int, pixelsPerCm: Int): Int = jround(value / (pixelsPerCm * 2.54) * 500)

    /** ISO angles count counterclockwise in 256 steps; SourceAFIS directions are clockwise radians. */
    private fun encodeAngle(direction: Float): Int =
        jround(DoubleAngle.complementary(direction.toDouble()) * DoubleAngle.INV_PI2 * 256) and 0xFF

    private fun decodeAngle(angle: Int): Float = FloatAngle.complementary(angle / 256.0f * FloatAngle.PI2)

    private fun startsWith(bytes: ByteArray, prefix: ByteArray) =
        bytes.size >= prefix.size && prefix.indices.all { bytes[it] == prefix[it] }

    private class Writer(size: Int) {
        private val out = ByteArray(size)
        private var at = 0
        fun bytes(value: ByteArray) { value.copyInto(out, at); at += value.size }
        fun u8(value: Int) { out[at++] = value.toByte() }
        fun u16(value: Int) { u8(value ushr 8); u8(value) }
        fun u32(value: Int) { u16(value ushr 16); u16(value) }
        fun result(): ByteArray { check(at == out.size); return out }
    }

    private class Reader(private val bytes: ByteArray) {
        var position = 0
            private set

        fun u8(): Int {
            require(position < bytes.size) { "Record is truncated" }
            return bytes[position++].toInt() and 0xFF
        }
        fun u16() = (u8() shl 8) or u8()
        fun u32() = (u16() shl 16) or u16()
        fun skip(count: Int) {
            require(count >= 0 && position + count <= bytes.size) { "Record is truncated" }
            position += count
        }
    }
}
