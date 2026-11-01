package net.iriscan.sdk.core.io

import net.iriscan.sdk.io.exception.IOException

/*
 * zlib (RFC 1950) around DEFLATE (RFC 1951) in pure Kotlin, so that PNG images are read and
 * written the same way on every platform without native libraries.
 *
 * A zlib stream is a 2-byte header, DEFLATE data and the Adler-32 checksum of the uncompressed
 * bytes. DEFLATE data is a sequence of blocks; each block is stored as is, or compressed with
 * Huffman codes that are either fixed by the standard or sent at the start of the block.
 *
 * The compressor here is deliberately simple: LZ77 matches found through hash chains, coded with
 * the fixed Huffman codes. It compresses images about as well as zlib's fast levels. The
 * decompressor reads all three block types, so it accepts any standard zlib stream, including
 * those written by libpng, Pillow, OpenCV and java.util.zip at any compression level.
 *
 * Section numbers in the comments refer to RFC 1950 (zlib) and RFC 1951 (DEFLATE).
 */

/** zlib streams: header, DEFLATE data, Adler-32 trailer (RFC 1950, section 2.2). */
internal object ZLib {

    /**
     * First header byte, CMF: compression method 8 (DEFLATE) in the low 4 bits and
     * log2(window size) - 8 = 7 (a 32 KiB window) in the high 4 bits.
     */
    private const val CMF_DEFLATE_32K_WINDOW = 0x78

    /**
     * Second header byte, FLG: compression level "default" (2) in the top 2 bits, no preset
     * dictionary, and 5 check bits chosen so that CMF * 256 + FLG is a multiple of 31.
     */
    private const val FLG_DEFAULT_LEVEL = 0x9C

    private const val COMPRESSION_METHOD_MASK = 0x0F
    private const val COMPRESSION_METHOD_DEFLATE = 8
    private const val WINDOW_SIZE_SHIFT = 4
    private const val MAX_WINDOW_LOG2_MINUS_8 = 7
    private const val HEADER_CHECK_DIVISOR = 31
    private const val FLAG_PRESET_DICTIONARY = 0x20

    private const val HEADER_BYTES = 2
    private const val CHECKSUM_BYTES = 4

    /** Compresses [data] into a complete zlib stream. */
    fun compress(data: ByteArray): ByteArray {
        val compressed = BitWriter(data.size / 2 + 64)
        Deflate.compress(data, compressed)
        compressed.alignToByte()

        val out = BitWriter(data.size / 2 + 64)
        out.writeByte(CMF_DEFLATE_32K_WINDOW)
        out.writeByte(FLG_DEFAULT_LEVEL)
        if (compressed.size <= Deflate.storedSize(data.size)) {
            out.writeBytes(compressed.toByteArray())
        } else {
            // data that does not compress (noise, already compressed files) is stored as is
            Deflate.store(data, out)
        }
        val checksum = adler32(data)
        for (shift in intArrayOf(24, 16, 8, 0)) out.writeByte(checksum ushr shift)
        return out.toByteArray()
    }

    /**
     * Decompresses a complete zlib stream.
     * @throws IOException when the header is invalid, the data is truncated or corrupt, or the
     * Adler-32 checksum does not match.
     */
    fun uncompress(data: ByteArray): ByteArray {
        if (data.size < HEADER_BYTES + CHECKSUM_BYTES) throw IOException("Invalid zlib data: too short")
        val cmf = data[0].toInt() and 0xFF
        val flg = data[1].toInt() and 0xFF
        if (cmf and COMPRESSION_METHOD_MASK != COMPRESSION_METHOD_DEFLATE) {
            throw IOException("Invalid zlib header: compression method ${cmf and COMPRESSION_METHOD_MASK}, expected 8 (deflate)")
        }
        if (cmf ushr WINDOW_SIZE_SHIFT > MAX_WINDOW_LOG2_MINUS_8) {
            throw IOException("Invalid zlib header: window larger than 32 KiB")
        }
        if (((cmf shl 8) or flg) % HEADER_CHECK_DIVISOR != 0) {
            throw IOException("Invalid zlib header: check bits do not match")
        }
        if (flg and FLAG_PRESET_DICTIONARY != 0) throw IOException("zlib preset dictionaries are not supported")

        val inflated = Inflate.inflate(data, HEADER_BYTES)
        val trailer = inflated.endPosition
        if (trailer + CHECKSUM_BYTES > data.size) throw IOException("Invalid zlib data: missing Adler-32 checksum")
        var expected = 0
        for (i in 0 until CHECKSUM_BYTES) expected = (expected shl 8) or (data[trailer + i].toInt() and 0xFF)
        if (expected != adler32(inflated.bytes)) throw IOException("Invalid zlib data: Adler-32 checksum mismatch")
        return inflated.bytes
    }
}

