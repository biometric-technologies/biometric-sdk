package net.iriscan.sdk.iris.impl

/**
 * Iris code with occlusion mask.
 *
 * Bit layout: for row r and column c, bit (r * cols + c) * 2 is the sign of the
 * real part of the filter response and bit (r * cols + c) * 2 + 1 the sign of
 * the imaginary part. Each row occupies cols * 2 / 64 words, so a rotation of
 * the eye is a circular shift of every row.
 *
 * Serialized form (little-endian bit order inside bytes):
 * "IR2" | version(1) | rows(1) | cols/32(1) | bitsPerCell(1)=2 | reserved(1) | code bits | mask bits
 */
internal class IrisTemplate(val rows: Int, val cols: Int, val code: LongArray, val mask: LongArray) {
    val wordsPerRow = cols * 2 / 64
    val totalBits = rows * cols * 2

    init {
        require(cols % 32 == 0)
        require(code.size == rows * wordsPerRow && mask.size == code.size)
    }

    fun validBits(): Int = mask.sumOf { it.countOneBits() }

    fun toBytes(): ByteArray {
        val bytesPerPlane = totalBits / 8
        val out = ByteArray(HEADER_SIZE + 2 * bytesPerPlane)
        out[0] = 'I'.code.toByte(); out[1] = 'R'.code.toByte(); out[2] = '2'.code.toByte()
        out[3] = VERSION.toByte()
        out[4] = rows.toByte()
        out[5] = (cols / 32).toByte()
        out[6] = 2
        writePlane(code, out, HEADER_SIZE)
        writePlane(mask, out, HEADER_SIZE + bytesPerPlane)
        return out
    }

    /** Circular shift of all rows by [shift] columns (positive = towards higher angles). */
    fun shifted(shift: Int): IrisTemplate {
        if (shift % cols == 0) return this
        val bitsPerRow = cols * 2
        val s = ((shift % cols) + cols) % cols * 2
        fun shiftPlane(src: LongArray): LongArray {
            val dst = LongArray(src.size)
            for (r in 0 until rows) {
                val base = r * wordsPerRow
                for (b in 0 until bitsPerRow) {
                    val from = b
                    if ((src[base + from / 64] ushr (from % 64)) and 1L == 0L) continue
                    val to = (b + s) % bitsPerRow
                    dst[base + to / 64] = dst[base + to / 64] or (1L shl (to % 64))
                }
            }
            return dst
        }
        return IrisTemplate(rows, cols, shiftPlane(code), shiftPlane(mask))
    }

    companion object {
        const val HEADER_SIZE = 8
        const val VERSION = 1

        fun isV2(bytes: ByteArray): Boolean =
            bytes.size >= HEADER_SIZE && bytes[0] == 'I'.code.toByte() && bytes[1] == 'R'.code.toByte() &&
                    bytes[2] == '2'.code.toByte()

        fun fromBytes(bytes: ByteArray): IrisTemplate {
            require(isV2(bytes)) { "Not an iris template v2" }
            require(bytes[3].toInt() == VERSION) { "Unsupported iris template version ${bytes[3]}" }
            val rows = bytes[4].toInt() and 0xFF
            val cols = (bytes[5].toInt() and 0xFF) * 32
            require(bytes[6].toInt() == 2) { "Unsupported bits per cell" }
            val bytesPerPlane = rows * cols * 2 / 8
            require(bytes.size == HEADER_SIZE + 2 * bytesPerPlane) { "Corrupted iris template" }
            val words = rows * cols * 2 / 64
            return IrisTemplate(
                rows, cols,
                readPlane(bytes, HEADER_SIZE, words),
                readPlane(bytes, HEADER_SIZE + bytesPerPlane, words),
            )
        }

        private fun writePlane(plane: LongArray, out: ByteArray, offset: Int) {
            for (w in plane.indices) for (k in 0 until 8) {
                out[offset + w * 8 + k] = (plane[w] ushr (8 * k)).toByte()
            }
        }

        private fun readPlane(bytes: ByteArray, offset: Int, words: Int): LongArray = LongArray(words) { w ->
            var v = 0L
            for (k in 0 until 8) v = v or ((bytes[offset + w * 8 + k].toLong() and 0xFF) shl (8 * k))
            v
        }
    }
}
