package net.iriscan.sdk.core.io

import net.iriscan.sdk.io.exception.IOException

/**
 * zlib (RFC 1950) around DEFLATE (RFC 1951), in pure Kotlin so that PNG works the same
 * on every platform without native dependencies.
 */
internal object ZLib {

    fun compress(data: ByteArray): ByteArray {
        val out = BitWriter(data.size / 2 + 64)
        out.writeByte(0x78)
        out.writeByte(0x9C)
        val body = BitWriter(data.size / 2 + 64)
        Deflate.compress(data, body)
        body.alignToByte()
        val compressed = body.toByteArray()
        if (compressed.size <= data.size + data.size / 65535 * 5 + 5) {
            for (b in compressed) out.writeByte(b.toInt())
        } else {
            Deflate.store(data, out)
        }
        val adler = adler32(data)
        out.writeByte(adler ushr 24)
        out.writeByte(adler ushr 16)
        out.writeByte(adler ushr 8)
        out.writeByte(adler)
        return out.toByteArray()
    }

    fun uncompress(data: ByteArray): ByteArray {
        if (data.size < 6) throw IOException("Invalid zlib data: too short")
        val cmf = data[0].toInt() and 0xFF
        val flg = data[1].toInt() and 0xFF
        if (cmf and 0x0F != 8 || ((cmf shl 8) or flg) % 31 != 0) throw IOException("Invalid zlib header")
        if (flg and 0x20 != 0) throw IOException("zlib preset dictionaries are not supported")
        return Inflate.inflate(data, 2)
    }
}

internal class BitWriter(capacity: Int) {
    private var buf = ByteArray(maxOf(capacity, 64))
    private var pos = 0
    private var bitBuf = 0
    private var bitCount = 0

    private fun put(b: Int) {
        if (pos == buf.size) buf = buf.copyOf(buf.size * 2)
        buf[pos++] = b.toByte()
    }

    /** Writes [n] bits of [value], least significant bit first. */
    fun writeBits(value: Int, n: Int) {
        bitBuf = bitBuf or (value shl bitCount)
        bitCount += n
        while (bitCount >= 8) {
            put(bitBuf and 0xFF)
            bitBuf = bitBuf ushr 8
            bitCount -= 8
        }
    }

    /** Writes a Huffman code, which DEFLATE stores most significant bit first. */
    fun writeCode(code: Int, length: Int) {
        var reversed = 0
        var c = code
        repeat(length) {
            reversed = (reversed shl 1) or (c and 1)
            c = c ushr 1
        }
        writeBits(reversed, length)
    }

    fun alignToByte() {
        if (bitCount > 0) writeBits(0, 8 - bitCount)
    }

    fun writeByte(b: Int) {
        alignToByte()
        put(b and 0xFF)
    }

    fun toByteArray(): ByteArray = buf.copyOf(pos)
}

private val LENGTH_BASE = intArrayOf(
    3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258
)
private val LENGTH_EXTRA = intArrayOf(
    0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0
)
private val DIST_BASE = intArrayOf(
    1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257, 385, 513, 769,
    1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577
)
private val DIST_EXTRA = intArrayOf(
    0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13
)

/** DEFLATE compressor: LZ77 with hash chains, fixed Huffman codes. */
internal object Deflate {
    private const val WINDOW = 32768
    private const val MIN_MATCH = 3
    private const val MAX_MATCH = 258
    private const val HASH_BITS = 15
    private const val MAX_CHAIN = 64