/**
 * Writes a DEFLATE bit stream. DEFLATE packs data elements starting at the least significant bit
 * of each byte (RFC 1951, section 3.1.1); Huffman codes are the exception and are packed starting
 * with their most significant bit, see [writeHuffmanCode].
 */
internal class BitWriter(capacity: Int) {
    private var buffer = ByteArray(maxOf(capacity, 64))
    private var bytesWritten = 0

    /** Bits waiting to fill the next byte, least significant first. */
    private var pendingBits = 0
    private var pendingBitCount = 0

    /** Bytes written so far, not counting bits that do not fill a byte yet. */
    val size: Int get() = bytesWritten

    private fun appendByte(byte: Int) {
        if (bytesWritten == buffer.size) buffer = buffer.copyOf(buffer.size * 2)
        buffer[bytesWritten++] = byte.toByte()
    }

    /** Writes the low [count] bits of [value], least significant bit first (at most 24 bits). */
    fun writeBits(value: Int, count: Int) {
        require(count in 0..24) { "Can write 0 to 24 bits at once, got $count" }
        pendingBits = pendingBits or ((value and ((1 shl count) - 1)) shl pendingBitCount)
        pendingBitCount += count
        while (pendingBitCount >= Byte.SIZE_BITS) {
            appendByte(pendingBits and 0xFF)
            pendingBits = pendingBits ushr Byte.SIZE_BITS
            pendingBitCount -= Byte.SIZE_BITS
        }
    }

    /** Writes a Huffman code of [length] bits, most significant bit first (RFC 1951, section 3.1.1). */
    fun writeHuffmanCode(code: Int, length: Int) {
        var reversed = 0
        var remaining = code
        repeat(length) {
            reversed = (reversed shl 1) or (remaining and 1)
            remaining = remaining ushr 1
        }
        writeBits(reversed, length)
    }

    /** Pads the current byte with zero bits. */
    fun alignToByte() {
        if (pendingBitCount > 0) writeBits(0, Byte.SIZE_BITS - pendingBitCount)
    }

    /** Pads to a byte boundary, then writes one whole byte (the low 8 bits of [byte]). */
    fun writeByte(byte: Int) {
        alignToByte()
        appendByte(byte and 0xFF)
    }

    fun writeBytes(bytes: ByteArray) {
        for (b in bytes) writeByte(b.toInt())
    }

    fun toByteArray(): ByteArray = buffer.copyOf(bytesWritten)
}

/** Block header: 1 bit "last block", then 2 bits block type (RFC 1951, section 3.2.3). */
private const val BLOCK_TYPE_BITS = 2
private const val BLOCK_TYPE_STORED = 0
private const val BLOCK_TYPE_FIXED_HUFFMAN = 1
private const val BLOCK_TYPE_DYNAMIC_HUFFMAN = 2

/** A stored block holds at most 65535 bytes: its length field is 16 bits (section 3.2.4). */
private const val MAX_STORED_BLOCK_BYTES = 0xFFFF

/** Literal/length alphabet: 0..255 are bytes, 256 ends the block, 257..285 are match lengths (section 3.2.5). */
private const val END_OF_BLOCK = 256
private const val FIRST_LENGTH_SYMBOL = 257
private const val LENGTH_SYMBOL_COUNT = 29
private const val DISTANCE_SYMBOL_COUNT = 30

/** Shortest and longest LZ77 match DEFLATE can express (section 3.2.5). */
private const val MIN_MATCH = 3
private const val MAX_MATCH = 258

/** Matches may refer back at most 32 KiB (section 2). */
private const val WINDOW_BYTES = 32768

/** Shortest match length for each length symbol 257..285 (section 3.2.5). */
private val LENGTH_BASE = intArrayOf(
    3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258
)

/** Number of extra bits after each length symbol, added to [LENGTH_BASE]. */
private val LENGTH_EXTRA_BITS = intArrayOf(
    0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0
)

