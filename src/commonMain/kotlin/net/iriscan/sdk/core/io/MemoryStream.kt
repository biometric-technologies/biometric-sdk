package net.iriscan.sdk.core.io

import net.iriscan.sdk.io.exception.IOException

/**
 * In-memory binary stream used by the image and ISO record serializers:
 * sequential reads from a byte array, or appending writes to a growing buffer.
 */
internal class MemoryStream(initial: ByteArray = ByteArray(0)) {

    private var buffer: ByteArray = initial
    private var size: Int = initial.size

    var position: Int = 0

    val length: Int get() = size
    val availableRead: Int get() = size - position

    // ---- read ----

    private fun require(count: Int) {
        if (count < 0 || position + count > size) {
            throw IOException("Unexpected end of data: need $count bytes at $position, have ${size - position}")
        }
    }

    fun skip(count: Int) {
        require(count)
        position += count
    }

    fun readU8(): Int {
        require(1)
        return buffer[position++].toInt() and 0xFF
    }

    fun readS8(): Int {
        require(1)
        return buffer[position++].toInt()
    }

    fun readU16BE(): Int {
        require(2)
        val v = ((buffer[position].toInt() and 0xFF) shl 8) or (buffer[position + 1].toInt() and 0xFF)
        position += 2
        return v
    }

    fun readS16LE(): Int {
        require(2)
        val v = (buffer[position].toInt() and 0xFF) or (buffer[position + 1].toInt() shl 8)
        position += 2
        return v
    }

    fun readS24LE(): Int {
        require(3)
        val v = buffer.readS24LE(position)
        position += 3
        return v
    }

    fun readS32LE(): Int {
        require(4)
        val v = buffer.readS32LE(position)
        position += 4
        return v
    }

    fun readS32BE(): Int {
        require(4)
        val v = ((buffer[position].toInt() and 0xFF) shl 24) or
                ((buffer[position + 1].toInt() and 0xFF) shl 16) or
                ((buffer[position + 2].toInt() and 0xFF) shl 8) or
                (buffer[position + 3].toInt() and 0xFF)
        position += 4
        return v
    }

    fun readU32BE(): Long = readS32BE().toLong() and 0xFFFFFFFFL

    fun readBytes(count: Int): ByteArray {
        require(count)
        val out = buffer.copyOfRange(position, position + count)
        position += count
        return out
    }

    fun readUByteArray(count: Int): UByteArray = readBytes(count).asUByteArray()

    /** Reads [count] bytes and returns the text before the first zero byte. */
    fun readStringz(count: Int): String {
        val bytes = readBytes(count)
        val end = bytes.indexOf(0).let { if (it < 0) bytes.size else it }
        return bytes.decodeToString(0, end)
    }

    // ---- write ----

    private fun ensure(extra: Int) {
        val needed = position + extra
        if (needed > buffer.size) {
            buffer = buffer.copyOf(maxOf(needed, buffer.size * 2, 64))
        }
    }

    private fun put(b: Int) {
        ensure(1)
        buffer[position++] = b.toByte()
        if (position > size) size = position
    }

    fun write8(v: Int) = put(v)

    fun write16BE(v: Int) {
        put(v ushr 8); put(v)
    }

    fun write16LE(v: Int) {
        put(v); put(v ushr 8)
    }

    fun write24LE(v: Int) {
        put(v); put(v ushr 8); put(v ushr 16)
    }

    fun write32BE(v: Int) {
        put(v ushr 24); put(v ushr 16); put(v ushr 8); put(v)
    }

    fun write32LE(v: Int) {
        put(v); put(v ushr 8); put(v ushr 16); put(v ushr 24)
    }

    fun writeBytes(bytes: ByteArray) {
        ensure(bytes.size)
        bytes.copyInto(buffer, position)
        position += bytes.size
        if (position > size) size = position
    }

    /** Writes the text followed by a zero byte. */
    fun writeStringz(text: String) {
        writeBytes(text.encodeToByteArray())
        put(0)
    }

    /** All bytes in the stream, independent of the current position. */
    fun toByteArray(): ByteArray = buffer.copyOf(size)
}

internal inline fun buildBytes(block: MemoryStream.() -> Unit): ByteArray =
    MemoryStream().apply(block).toByteArray()

internal fun ByteArray.readS24LE(offset: Int): Int {
    val v = (this[offset].toInt() and 0xFF) or
            ((this[offset + 1].toInt() and 0xFF) shl 8) or
            ((this[offset + 2].toInt() and 0xFF) shl 16)
    return (v shl 8) shr 8
}

internal fun ByteArray.readS32LE(offset: Int): Int =
    (this[offset].toInt() and 0xFF) or
            ((this[offset + 1].toInt() and 0xFF) shl 8) or
            ((this[offset + 2].toInt() and 0xFF) shl 16) or
            ((this[offset + 3].toInt() and 0xFF) shl 24)
