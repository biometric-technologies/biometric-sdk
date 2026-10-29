package net.iriscan.sdk.io

import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.image.ImageColorType
import net.iriscan.sdk.core.io.CRC32
import net.iriscan.sdk.core.io.HashMethod
import net.iriscan.sdk.core.io.MemoryStream
import net.iriscan.sdk.core.io.SHA256
import net.iriscan.sdk.core.io.ZLib
import net.iriscan.sdk.core.io.calculateHash
import net.iriscan.sdk.core.io.toHexLower
import net.iriscan.sdk.io.image.serializers.BMP
import net.iriscan.sdk.io.image.serializers.PNG
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BinaryIOTest {

    @Test
    fun knownDigests() {
        val abc = "abc".encodeToByteArray()
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", SHA256.digest(abc).toHexLower())
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            SHA256.digest(ByteArray(0)).toHexLower()
        )
        assertEquals(0x352441C2, CRC32.compute(abc))
        assertEquals("352441c2", calculateHash(abc, HashMethod.CRC32))
    }

    @Test
    fun zlibRoundTrip() {
        val rnd = Random(3)
        val samples = listOf(
            ByteArray(0),
            "iris iris iris iris iris".encodeToByteArray(),
            ByteArray(200_000) { (it % 97).toByte() },
            ByteArray(80_000) { rnd.nextInt(256).toByte() },
        )
        for (s in samples) {
            assertContentEquals(s, ZLib.uncompress(ZLib.compress(s)))
        }
        // Stream produced by standard zlib (python zlib.compress(b"hello hello hello"))
        val reference = byteArrayOf(
            0x78, 0x9C.toByte(), 0xCB.toByte(), 0x48, 0xCD.toByte(), 0xC9.toByte(), 0xC9.toByte(), 0x57, 0xC8.toByte(),
            0x40, 0x90.toByte(), 0x00, 0x3A, 0x2E, 0x06, 0x7D
        )
        assertEquals("hello hello hello", ZLib.uncompress(reference).decodeToString())
    }

    @Test
    fun streamReadsWhatItWrites() {
        val s = MemoryStream()
        s.write16BE(0xABCD)
        s.write32LE(-2)
        s.writeStringz("FMR")
        s.write32BE(123456789)
        val r = MemoryStream(s.toByteArray())
        assertEquals(0xABCD, r.readU16BE())
        assertEquals(-2, r.readS32LE())
        assertEquals("FMR", r.readStringz(4))
        assertEquals(123456789, r.readS32BE())
        assertEquals(0, r.availableRead)
    }

    @Test
    fun pngAndBmpRoundTrip() {
        val rnd = Random(5)
        val w = 37
        val h = 21
        // colors as the readers return them: 24-bit values, sign-extended
        val colors = IntArray(w * h) { (rnd.nextInt(0x1000000) shl 8) shr 8 }
        val image = Image(w, h, ImageColorType.RGB, colors)
        for (serializer in listOf(PNG, BMP)) {
            val bytes = serializer.write(image)
            assertTrue(serializer.canRead(bytes))
            val back = serializer.read(bytes)
            assertEquals(w, back.width)
            assertEquals(h, back.height)
            assertContentEquals(colors, back.colors, "${serializer.format} pixels differ")
        }
    }
}
