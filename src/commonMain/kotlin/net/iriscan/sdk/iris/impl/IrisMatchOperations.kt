package net.iriscan.sdk.iris.impl

/**
 * @author Slava Gornostal
 */
internal fun matchInternal(template1: ByteArray, template2: ByteArray): Double {
    check(template1.size == template2.size)
    var d = 0
    for (i in template1.indices) {
        d += when (template1[i] == template2[i]) {
            true -> 1
            false -> 0
        }
    }
    return d / template1.size.toDouble()
}