package net.iriscan.sdk.core.io

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Checksums against published test vectors. Every expected value was also checked with Python's
 * zlib and hashlib; JvmInteropTest compares the implementations with java.util.zip and
 * java.security.MessageDigest on random inputs.
 */
class ChecksumsTest {

    private fun ascii(text: String) = text.encodeToByteArray()

    @Test
    fun crc32MatchesStandardCheckValues() {
        assertEquals(0, CRC32.compute(ByteArray(0)))
        // "123456789" is the standard check input for CRC algorithms; 0xCBF43926 is CRC-32's check value
        assertEquals(0xCBF43926.toInt(), CRC32.compute(ascii("123456789")))
        assertEquals(0x352441C2, CRC32.compute(ascii("abc")))
        assertEquals(0x414FA339, CRC32.compute(ascii("The quick brown fox jumps over the lazy dog")))
    }

    @Test
    fun crc32CanBeComputedInParts() {
        val data = ascii("The quick brown fox jumps over the lazy dog")
        var running = CRC32.initialValue
        for (start in data.indices step 7) {
            running = CRC32.update(running, data, start, minOf(7, data.size - start))
        }
        assertEquals(CRC32.compute(data), running)
    }

    @Test
    fun crc32RejectsRangeOutsideArray() {
        assertFailsWith<IllegalArgumentException> { CRC32.update(0, ByteArray(4), 2, 3) }
    }

    @Test
    fun adler32MatchesKnownValues() {
        assertEquals(1, adler32(ByteArray(0)))
        assertEquals(0x00620062, adler32(ascii("a")))
        assertEquals(0x11E60398, adler32(ascii("Wikipedia")))
        assertEquals(0x091E01DE, adler32(ascii("123456789")))
    }

    @Test
    fun adler32ReducesLargeSumsCorrectly() {
        // 100 000 bytes of 0xFF make both sums wrap the modulus many times
        assertEquals(0x149A302C, adler32(ByteArray(100_000) { 0xFF.toByte() }))
    }

    @Test
    fun sha256MatchesFips180Examples() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", SHA256.digest(ByteArray(0)).toHexLower())
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", SHA256.digest(ascii("abc")).toHexLower())
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            SHA256.digest(ascii("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq")).toHexLower()
        )
    }

    @Test
    fun sha256HandlesEveryPaddingCase() {
        // 55 bytes: padding fits in the block; 56: the length field spills into a second block;
        // 63, 64, 65: around one full block; 119, 120: the same around two blocks
        val expected = mapOf(
            55 to "463eb28e72f82e0a96c0a4cc53690c571281131f672aa229e0d45ae59b598b59",
            56 to "da2ae4d6b36748f2a318f23e7ab1dfdf45acdc9d049bd80e59de82a60895f562",
            63 to "29af2686fd53374a36b0846694cc342177e428d1647515f078784d69cdb9e488",
            64 to "fdeab9acf3710362bd2658cdc9a29e8f9c757fcf9811603a8c447cd1d9151108",
            65 to "4bfd2c8b6f1eec7a2afeb48b934ee4b2694182027e6d0fc075074f2fabb31781",
            119 to "da18797ed7c3a777f0847f429724a2d8cd5138e6ed2895c3fa1a6d39d18f7ec6",
            120 to "f52b23db1fbb6ded89ef42a23ce0c8922c45f25c50b568a93bf1c075420bbb7c",
        )
        for ((length, digest) in expected) {
            val data = ByteArray(length) { (it % 251).toByte() }
            assertEquals(digest, SHA256.digest(data).toHexLower(), "length $length")
        }
    }

    @Test
    fun sha256OfOneMillionLetters() {
        // the long test vector of FIPS 180-2: one million repetitions of "a"
        assertEquals(
            "cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0",
            SHA256.digest(ByteArray(1_000_000) { 'a'.code.toByte() }).toHexLower()
        )
    }

    @Test
    fun hashTextKeepsTheFormatOfEarlierVersions() {
        // model checksum files written by earlier versions must still match
        assertEquals("352441c2", calculateHash(ascii("abc"), HashMethod.CRC32))
        // a CRC with the top bit set is written as a negative number, like Int.toString(16)
        assertEquals((0xCBF43926.toInt()).toString(16), calculateHash(ascii("123456789"), HashMethod.CRC32))
        assertEquals("-340bc6da", calculateHash(ascii("123456789"), HashMethod.CRC32))
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            calculateHash(ascii("abc"), HashMethod.SHA256)
        )
    }

    @Test
    fun hexTextIsLowercaseWithLeadingZeros() {
        assertEquals("00ff0a7f80", byteArrayOf(0, -1, 10, 127, -128).toHexLower())
    }
}
