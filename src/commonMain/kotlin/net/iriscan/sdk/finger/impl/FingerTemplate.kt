package net.iriscan.sdk.finger.impl

import net.iriscan.sdk.finger.impl.afis.FeatureMinutia
import net.iriscan.sdk.finger.impl.afis.FeatureTemplate
import net.iriscan.sdk.finger.impl.afis.IntPoint
import net.iriscan.sdk.finger.impl.afis.MinutiaType
import net.iriscan.sdk.finger.impl.afis.SearchTemplate
import kotlin.math.PI

/**
 * Serialized fingerprint template. Big-endian:
 * "FP1" | version(1) | width(2) | height(2) | count(2) | count x minutia
 *
 * Minutia: x(2, signed) | y(2, signed) | direction(4, IEEE float, radians 0..2pi) | type(1, 0 ending, 1 bifurcation)
 *
 * The minutiae are stored in SourceAFIS matching order, so a template read back matches exactly
 * as the one that was written.
 */
internal object FingerTemplate {
    private const val HEADER_SIZE = 10
    private const val MINUTIA_SIZE = 9
    private const val VERSION = 1

    fun isFingerTemplate(bytes: ByteArray): Boolean =
        bytes.size >= HEADER_SIZE && bytes[0] == 'F'.code.toByte() && bytes[1] == 'P'.code.toByte() &&
            bytes[2] == '1'.code.toByte()

    fun toBytes(template: SearchTemplate): ByteArray {
        val count = template.minutiae.size
        val out = ByteArray(HEADER_SIZE + count * MINUTIA_SIZE)
        out[0] = 'F'.code.toByte(); out[1] = 'P'.code.toByte(); out[2] = '1'.code.toByte()
        out[3] = VERSION.toByte()
        putShort(out, 4, template.width.toInt())
        putShort(out, 6, template.height.toInt())
        putShort(out, 8, count)
        var at = HEADER_SIZE
        for (m in template.minutiae) {
            putShort(out, at, m.x.toInt())
            putShort(out, at + 2, m.y.toInt())
            val bits = m.direction.toRawBits()
            for (k in 0 until 4) out[at + 4 + k] = (bits ushr (24 - 8 * k)).toByte()
            out[at + 8] = if (m.type == MinutiaType.BIFURCATION) 1 else 0
            at += MINUTIA_SIZE
        }
        return out
    }

    fun fromBytes(bytes: ByteArray): SearchTemplate {
        require(isFingerTemplate(bytes)) { "Not a fingerprint template" }
        require(bytes[3].toInt() == VERSION) { "Unsupported fingerprint template version ${bytes[3]}" }
        val width = getShort(bytes, 4) and 0xFFFF
        val height = getShort(bytes, 6) and 0xFFFF
        val count = getShort(bytes, 8) and 0xFFFF
        require(bytes.size == HEADER_SIZE + count * MINUTIA_SIZE) { "Corrupted fingerprint template" }
        val minutiae = ArrayList<FeatureMinutia>(count)
        var at = HEADER_SIZE
        repeat(count) {
            val x = getShort(bytes, at)
            val y = getShort(bytes, at + 2)
            var bits = 0
            for (k in 0 until 4) bits = (bits shl 8) or (bytes[at + 4 + k].toInt() and 0xFF)
            val direction = Float.fromBits(bits)
            require(kotlin.math.abs(x) <= 10_000 && kotlin.math.abs(y) <= 10_000) { "Minutia position out of range" }
            require(direction >= 0f && direction < (2 * PI).toFloat()) { "Minutia direction out of range" }
            val type = when (bytes[at + 8].toInt()) {
                0 -> MinutiaType.ENDING
                1 -> MinutiaType.BIFURCATION
                else -> throw IllegalArgumentException("Unknown minutia type")
            }
            minutiae.add(FeatureMinutia(IntPoint(x, y), direction, type))
            at += MINUTIA_SIZE
        }
        return SearchTemplate(FeatureTemplate(IntPoint(width, height), minutiae))
    }

    private fun putShort(out: ByteArray, at: Int, value: Int) {
        out[at] = (value ushr 8).toByte()
        out[at + 1] = value.toByte()
    }

    /** Signed 16-bit value. */
    private fun getShort(bytes: ByteArray, at: Int): Int =
        ((bytes[at].toInt() shl 8) or (bytes[at + 1].toInt() and 0xFF)).toShort().toInt()
}
