package net.iriscan.sdk.iris.record.iso

/*
 * The "image properties" byte of an ISO/IEC 19794-6 iris representation header packs three
 * fields into one byte:
 *
 *   bit:   7 6 | 5 4      | 3 2                  | 1 0
 *          compression | reserved | vertical orientation | horizontal orientation
 *          history
 *
 * Each field is 2 bits wide and holds the value of the matching enum
 * (HorizontalOrientation, VerticalOrientation, CompressionHistory).
 */

private const val FIELD_MASK = 0b11
private const val HORIZONTAL_ORIENTATION_SHIFT = 0
private const val VERTICAL_ORIENTATION_SHIFT = 2
private const val COMPRESSION_HISTORY_SHIFT = 6

private fun Properties.shift(): Int = when (this) {
    Properties.HORIZONTAL_ORIENTATION -> HORIZONTAL_ORIENTATION_SHIFT
    Properties.VERTICAL_ORIENTATION -> VERTICAL_ORIENTATION_SHIFT
    Properties.COMPRESSION_HISTORY -> COMPRESSION_HISTORY_SHIFT
}

/**
 * Extracts one 2-bit field from the image properties byte [source].
 *
 * @author Anton Kurinnoy
 */
fun getProperty(source: Int, pos: Properties): Int = (source ushr pos.shift()) and FIELD_MASK

/**
 * Packs the three 2-bit fields into an image properties byte; the reserved bits 4 and 5 are 0.
 * Values outside 0..3 are cut to their low 2 bits.
 *
 * @author Anton Kurinnoy
 */
fun setProperty(hor: Int = 0, ver: Int = 0, com: Int = 0): Int =
    ((hor and FIELD_MASK) shl HORIZONTAL_ORIENTATION_SHIFT) or
            ((ver and FIELD_MASK) shl VERTICAL_ORIENTATION_SHIFT) or
            ((com and FIELD_MASK) shl COMPRESSION_HISTORY_SHIFT)
