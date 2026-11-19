package net.iriscan.sdk.core.io

/*
 * Checksums and hashes in pure Kotlin.
 *
 * Kotlin common code has no CRC, Adler or SHA implementation in its standard library, and the SDK
 * builds for JVM, Android and iOS from the same sources. Earlier versions took these algorithms
 * from korio and krypto (com.soywiz), which are no longer maintained and are not built for
 * Kotlin 2; these small implementations replace them, so that PNG files, zlib streams and model
 * checksums give the same result on every platform. Each algorithm follows its specification
 * literally; the tests compare them with published test vectors and, on the JVM, with
 * java.util.zip and java.security.MessageDigest.
 */

/**
 * CRC-32 as used by PNG (ISO/IEC 15948), zlib's gzip wrapper and Ethernet (IEEE 802.3).
 *
 * The bits of every byte are processed least significant first, which is why the polynomial is
 * used in its bit-reversed form. The register starts as all ones and the result is inverted;
 * [update] hides both inversions, so a running CRC can be passed from one call to the next.
 */
internal object CRC32 {

    /** x^32 + x^26 + x^23 + x^22 + x^16 + x^12 + x^11 + x^10 + x^8 + x^7 + x^5 + x^4 + x^2 + x + 1, bit-reversed. */
    private const val REVERSED_POLYNOMIAL: Int = 0xEDB88320.toInt()

    /** CRC of every possible byte value: lets [update] process a whole byte per step instead of a bit. */
    private val tableByByte = IntArray(256) { byte ->
        var crc = byte
        repeat(Byte.SIZE_BITS) {
            crc = if (crc and 1 != 0) (crc ushr 1) xor REVERSED_POLYNOMIAL else crc ushr 1
        }
        crc
    }

    /** CRC of no data; the value to start a running CRC with. */
    const val initialValue: Int = 0

    /** Continues the running [crc] over [length] bytes of [data] starting at [offset]. */
    fun update(crc: Int, data: ByteArray, offset: Int = 0, length: Int = data.size - offset): Int {
        require(offset >= 0 && length >= 0 && offset + length <= data.size) {
            "Range $offset..${offset + length} is outside the array of ${data.size} bytes"
        }
        var register = crc.inv()
        for (i in offset until offset + length) {
            register = tableByByte[(register xor data[i].toInt()) and 0xFF] xor (register ushr 8)
        }
        return register.inv()
    }

    /** CRC-32 of the whole array. */
    fun compute(data: ByteArray): Int = update(initialValue, data)
}

/**
 * Adler-32 (RFC 1950, section 8.2), the checksum at the end of every zlib stream.
 *
 * Two running sums are kept modulo [ADLER32_MODULUS]: `a` is 1 plus the sum of all bytes, `b` is
 * the sum of all intermediate values of `a`. The result is `b` in the high 16 bits and `a` in the
 * low 16 bits.
 */
internal fun adler32(data: ByteArray): Int {
    var a = 1L
    var b = 0L
    var i = 0
    while (i < data.size) {
        // reducing modulo the prime only once per block is what makes Adler-32 fast
        val blockEnd = minOf(i + ADLER32_BYTES_BETWEEN_REDUCTIONS, data.size)
        while (i < blockEnd) {
            a += data[i].toInt() and 0xFF
            b += a
            i++
        }
        a %= ADLER32_MODULUS
        b %= ADLER32_MODULUS
    }
    return ((b shl 16) or a).toInt()
}

/** The largest prime below 2^16 (RFC 1950). */
private const val ADLER32_MODULUS = 65521L

/**
 * How many bytes can be summed before the sums must be reduced modulo [ADLER32_MODULUS]. zlib uses
 * 5552, the largest count for which `b` cannot exceed 2^32 - 1; the sums here are 64-bit, so the
 * same value is safe with a wide margin.
 */
private const val ADLER32_BYTES_BETWEEN_REDUCTIONS = 5552

/**
 * SHA-256 (FIPS 180-4, section 6.2). Used to verify downloaded model files.
 *
 * The message is padded to a multiple of 512 bits: a single 1 bit, zero bits, and the message
 * length in bits as a 64-bit big-endian number. Every 512-bit block is expanded to a 64-word
 * schedule and mixed into the 8-word state in 64 rounds.
 */
internal object SHA256 {

    private const val BLOCK_BYTES = 64
    private const val ROUNDS = 64
    private const val DIGEST_BYTES = 32

    /** The padding starts with a single 1 bit right after the message. */
    private const val PADDING_FIRST_BYTE = 0x80

    /** The message length in bits is stored in the last 8 bytes of the padding. */
    private const val LENGTH_FIELD_BYTES = 8

    /** First 32 bits of the fractional parts of the square roots of the first 8 primes (FIPS 180-4, 5.3.3). */
    private val INITIAL_HASH: IntArray = longArrayOf(
        0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19,
    ).toIntArray()