    fun compress(data: ByteArray, out: BitWriter) {
        out.writeBits(1, 1) // final block
        out.writeBits(1, 2) // fixed Huffman
        val head = IntArray(1 shl HASH_BITS) { -1 }
        val prev = IntArray(WINDOW)
        var i = 0
        val n = data.size
        fun hash(p: Int): Int {
            val h = ((data[p].toInt() and 0xFF) shl 10) xor ((data[p + 1].toInt() and 0xFF) shl 5) xor
                    (data[p + 2].toInt() and 0xFF)
            return h and ((1 shl HASH_BITS) - 1)
        }
        fun insert(p: Int) {
            if (p + MIN_MATCH > n) return
            val h = hash(p)
            prev[p and (WINDOW - 1)] = head[h]
            head[h] = p
        }
        while (i < n) {
            var bestLen = 0
            var bestDist = 0
            if (i + MIN_MATCH <= n) {
                var candidate = head[hash(i)]
                var chain = MAX_CHAIN
                val maxLen = minOf(MAX_MATCH, n - i)
                while (candidate >= 0 && i - candidate <= WINDOW && chain-- > 0) {
                    var len = 0
                    while (len < maxLen && data[candidate + len] == data[i + len]) len++
                    if (len > bestLen) {
                        bestLen = len
                        bestDist = i - candidate
                        if (len == maxLen) break
                    }
                    val next = prev[candidate and (WINDOW - 1)]
                    if (next >= candidate) break
                    candidate = next
                }
            }
            if (bestLen >= MIN_MATCH) {
                writeLength(out, bestLen)
                writeDistance(out, bestDist)
                for (k in 0 until bestLen) insert(i + k)
                i += bestLen
            } else {
                writeLiteral(out, data[i].toInt() and 0xFF)
                insert(i)
                i++
            }
        }
        writeLiteral(out, 256) // end of block
    }

    /** Stored (uncompressed) blocks, for data that does not compress. */
    fun store(data: ByteArray, out: BitWriter) {
        var offset = 0
        do {
            val len = minOf(65535, data.size - offset)
            val last = offset + len >= data.size
            out.writeBits(if (last) 1 else 0, 1)
            out.writeBits(0, 2)
            out.alignToByte()
            out.writeByte(len)
            out.writeByte(len ushr 8)
            out.writeByte(len.inv())
            out.writeByte(len.inv() ushr 8)
            for (k in 0 until len) out.writeByte(data[offset + k].toInt())
            offset += len
        } while (!last)
    }

    private fun writeLiteral(out: BitWriter, sym: Int) = when {
        sym < 144 -> out.writeCode(0x30 + sym, 8)
        sym < 256 -> out.writeCode(0x190 + sym - 144, 9)
        sym < 280 -> out.writeCode(sym - 256, 7)
        else -> out.writeCode(0xC0 + sym - 280, 8)
    }

    private fun writeLength(out: BitWriter, length: Int) {
        var idx = LENGTH_BASE.size - 1
        while (LENGTH_BASE[idx] > length) idx--
        writeLiteral(out, 257 + idx)
        if (LENGTH_EXTRA[idx] > 0) out.writeBits(length - LENGTH_BASE[idx], LENGTH_EXTRA[idx])
    }

    private fun writeDistance(out: BitWriter, distance: Int) {
        var idx = DIST_BASE.size - 1
        while (DIST_BASE[idx] > distance) idx--
        out.writeCode(idx, 5)
        if (DIST_EXTRA[idx] > 0) out.writeBits(distance - DIST_BASE[idx], DIST_EXTRA[idx])
    }
}

/** DEFLATE decompressor supporting stored, fixed and dynamic Huffman blocks. */
internal object Inflate {

    private class Huffman(lengths: IntArray, offset: Int, count: Int) {
        val counts = IntArray(16)
        val symbols = IntArray(count)

        init {
            for (i in 0 until count) counts[lengths[offset + i]]++
            counts[0] = 0
            val offs = IntArray(16)
            for (len in 1 until 16) offs[len] = offs[len - 1] + counts[len - 1]
            for (i in 0 until count) {
                val len = lengths[offset + i]
                if (len != 0) symbols[offs[len]++] = i
            }
        }
    }

    private class Reader(val data: ByteArray, var pos: Int) {
        var bitBuf = 0
        var bitCount = 0

        fun bits(n: Int): Int {
            while (bitCount < n) {
                if (pos >= data.size) throw IOException("Unexpected end of deflate data")
                bitBuf = bitBuf or ((data[pos++].toInt() and 0xFF) shl bitCount)
                bitCount += 8
            }
            val v = bitBuf and ((1 shl n) - 1)
            bitBuf = bitBuf ushr n
            bitCount -= n
            return v
        }