/** Shortest distance for each distance symbol 0..29 (section 3.2.5). */
private val DISTANCE_BASE = intArrayOf(
    1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257, 385, 513, 769,
    1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577
)

/** Number of extra bits after each distance symbol, added to [DISTANCE_BASE]. */
private val DISTANCE_EXTRA_BITS = intArrayOf(
    0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13
)

/**
 * The fixed Huffman code for the literal/length alphabet (section 3.2.6), as ranges of symbols
 * that share a code length. Codes are assigned in symbol order, so within a range the code is
 * the first code of the range plus the offset of the symbol.
 */
private class FixedCodeRange(val firstSymbol: Int, val lastSymbol: Int, val codeLength: Int, val firstCode: Int)

private val FIXED_LITERAL_LENGTH_CODES = listOf(
    FixedCodeRange(0, 143, 8, 0b0011_0000),       // 00110000 .. 10111111
    FixedCodeRange(144, 255, 9, 0b1_1001_0000),   // 110010000 .. 111111111
    FixedCodeRange(256, 279, 7, 0b000_0000),      // 0000000 .. 0010111
    FixedCodeRange(280, 287, 8, 0b1100_0000),     // 11000000 .. 11000111
)

/** In the fixed code every distance symbol is a 5-bit code equal to the symbol (section 3.2.6). */
private const val FIXED_DISTANCE_CODE_LENGTH = 5

/** DEFLATE compressor: LZ77 matching through hash chains, fixed Huffman codes, one block. */
internal object Deflate {

    /** Matches are looked up by a hash of their first [MIN_MATCH] bytes: 2^15 buckets. */
    private const val HASH_BITS = 15
    private const val HASH_SIZE = 1 shl HASH_BITS

    /** Per input byte, how much each of the 3 hashed bytes is shifted: 10 + 5 + 0 bits spread over 15. */
    private const val HASH_SHIFT_FIRST = 10
    private const val HASH_SHIFT_SECOND = 5

    /**
     * How many earlier positions with the same hash are compared before taking the best match so
     * far. More finds slightly longer matches at a higher cost; 64 is in the range of zlib's
     * default level (128) and its faster levels (4 to 32).
     */
    private const val MAX_CHAIN_LENGTH = 64

    /** Size of [data] stored without compression: the data plus 5 header bytes per stored block. */
    fun storedSize(dataSize: Int): Int {
        val blocks = maxOf(1, (dataSize + MAX_STORED_BLOCK_BYTES - 1) / MAX_STORED_BLOCK_BYTES)
        return dataSize + STORED_BLOCK_HEADER_BYTES * blocks
    }

    /** Stored block header after byte alignment: LEN and NLEN, 2 bytes each, plus the bit header byte. */
    private const val STORED_BLOCK_HEADER_BYTES = 5

    /** Writes [data] as a single compressed block with the fixed Huffman code. */
    fun compress(data: ByteArray, out: BitWriter) {
        out.writeBits(1, 1) // this is the last block
        out.writeBits(BLOCK_TYPE_FIXED_HUFFMAN, BLOCK_TYPE_BITS)

        // head[hash] = latest position with this hash; previous[position % window] = the position before it
        val head = IntArray(HASH_SIZE) { -1 }
        val previous = IntArray(WINDOW_BYTES)
        val n = data.size
        fun hashAt(p: Int): Int {
            val h = ((data[p].toInt() and 0xFF) shl HASH_SHIFT_FIRST) xor
                    ((data[p + 1].toInt() and 0xFF) shl HASH_SHIFT_SECOND) xor
                    (data[p + 2].toInt() and 0xFF)
            return h and (HASH_SIZE - 1)
        }
        fun remember(p: Int) {
            if (p + MIN_MATCH > n) return
            val h = hashAt(p)
            previous[p and (WINDOW_BYTES - 1)] = head[h]
            head[h] = p
        }

        var i = 0
        while (i < n) {
            var bestLength = 0
            var bestDistance = 0
            if (i + MIN_MATCH <= n) {
                var candidate = head[hashAt(i)]
                var chainLeft = MAX_CHAIN_LENGTH
                val longestPossible = minOf(MAX_MATCH, n - i)
                while (candidate >= 0 && i - candidate <= WINDOW_BYTES && chainLeft-- > 0) {
                    var length = 0
                    while (length < longestPossible && data[candidate + length] == data[i + length]) length++
                    if (length > bestLength) {
                        bestLength = length
                        bestDistance = i - candidate
                        if (length == longestPossible) break
                    }
                    val older = previous[candidate and (WINDOW_BYTES - 1)]
                    // the window slot was reused by a newer position: the chain ends here
                    if (older >= candidate) break
                    candidate = older
                }
            }
            if (bestLength >= MIN_MATCH) {
                writeLength(out, bestLength)
                writeDistance(out, bestDistance)
                for (k in 0 until bestLength) remember(i + k)
                i += bestLength
            } else {
                writeLiteralOrLengthSymbol(out, data[i].toInt() and 0xFF)
                remember(i)
                i++
            }
        }
        writeLiteralOrLengthSymbol(out, END_OF_BLOCK)
    }

