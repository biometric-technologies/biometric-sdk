package net.iriscan.sdk.iris

import net.iriscan.sdk.iris.impl.IrisTemplate
import net.iriscan.sdk.iris.impl.identify
import net.iriscan.sdk.iris.impl.matchScore
import net.iriscan.sdk.iris.impl.matches
import net.iriscan.sdk.testutil.hex
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The IR2 template format (README, "Template format") on reference bytes written by hand from
 * the layout: the templates [IrisTemplate.fromBytes] reads, the damaged ones it rejects, and what
 * the matcher functions behind `iris().matcher()` do with a damaged template.
 */
class IrisTemplateFormatTest {
    private val match = IrisMatchProperties()

    /**
     * The smallest template the encoder can write (1 row, 32 columns): "IR2", version 1, 1 row,
     * 1 x 32 columns, 2 bits per cell, reserved 0; then one 64-bit word per plane, little-endian:
     * code 0x0123456789ABCDEF, mask with all 64 bits set.
     */
    private val smallest = hex("495232 01 01 01 02 00  EFCDAB8967452301  FFFFFFFFFFFFFFFF")

    /** 16 rows and 0 columns: the header asks for no bits, so its 8 bytes are the full length. */
    private val zeroColumns = hex("495232 01 10 00 02 00")

    /** 0 rows and 4 x 32 columns, also 8 bytes. */
    private val zeroRows = hex("495232 01 00 04 02 00")

    /** A template of SDK 0.6 or earlier: no IR2 header. */
    private val legacy = ByteArray(127) { (it % 2).toByte() }

    @Test
    fun theSmallestTemplateIsReadAndWrittenBackByteForByte() {
        val t = IrisTemplate.fromBytes(smallest)
        assertEquals(1, t.rows)
        assertEquals(32, t.cols)
        assertContentEquals(longArrayOf(0x0123456789ABCDEF), t.code)
        assertContentEquals(longArrayOf(-1L), t.mask)
        assertEquals(64, t.validBits())
        assertContentEquals(smallest, t.toBytes())
        assertEquals(1.0, matchScore(smallest, smallest, match))
    }

    @Test
    fun aHeaderWithoutRowsOrColumnsIsRejectedAsCorrupted() {
        for (damaged in listOf(zeroColumns, zeroRows, hex("495232 01 00 00 02 00"))) {
            val error = assertFailsWith<IllegalArgumentException> { IrisTemplate.fromBytes(damaged) }
            assertTrue(error.message.orEmpty().startsWith("Corrupted iris template"), error.message)
        }
        // nor can such a template be made in memory, where shifted() would divide by 0 columns
        assertFailsWith<IllegalArgumentException> { IrisTemplate(16, 0, LongArray(0), LongArray(0)) }
        assertFailsWith<IllegalArgumentException> { IrisTemplate(0, 128, LongArray(0), LongArray(0)) }
    }

    @Test
    fun theMatcherReportsATemplateWithoutColumnsAsCorruptedInsteadOfDividingByZero() {
        // as the probe: an ArithmeticException (/ by zero) from the probe's rotations before
        assertFailsWith<IllegalArgumentException> { matchScore(zeroColumns, smallest, match) }
        assertFailsWith<IllegalArgumentException> { matches(zeroColumns, smallest, match) }
        assertFailsWith<IllegalArgumentException> { identify(zeroColumns, listOf(smallest), match) }
        // in the list: it scored 0 as a template of another size before, and the search went on;
        // now it stops the search as a truncated template always did
        val truncated = smallest.copyOf(smallest.size - 1)
        assertFailsWith<IllegalArgumentException> { identify(smallest, listOf(truncated, smallest), match) }
        assertFailsWith<IllegalArgumentException> { identify(smallest, listOf(zeroColumns, smallest), match) }
        // 0 rows scored 0 before (no bit to compare) and is now rejected the same way
        assertFailsWith<IllegalArgumentException> { matchScore(zeroRows, smallest, match) }
    }

    @Test
    fun otherDamagedTemplatesAreRejectedAsBefore() {
        val otherVersion = smallest.copyOf().also { it[3] = 2 }
        val otherBitsPerCell = smallest.copyOf().also { it[6] = 1 }
        val truncated = smallest.copyOf(smallest.size - 1)
        val extended = smallest + byteArrayOf(0)
        for (damaged in listOf(otherVersion, otherBitsPerCell, truncated, extended)) {
            assertFailsWith<IllegalArgumentException> { IrisTemplate.fromBytes(damaged) }
            assertFailsWith<IllegalArgumentException> { matchScore(smallest, damaged, match) }
        }
    }

    @Test
    fun aDamagedTemplateIsNotReadWhenNothingIsComparedWithIt() {
        // a legacy template is never compared with an IR2 template (score 0), so neither is read
        assertEquals(0.0, matchScore(zeroColumns, legacy, match))
        assertEquals(0.0, matchScore(legacy, zeroColumns, match))
        assertEquals(0.0, identify(legacy, listOf(zeroColumns), match)?.second)
        // an empty list is answered before the probe is read
        assertNull(identify(zeroColumns, emptyList(), match))
    }
}
