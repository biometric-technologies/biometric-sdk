package net.iriscan.sdk.core.io

import net.iriscan.sdk.io.exception.IOException
import net.iriscan.sdk.testutil.hex
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MemoryStreamTest {

    @Test
    fun writesEachWidthInTheStatedByteOrder() {
        val bytes = buildBytes {
            write8(0x1FF)            // only the low 8 bits
            write16BE(0xABCD)
            write16LE(0xABCD)
            write24LE(0x123456)
            write32BE(0x01020304)
            write32LE(0x01020304)
            writeStringz("IIR")
        }
        assertContentEquals(hex("ff" + "abcd" + "cdab" + "563412" + "01020304" + "04030201" + "49495200"), bytes)
    }

    @Test
    fun readsUnsignedAndSignedValues() {
        val stream = MemoryStream(hex("ff" + "ff" + "fffe" + "feff" + "feff" + "fffeff" + "fffeff" + "feffffff" + "fffffffe" + "fffffffe"))
        assertEquals(255, stream.readU8())
        assertEquals(-1, stream.readS8())
        assertEquals(0xFFFE, stream.readU16BE())
        assertEquals(0xFFFE, stream.readU16LE())
        assertEquals(-2, stream.readS16LE())
        assertEquals(0xFFFEFF, stream.readU24LE())
        assertEquals(-257, stream.readS24LE()) // 0xFFFEFF sign-extended
        assertEquals(-2, stream.readS32LE())
        assertEquals(-2, stream.readS32BE())
        assertEquals(0xFFFFFFFEL, stream.readU32BE())
        assertEquals(0, stream.availableRead)
    }

    @Test
    fun readsWhatItWrote() {
        val written = MemoryStream()
        written.write16BE(0xABCD)
        written.write32LE(-2)
        written.writeStringz("FMR")
        written.write32BE(123456789)
        written.writeBytes(byteArrayOf(1, 2, 3))
        val read = MemoryStream(written.toByteArray())
        assertEquals(0xABCD, read.readU16BE())
        assertEquals(-2, read.readS32LE())
        assertEquals("FMR", read.readStringz(4))
        assertEquals(123456789, read.readS32BE())
        assertContentEquals(byteArrayOf(1, 2, 3), read.readBytes(3))
        assertEquals(0, read.availableRead)
    }

    @Test
    fun fixedSizeTextStopsAtTheFirstZeroButConsumesTheWholeField() {
        val stream = MemoryStream("IIR\u0000020\u0000".encodeToByteArray())
        assertEquals("IIR", stream.readStringz(4))
        assertEquals(4, stream.position)
        assertEquals("020", stream.readStringz(4))
    }

    @Test
    fun throwsIOExceptionAtTheEndOfData() {
        val stream = MemoryStream(hex("0102"))
        stream.skip(1)
        assertFailsWith<IOException> { stream.readU16BE() }
        assertFailsWith<IOException> { stream.readBytes(2) }
        assertFailsWith<IOException> { stream.skip(2) }
        assertFailsWith<IOException> { stream.readBytes(-1) }
        assertEquals(1, stream.position, "a failed read does not move the position")
    }

    @Test
    fun writingAtAnEarlierPositionOverwrites() {
        val stream = MemoryStream()
        stream.write32BE(0)
        stream.write8(9)
        stream.position = 0
        stream.write16BE(0x0102)
        assertContentEquals(hex("0102000009"), stream.toByteArray())
        assertEquals(5, stream.length)
    }

    @Test
    fun growsPastTheInitialBuffer() {
        val stream = MemoryStream(ByteArray(2))
        stream.position = 2
        repeat(1000) { stream.write8(it) }
        assertEquals(1002, stream.toByteArray().size)
        assertEquals((999 and 0xFF).toByte(), stream.toByteArray().last())
    }

    @Test
    fun byteArrayHelpers() {
        val bytes = hex("fffeff7f" + "0080")
        assertEquals(0xFFFEFF, bytes.readU24LE(0))
        assertEquals(-257, bytes.readS24LE(0))
        assertEquals(0x7FFFFEFF, bytes.readS32LE(0))
        assertEquals(0x0080, bytes.readU16BE(4))
    }
}