    /** Writes [data] as stored blocks (section 3.2.4), for data that does not compress. */
    fun store(data: ByteArray, out: BitWriter) {
        var offset = 0
        do {
            val length = minOf(MAX_STORED_BLOCK_BYTES, data.size - offset)
            val isLast = offset + length >= data.size
            out.writeBits(if (isLast) 1 else 0, 1)
            out.writeBits(BLOCK_TYPE_STORED, BLOCK_TYPE_BITS)
            out.alignToByte()
            // LEN, then NLEN = one's complement of LEN, both little-endian
            out.writeByte(length)
            out.writeByte(length ushr 8)
            out.writeByte(length.inv())
            out.writeByte(length.inv() ushr 8)
            for (k in 0 until length) out.writeByte(data[offset + k].toInt())
            offset += length
        } while (!isLast)
    }

    private fun writeLiteralOrLengthSymbol(out: BitWriter, symbol: Int) {
        val range = FIXED_LITERAL_LENGTH_CODES.first { symbol in it.firstSymbol..it.lastSymbol }
        out.writeHuffmanCode(range.firstCode + (symbol - range.firstSymbol), range.codeLength)
    }

    private fun writeLength(out: BitWriter, length: Int) {
        val index = LENGTH_BASE.indexOfLast { it <= length }
        writeLiteralOrLengthSymbol(out, FIRST_LENGTH_SYMBOL + index)
        out.writeBits(length - LENGTH_BASE[index], LENGTH_EXTRA_BITS[index])
    }

    private fun writeDistance(out: BitWriter, distance: Int) {
        val index = DISTANCE_BASE.indexOfLast { it <= distance }
        out.writeHuffmanCode(index, FIXED_DISTANCE_CODE_LENGTH)
        out.writeBits(distance - DISTANCE_BASE[index], DISTANCE_EXTRA_BITS[index])
    }
}

/** Result of [Inflate.inflate]: the bytes and the position right after the DEFLATE data. */
internal class InflateResult(val bytes: ByteArray, val endPosition: Int)

/**
 * DEFLATE decompressor for stored, fixed-Huffman and dynamic-Huffman blocks. The Huffman decoding
 * follows the canonical-code method of Mark Adler's puff.c, the reference decoder shipped with zlib.
 */
internal object Inflate {

    /** No Huffman code in DEFLATE is longer than 15 bits (section 3.2.7). */
    private const val MAX_CODE_LENGTH = 15

    /** The code-length alphabet: 0..15 are lengths, 16..18 repeat (section 3.2.7). */
    private const val CODE_LENGTH_SYMBOL_COUNT = 19
    private const val REPEAT_PREVIOUS = 16
    private const val REPEAT_ZERO_SHORT = 17
    private const val REPEAT_ZERO_LONG = 18

    /** Order in which the code lengths of the code-length alphabet are sent (section 3.2.7). */
    private val CODE_LENGTH_ORDER = intArrayOf(16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15)

    /** The literal/length alphabet has 288 code lengths in the fixed code, of which 286 are used. */
    private const val FIXED_LITERAL_LENGTH_SYMBOLS = 288

    /**
     * A canonical Huffman code given by the code length of each symbol: how many codes there are
     * of each length, and the symbols sorted by code length, then by value (section 3.2.2).
     */
    private class Huffman(codeLengths: IntArray, offset: Int, symbolCount: Int) {
        val countByLength = IntArray(MAX_CODE_LENGTH + 1)
        val symbolsInCodeOrder = IntArray(symbolCount)

