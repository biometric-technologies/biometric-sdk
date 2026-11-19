package net.iriscan.sdk.finger.record.iso

/*
 * In an ISO/IEC 19794-2 minutia, the X and Y coordinates are 14-bit numbers, each stored in a
 * 16-bit big-endian word. The 2 top bits of the X word hold the minutia type
 * (0 other, 1 ridge ending, 2 ridge bifurcation); the 2 top bits of the Y word are reserved.
 *
 *   X word: bit 15 14 = type, bits 13..0 = x
 *   Y word: bit 15 14 = reserved (0), bits 13..0 = y
 */

/** Which part of a minutia word [getMinutia] returns. */
const val MINUTIA_TYPE = 1
const val MINUTIA_POSITION = 2

private const val TYPE_SHIFT = 14
private const val TYPE_MASK = 0b11
private const val POSITION_MASK = (1 shl TYPE_SHIFT) - 1 // 0x3FFF, 14 bits

/**
 * Builds a minutia word from the 2-bit [value] (the minutia type, or 0 for the Y word) and the
 * 14-bit coordinate [pos].
 *
 * @author Anton Kurinnoy
 */
fun setMinutia(value: Int = 0, pos: Int): Int =
    ((value and TYPE_MASK) shl TYPE_SHIFT) or (pos and POSITION_MASK)

/**
 * Reads a part of the 16-bit minutia word [source]: [MINUTIA_TYPE] (the top 2 bits) or
 * [MINUTIA_POSITION] (the low 14 bits). Any other [value] gives 0.
 *
 * @author Anton Kurinnoy
 */
fun getMinutia(value: Int, source: Int): Int =
    when (value) {
        MINUTIA_TYPE -> (source ushr TYPE_SHIFT) and TYPE_MASK
        MINUTIA_POSITION -> source and POSITION_MASK
        else -> 0
    }
