package net.iriscan.sdk.testutil

/** Bytes from hexadecimal text; whitespace is ignored, so long fixtures can be wrapped. */
internal fun hex(text: String): ByteArray {
    val digits = text.filterNot { it.isWhitespace() }
    require(digits.length % 2 == 0) { "Odd number of hex digits" }
    return ByteArray(digits.length / 2) { digits.substring(2 * it, 2 * it + 2).toInt(16).toByte() }
}

/** Deterministic test bytes: a cheap linear congruential sequence, the same on every platform. */
internal fun pseudoRandomBytes(size: Int, seed: Int): ByteArray {
    var state = seed
    return ByteArray(size) {
        state = state * 1_103_515_245 + 12_345
        (state ushr 16).toByte()
    }
}
