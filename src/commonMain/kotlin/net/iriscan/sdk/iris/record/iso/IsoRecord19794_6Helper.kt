package net.iriscan.sdk.iris.record.iso

/**
 * @author Anton Kurinnoy
 */
fun getProperty(source: Int, pos: Properties): Int = when (pos) {
    Properties.HORIZONTAL_ORIENTATION -> source and 3
    Properties.VERTICAL_ORIENTATION -> source and 12 shr 2
    Properties.COMPRESSION_HISTORY -> source and 192 shr 6
}

fun setProperty(hor: Int = 0, ver: Int = 0, com: Int = 0): Int {
    val step1 = (3 shl 6) xor 207
    val step2 = (com shl 6) or step1

    val step3 = (3 shl 2) xor step2
    val step4 = (ver shl 2) or step3

    return (step4 xor 3) or hor
}