        fun decode(h: Huffman): Int {
            var code = 0
            var first = 0
            var index = 0
            for (len in 1 until 16) {
                code = code or bits(1)
                val count = h.counts[len]
                if (code - count < first) return h.symbols[index + (code - first)]
                index += count
                first = (first + count) shl 1
                code = code shl 1
            }
            throw IOException("Invalid Huffman code in deflate data")
        }
    }

    private class Output(capacity: Int) {
        var buf = ByteArray(maxOf(capacity, 1024))
        var size = 0

        fun put(b: Int) {
            if (size == buf.size) buf = buf.copyOf(buf.size * 2)
            buf[size++] = b.toByte()
        }

        fun copy(distance: Int, length: Int) {
            if (distance > size) throw IOException("Invalid deflate distance")
            var from = size - distance
            repeat(length) { put(buf[from++].toInt()) }
        }
    }

    private val fixedLit: Huffman
    private val fixedDist: Huffman

    init {
        val l = IntArray(288)
        for (i in 0 until 144) l[i] = 8
        for (i in 144 until 256) l[i] = 9
        for (i in 256 until 280) l[i] = 7
        for (i in 280 until 288) l[i] = 8
        fixedLit = Huffman(l, 0, 288)
        fixedDist = Huffman(IntArray(30) { 5 }, 0, 30)
    }

    private val ORDER = intArrayOf(16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15)

    fun inflate(data: ByteArray, start: Int): ByteArray {
        val r = Reader(data, start)
        val out = Output(data.size * 4)
        do {
            val last = r.bits(1)
            when (r.bits(2)) {
                0 -> {
                    r.bitBuf = 0
                    r.bitCount = 0
                    if (r.pos + 4 > data.size) throw IOException("Unexpected end of deflate data")
                    val len = (data[r.pos].toInt() and 0xFF) or ((data[r.pos + 1].toInt() and 0xFF) shl 8)
                    r.pos += 4
                    if (r.pos + len > data.size) throw IOException("Unexpected end of deflate data")
                    for (k in 0 until len) out.put(data[r.pos + k].toInt())
                    r.pos += len
                }
                1 -> codes(r, out, fixedLit, fixedDist)
                2 -> {
                    val nLen = r.bits(5) + 257
                    val nDist = r.bits(5) + 1
                    val nCode = r.bits(4) + 4
                    val lengths = IntArray(320)
                    for (k in 0 until nCode) lengths[ORDER[k]] = r.bits(3)
                    val lenCode = Huffman(lengths, 0, 19)
                    lengths.fill(0)
                    var k = 0
                    while (k < nLen + nDist) {
                        val sym = r.decode(lenCode)
                        when {
                            sym < 16 -> lengths[k++] = sym
                            sym == 16 -> {
                                if (k == 0) throw IOException("Invalid deflate code lengths")
                                val prevLen = lengths[k - 1]
                                repeat(3 + r.bits(2)) { lengths[k++] = prevLen }
                            }
                            sym == 17 -> k += 3 + r.bits(3)
                            else -> k += 11 + r.bits(7)
                        }
                    }
                    codes(r, out, Huffman(lengths, 0, nLen), Huffman(lengths, nLen, nDist))
                }
                else -> throw IOException("Invalid deflate block type")
            }
        } while (last == 0)
        return out.buf.copyOf(out.size)
    }

    private fun codes(r: Reader, out: Output, lit: Huffman, dist: Huffman) {
        while (true) {
            val sym = r.decode(lit)
            when {
                sym < 256 -> out.put(sym)
                sym == 256 -> return
                else -> {
                    val idx = sym - 257
                    if (idx >= 29) throw IOException("Invalid deflate length symbol")
                    val length = LENGTH_BASE[idx] + r.bits(LENGTH_EXTRA[idx])
                    val dSym = r.decode(dist)
                    if (dSym >= 30) throw IOException("Invalid deflate distance symbol")
                    val distance = DIST_BASE[dSym] + r.bits(DIST_EXTRA[dSym])
                    out.copy(distance, length)
                }
            }
        }
    }
}