        init {
            for (i in 0 until symbolCount) countByLength[codeLengths[offset + i]]++
            countByLength[0] = 0
            // a set of code lengths that needs more codes than exist cannot be decoded
            var codesLeft = 1
            for (length in 1..MAX_CODE_LENGTH) {
                codesLeft = (codesLeft shl 1) - countByLength[length]
                if (codesLeft < 0) throw IOException("Invalid deflate data: over-subscribed Huffman code")
            }
            val firstIndexOfLength = IntArray(MAX_CODE_LENGTH + 1)
            for (length in 1 until MAX_CODE_LENGTH) {
                firstIndexOfLength[length + 1] = firstIndexOfLength[length] + countByLength[length]
            }
            for (i in 0 until symbolCount) {
                val length = codeLengths[offset + i]
                if (length != 0) symbolsInCodeOrder[firstIndexOfLength[length]++] = i
            }
        }
    }

    /** Reads bits least significant first from [data], starting at byte [position]. */
    private class BitReader(val data: ByteArray, var position: Int) {
        private var pendingBits = 0
        private var pendingBitCount = 0

        fun bits(count: Int): Int {
            while (pendingBitCount < count) {
                if (position >= data.size) throw IOException("Unexpected end of deflate data")
                pendingBits = pendingBits or ((data[position++].toInt() and 0xFF) shl pendingBitCount)
                pendingBitCount += Byte.SIZE_BITS
            }
            val value = pendingBits and ((1 shl count) - 1)
            pendingBits = pendingBits ushr count
            pendingBitCount -= count
            return value
        }

        /** Drops the bits left in the current byte; stored blocks start at a byte boundary. */
        fun alignToByte() {
            pendingBits = 0
            pendingBitCount = 0
        }

        /**
         * Decodes one symbol. Canonical codes of the same length are consecutive numbers, so the
         * code is read bit by bit until it falls into the range of codes of its length.
         */
        fun decode(code: Huffman): Int {
            var value = 0          // the bits read so far
            var firstCode = 0      // first code of the current length
            var firstIndex = 0     // index of that code's symbol in symbolsInCodeOrder
            for (length in 1..MAX_CODE_LENGTH) {
                value = value or bits(1)
                val count = code.countByLength[length]
                if (value - count < firstCode) return code.symbolsInCodeOrder[firstIndex + (value - firstCode)]
                firstIndex += count
                firstCode = (firstCode + count) shl 1
                value = value shl 1
            }
            throw IOException("Invalid deflate data: unknown Huffman code")
        }
    }

    /** Growing output buffer that can copy from its own end (LZ77 back-references). */
    private class Output(capacity: Int) {
        var buffer = ByteArray(maxOf(capacity, 1024))
        var size = 0

        fun append(byte: Int) {
            if (size == buffer.size) buffer = buffer.copyOf(buffer.size * 2)
            buffer[size++] = byte.toByte()
        }

        /** Copies [length] bytes from [distance] bytes back; the ranges may overlap (section 3.2.3). */
        fun copyBack(distance: Int, length: Int) {
            if (distance > size) throw IOException("Invalid deflate data: distance $distance before the start of the data")
            var from = size - distance
            repeat(length) { append(buffer[from++].toInt()) }
        }
    }

    private val fixedLiteralLengthCode: Huffman
    private val fixedDistanceCode: Huffman

    init {
        val lengths = IntArray(FIXED_LITERAL_LENGTH_SYMBOLS)
        for (range in FIXED_LITERAL_LENGTH_CODES) {
            for (symbol in range.firstSymbol..range.lastSymbol) lengths[symbol] = range.codeLength
        }
        fixedLiteralLengthCode = Huffman(lengths, 0, FIXED_LITERAL_LENGTH_SYMBOLS)
        fixedDistanceCode = Huffman(IntArray(DISTANCE_SYMBOL_COUNT) { FIXED_DISTANCE_CODE_LENGTH }, 0, DISTANCE_SYMBOL_COUNT)
    }

    /** Decompresses the DEFLATE data in [data] that begins at byte [start]. */
    fun inflate(data: ByteArray, start: Int): InflateResult {
        val reader = BitReader(data, start)
        val out = Output(data.size * 4)
        do {
            val isLast = reader.bits(1) == 1
            when (reader.bits(BLOCK_TYPE_BITS)) {
                BLOCK_TYPE_STORED -> storedBlock(reader, out)
                BLOCK_TYPE_FIXED_HUFFMAN -> huffmanBlock(reader, out, fixedLiteralLengthCode, fixedDistanceCode)
                BLOCK_TYPE_DYNAMIC_HUFFMAN -> dynamicBlock(reader, out)
                else -> throw IOException("Invalid deflate data: reserved block type 3")
            }
        } while (!isLast)
        return InflateResult(out.buffer.copyOf(out.size), reader.position)
    }

