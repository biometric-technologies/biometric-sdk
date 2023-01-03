package net.iriscan.sdk.finger.record.iso

/**
 * @author Anton Kurinnoy
 */
fun setMinutia(value: Int = 0, pos: Int): Int = (value shl 14) + pos

fun getMinutia(value: Int, source: Int): Int =
    when (value) {
        1 -> source and 49152 shr 14
        2 -> source and 16383
        else -> 0
    }
