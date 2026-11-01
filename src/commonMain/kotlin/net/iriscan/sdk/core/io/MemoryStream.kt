package net.iriscan.sdk.core.io

import net.iriscan.sdk.io.exception.IOException

/**
 * In-memory binary stream for the image (PNG, BMP) and ISO record serializers.
 *
 * Reading goes forward from [position] through the bytes the stream was created with; every read
 * checks that enough bytes are left and throws [IOException] otherwise, so a truncated file gives a
 * clear error instead of an index exception. Writing goes to [position] as well and grows the
 * buffer as needed.
 *
 * Method names say the width and the byte order: `U8` is an unsigned byte, `S16LE` a signed 16-bit
 * little-endian number, `U16BE` an unsigned 16-bit big-endian number, and so on. BMP is
 * little-endian; PNG and the ISO/IEC 19794 records are big-endian.
 */
internal class MemoryStream(initial: ByteArray = ByteArray(0)) {

    private var buffer: ByteArray = initial
    private var size: Int = initial.size

    /** Index of the next byte to read or write. */
    var position: Int = 0

    /** Number of bytes in the stream. */
    val length: Int get() = size

    /** Number of bytes between [position] and the end. */
    val availableRead: Int get() = size - position

    // ---- reading ----

    private fun requireAvailable(count: Int) {
        if (count < 0 || position + count > size) {
            throw IOException("Unexpected end of data: need $count bytes at $position, have ${size - position}")
        }
    }

    fun skip(count: Int) {
        requireAvailable(count)
        position += count
    }

    fun readU8(): Int {
        requireAvailable(1)
        return buffer[position++].toInt() and 0xFF
    }

    fun readS8(): Int {
        requireAvailable(1)
        return buffer[position++].toInt()
    }

    fun readU16BE(): Int {
        requireAvailable(2)
        val value = buffer.readU16BE(position)
        position += 2
        return value
    }

    fun readU16LE(): Int {
        requireAvailable(2)
        val value = (buffer[position].toInt() and 0xFF) or ((buffer[position + 1].toInt() and 0xFF) shl 8)
        position += 2
        return value
    }

    fun readS16LE(): Int = readU16LE().toShort().toInt()

    fun readU24LE(): Int {
        requireAvailable(3)
        val value = buffer.readU24LE(position)
        position += 3
        return value
    }

    fun readS24LE(): Int = (readU24LE() shl 8) shr 8

    fun readS32LE(): Int {
        requireAvailable(4)
        val value = buffer.readS32LE(position)
        position += 4
        return value
    }

    fun readS32BE(): Int {
        requireAvailable(4)
        val value = (buffer.readU16BE(position) shl 16) or buffer.readU16BE(position + 2)
        position += 4
        return value
    }

    fun readU32BE(): Long = readS32BE().toLong() and 0xFFFFFFFFL

    fun readBytes(count: Int): ByteArray {
        requireAvailable(count)
        val out = buffer.copyOfRange(position, position + count)
        position += count
        return out
    }

    fun readUByteArray(count: Int): UByteArray = readBytes(count).asUByteArray()

    /**
     * Reads a fixed-size text field of [count] bytes, as used by the ISO records ("IIR\u0000"):
     * returns the UTF-8 text before the first zero byte, and always advances by [count] bytes.
     */
    fun readStringz(count: Int): String {
        val bytes = readBytes(count)
        val end = bytes.indexOf(0).let { if (it < 0) bytes.size else it }
        return bytes.decodeToString(0, end)
    }

    // ---- writing ----

    private fun ensureCapacity(extra: Int) {
        val needed = position + extra
        if (needed > buffer.size) {
            buffer = buffer.copyOf(maxOf(needed, buffer.size * 2, MIN_GROWTH_BYTES))
        }
    }

    private fun put(byte: Int) {
        ensureCapacity(1)
        buffer[position++] = byte.toByte()
        if (position > size) size = position
    }

    /** Writes the low 8 bits of [value]. */
    fun write8(value: Int) = put(value)

    /** Writes the low 16 bits of [value], most significant byte first. */
    fun write16BE(value: Int) {
        put(value ushr 8); put(value)
    }

    /** Writes the low 16 bits of [value], least significant byte first. */
    fun write16LE(value: Int) {
        put(value); put(value ushr 8)
    }

    /** Writes the low 24 bits of [value], least significant byte first. */
    fun write24LE(value: Int) {
        put(value); put(value ushr 8); put(value ushr 16)
    }

    fun write32BE(value: Int) {
        put(value ushr 24); put(value ushr 16); put(value ushr 8); put(value)
    }

    fun write32LE(value: Int) {
        put(value); put(value ushr 8); put(value ushr 16); put(value ushr 24)
    }

    fun writeBytes(bytes: ByteArray) {
        ensureCapacity(bytes.size)
        bytes.copyInto(buffer, position)
        position += bytes.size
        if (position > size) size = position
    }

    /** Writes the text as UTF-8 followed by a zero byte. */
    fun writeStringz(text: String) {
        writeBytes(text.encodeToByteArray())
        put(0)
    }

    /** All bytes in the stream, independent of the current position. */
    fun toByteArray(): ByteArray = buffer.copyOf(size)

    private companion object {
        /** Smallest buffer allocated when the stream starts to grow, to avoid many tiny copies. */
        const val MIN_GROWTH_BYTES = 64
    }
}

/** Builds a byte array by writing to a new [MemoryStream]. */
internal inline fun buildBytes(block: MemoryStream.() -> Unit): ByteArray =
    MemoryStream().apply(block).toByteArray()

internal fun ByteArray.readU16BE(offset: Int): Int =
    ((this[offset].toInt() and 0xFF) shl 8) or (this[offset + 1].toInt() and 0xFF)

/** Unsigned 24-bit little-endian value: 0 .. 0xFFFFFF. */
internal fun ByteArray.readU24LE(offset: Int): Int =
    (this[offset].toInt() and 0xFF) or
            ((this[offset + 1].toInt() and 0xFF) shl 8) or
            ((this[offset + 2].toInt() and 0xFF) shl 16)

/** Signed 24-bit little-endian value: bit 23 is copied into bits 24..31. */
internal fun ByteArray.readS24LE(offset: Int): Int = (readU24LE(offset) shl 8) shr 8

internal fun ByteArray.readS32LE(offset: Int): Int =
    (this[offset].toInt() and 0xFF) or
            ((this[offset + 1].toInt() and 0xFF) shl 8) or
            ((this[offset + 2].toInt() and 0xFF) shl 16) or
            ((this[offset + 3].toInt() and 0xFF) shl 24)