    /** Stored block: LEN, NLEN = ~LEN, then LEN raw bytes (section 3.2.4). */
    private fun storedBlock(reader: BitReader, out: Output) {
        reader.alignToByte()
        val data = reader.data
        var p = reader.position
        if (p + 4 > data.size) throw IOException("Unexpected end of deflate data")
        val length = (data[p].toInt() and 0xFF) or ((data[p + 1].toInt() and 0xFF) shl 8)
        val complement = (data[p + 2].toInt() and 0xFF) or ((data[p + 3].toInt() and 0xFF) shl 8)
        if (length != complement.inv() and 0xFFFF) throw IOException("Invalid deflate data: stored block length check failed")
        p += 4
        if (p + length > data.size) throw IOException("Unexpected end of deflate data")
        for (k in 0 until length) out.append(data[p + k].toInt())
        reader.position = p + length
    }

    /**
     * Dynamic block (section 3.2.7): the code lengths of the literal/length and distance codes are
     * themselves Huffman-coded, with a third, small code whose lengths come first.
     */
    private fun dynamicBlock(reader: BitReader, out: Output) {
        val literalLengthCount = reader.bits(5) + 257
        val distanceCount = reader.bits(5) + 1
        val codeLengthCount = reader.bits(4) + 4
        if (literalLengthCount > FIRST_LENGTH_SYMBOL + LENGTH_SYMBOL_COUNT || distanceCount > DISTANCE_SYMBOL_COUNT) {
            throw IOException("Invalid deflate data: too many literal/length or distance codes")
        }

        val codeLengthCodeLengths = IntArray(CODE_LENGTH_SYMBOL_COUNT)
        for (k in 0 until codeLengthCount) codeLengthCodeLengths[CODE_LENGTH_ORDER[k]] = reader.bits(3)
        val codeLengthCode = Huffman(codeLengthCodeLengths, 0, CODE_LENGTH_SYMBOL_COUNT)

        val total = literalLengthCount + distanceCount
        val lengths = IntArray(total)
        var k = 0
        while (k < total) {
            val symbol = reader.decode(codeLengthCode)
            val (value, times) = when (symbol) {
                REPEAT_PREVIOUS -> {
                    if (k == 0) throw IOException("Invalid deflate data: repeat with no previous code length")
                    lengths[k - 1] to 3 + reader.bits(2)
                }
                REPEAT_ZERO_SHORT -> 0 to 3 + reader.bits(3)
                REPEAT_ZERO_LONG -> 0 to 11 + reader.bits(7)
                else -> symbol to 1
            }
            if (k + times > total) throw IOException("Invalid deflate data: code lengths overflow")
            repeat(times) { lengths[k++] = value }
        }
        if (lengths[END_OF_BLOCK] == 0) throw IOException("Invalid deflate data: no end-of-block code")
        huffmanBlock(
            reader, out,
            Huffman(lengths, 0, literalLengthCount),
            Huffman(lengths, literalLengthCount, distanceCount),
        )
    }

    /** Decodes literals and length/distance pairs until the end-of-block symbol (section 3.2.5). */
    private fun huffmanBlock(reader: BitReader, out: Output, literalLength: Huffman, distance: Huffman) {
        while (true) {
            val symbol = reader.decode(literalLength)
            when {
                symbol < END_OF_BLOCK -> out.append(symbol)
                symbol == END_OF_BLOCK -> return
                else -> {
                    val index = symbol - FIRST_LENGTH_SYMBOL
                    if (index >= LENGTH_SYMBOL_COUNT) throw IOException("Invalid deflate data: length symbol $symbol")
                    val length = LENGTH_BASE[index] + reader.bits(LENGTH_EXTRA_BITS[index])
                    val distanceSymbol = reader.decode(distance)
                    if (distanceSymbol >= DISTANCE_SYMBOL_COUNT) {
                        throw IOException("Invalid deflate data: distance symbol $distanceSymbol")
                    }
                    out.copyBack(DISTANCE_BASE[distanceSymbol] + reader.bits(DISTANCE_EXTRA_BITS[distanceSymbol]), length)
                }
            }
        }
    }
}
