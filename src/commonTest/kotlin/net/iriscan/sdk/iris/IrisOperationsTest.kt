package net.iriscan.sdk.iris

import net.iriscan.sdk.IrisConfig
import net.iriscan.sdk.core.io.asByteArray
import net.iriscan.sdk.iris.impl.IrisOperationsImpl
import net.iriscan.sdk.iris.impl.IrisProbe
import net.iriscan.sdk.iris.impl.IrisTemplate
import net.iriscan.sdk.iris.impl.compare
import net.iriscan.sdk.iris.impl.isComparable
import net.iriscan.sdk.iris.impl.minComparedBits
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The iris API as an application calls it. */
class IrisOperationsTest {
    private val match = IrisMatchProperties()
    private val eye = SyntheticIris(61).render(SyntheticIris.Capture(upperLid = 1.0, lowerLid = 1.1, seed = 1))

    private fun operations(matcher: IrisMatchProperties = match): IrisOperations =
        IrisOperationsImpl(IrisConfig(IrisExtractProperties(), IrisEncodeProperties(), matcher))

    /** A template of the default size (16 x 128 cells, 4096 bits) with the first [count] mask bits set. */
    private fun templateWithValidBits(count: Int): IrisTemplate {
        val words = 16 * 128 * 2 / 64
        val mask = LongArray(words) { w ->
            val first = w * 64
            when {
                count >= first + 64 -> -1L
                count <= first -> 0L
                else -> (1L shl (count - first)) - 1
            }
        }
        val rnd = Random(count)
        return IrisTemplate(16, 128, LongArray(words) { rnd.nextLong() }, mask)
    }

    @Test
    fun templateWithTooFewValidBitsScoresZeroAgainstEveryTemplate() {
        val minBits = minComparedBits(16 * 128 * 2, match)
        assertEquals(614, minBits) // 15% of 4096
        val full = templateWithValidBits(16 * 128 * 2)
        val short = templateWithValidBits(minBits - 1)
        assertFalse(isComparable(short, match))
        assertEquals(0.0, compare(IrisProbe(short, match), short, match).score, "against itself")
        assertEquals(0.0, compare(IrisProbe(short, match), full, match).score, "as the probe")
        assertEquals(0.0, compare(IrisProbe(full, match), short, match).score, "in the gallery")
        // one bit more and the template matches itself
        val enough = templateWithValidBits(minBits)
        assertTrue(isComparable(enough, match))
        assertTrue(compare(IrisProbe(enough, match), enough, match).score >= match.threshold)
    }

    @Test
    fun extractAndEncodeRejectsATemplateTheMatcherCannotCompare() {
        // the encoder masks the weakest 15% of each bit plane as fragile bits, so no template has
        // more than 85% valid bits: a matcher that needs 90% in common can compare none of them
        val strict = IrisMatchProperties(minComparedBitsFraction = 0.9)
        assertNotNull(operations().encoder().extractAndEncode(eye))
        assertNull(operations(strict).encoder().extractAndEncode(eye))
    }

    @Test
    fun extractThenEncodeGivesTheTemplateOfExtractAndEncode() {
        val api = operations()
        val direct = assertNotNull(api.encoder().extractAndEncode(eye))
        val twoSteps = api.encoder().encode(assertNotNull(api.extractor().extract(eye)))
        assertTrue(api.matcher().matches(direct, twoSteps))
        // The score saturates at 1.0 long before the codes are equal, so compare the bits. The
        // texture image holds whole grey levels, which moves the fragile-bit cut a little: a few
        // mask bits may differ, but hardly any code bit that is valid in both templates.
        val a = IrisTemplate.fromBytes(direct.asByteArray())
        val b = IrisTemplate.fromBytes(twoSteps.asByteArray())
        var compared = 0
        var differing = 0
        var maskDiffers = 0
        for (w in a.code.indices) {
            val both = a.mask[w] and b.mask[w]
            compared += both.countOneBits()
            differing += ((a.code[w] xor b.code[w]) and both).countOneBits()
            maskDiffers += (a.mask[w] xor b.mask[w]).countOneBits()
        }
        assertTrue(differing * 100 < compared, "$differing of $compared compared bits differ")
        assertTrue(maskDiffers * 50 < a.totalBits, "$maskDiffers mask bits differ")
    }
}