    /** First 32 bits of the fractional parts of the cube roots of the first 64 primes (FIPS 180-4, 4.2.2). */
    private val ROUND_CONSTANTS: IntArray = longArrayOf(
        0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
        0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
        0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
        0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
    ).toIntArray()

    private fun LongArray.toIntArray() = IntArray(size) { this[it].toInt() }

    fun digest(data: ByteArray): ByteArray {
        val state = INITIAL_HASH.copyOf()
        val schedule = IntArray(ROUNDS)
        val padded = pad(data)
        for (blockStart in padded.indices step BLOCK_BYTES) {
            fillSchedule(padded, blockStart, schedule)
            compress(state, schedule)
        }
        val digest = ByteArray(DIGEST_BYTES)
        for (i in state.indices) digest.putIntBE(4 * i, state[i])
        return digest
    }

    /** The message, a 1 bit, zero bits and the 64-bit length, in whole blocks (FIPS 180-4, 5.1.1). */
    private fun pad(data: ByteArray): ByteArray {
        val minimumLength = data.size + 1 + LENGTH_FIELD_BYTES
        val padded = ByteArray((minimumLength + BLOCK_BYTES - 1) / BLOCK_BYTES * BLOCK_BYTES)
        data.copyInto(padded)
        padded[data.size] = PADDING_FIRST_BYTE.toByte()
        val bitLength = data.size.toLong() * Byte.SIZE_BITS
        for (i in 0 until LENGTH_FIELD_BYTES) {
            padded[padded.size - 1 - i] = (bitLength ushr (Byte.SIZE_BITS * i)).toByte()
        }
        return padded
    }

    /** The 16 big-endian words of the block, extended to 64 words (FIPS 180-4, 6.2.2 step 1). */
    private fun fillSchedule(padded: ByteArray, blockStart: Int, w: IntArray) {
        for (t in 0 until 16) w[t] = padded.getIntBE(blockStart + 4 * t)
        for (t in 16 until ROUNDS) {
            val sigma0 = w[t - 15].rotateRight(7) xor w[t - 15].rotateRight(18) xor (w[t - 15] ushr 3)
            val sigma1 = w[t - 2].rotateRight(17) xor w[t - 2].rotateRight(19) xor (w[t - 2] ushr 10)
            w[t] = w[t - 16] + sigma0 + w[t - 7] + sigma1
        }
    }

    /** 64 rounds over one block (FIPS 180-4, 6.2.2 steps 2-4). Additions wrap modulo 2^32 like Int does. */
    private fun compress(state: IntArray, w: IntArray) {
        var a = state[0]; var b = state[1]; var c = state[2]; var d = state[3]
        var e = state[4]; var f = state[5]; var g = state[6]; var h = state[7]
        for (t in 0 until ROUNDS) {
            val bigSigma1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
            val choose = (e and f) xor (e.inv() and g)
            val temp1 = h + bigSigma1 + choose + ROUND_CONSTANTS[t] + w[t]
            val bigSigma0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
            val majority = (a and b) xor (a and c) xor (b and c)
            val temp2 = bigSigma0 + majority
            h = g; g = f; f = e; e = d + temp1
            d = c; c = b; b = a; a = temp1 + temp2
        }
        state[0] += a; state[1] += b; state[2] += c; state[3] += d
        state[4] += e; state[5] += f; state[6] += g; state[7] += h
    }

    private fun ByteArray.getIntBE(offset: Int): Int =
        ((this[offset].toInt() and 0xFF) shl 24) or
                ((this[offset + 1].toInt() and 0xFF) shl 16) or
                ((this[offset + 2].toInt() and 0xFF) shl 8) or
                (this[offset + 3].toInt() and 0xFF)

    private fun ByteArray.putIntBE(offset: Int, value: Int) {
        this[offset] = (value ushr 24).toByte()
        this[offset + 1] = (value ushr 16).toByte()
        this[offset + 2] = (value ushr 8).toByte()
        this[offset + 3] = value.toByte()
    }
}

/** Lowercase hexadecimal text, two digits per byte. */
internal fun ByteArray.toHexLower(): String {
    val digits = "0123456789abcdef"
    val text = StringBuilder(size * 2)
    for (byte in this) {
        text.append(digits[(byte.toInt() ushr 4) and 0xF])
        text.append(digits[byte.toInt() and 0xF])
    }
    return text.toString()
}

/**
 * Hash as text, in the format earlier SDK versions produced and model checksum files contain.
 *
 * For CRC-32 this is the signed 32-bit value in hexadecimal without leading zeros, so a CRC with
 * the top bit set is written with a minus sign (for example "-2a5c0a3b"); this matches
 * `Int.toString(16)` used by the earlier versions.
 */
internal fun calculateHash(data: ByteArray, method: HashMethod): String = when (method) {
    HashMethod.CRC32 -> CRC32.compute(data).toString(16)
    HashMethod.SHA256 -> SHA256.digest(data).toHexLower()
}
