package net.iriscan.sdk.face

import net.iriscan.sdk.core.io.DataBytes
import net.iriscan.sdk.core.io.asDataBytes
import net.iriscan.sdk.face.impl.FaceMatcherImpl
import net.iriscan.sdk.testutil.hex
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The face matcher that `face().matcher()` returns. It loads no model, so it runs on every platform
 * (a server can compare templates made on phones). It is created directly, not through
 * FaceOperationsImpl: on iOS a test that reaches FaceOperationsImpl also reaches the TensorFlow Lite
 * code of the encoder, and the test executable then does not link (see [FaceMatcherImpl]). The JVM
 * test `FaceOperationsTest` checks that FaceOperationsImpl returns this matcher with the configured
 * threshold. Templates are written the way the encoders write them, as big-endian float32 values in
 * hex; the expected distances follow from that byte order.
 */
class FaceMatcherTest {

    private fun template(hexText: String): DataBytes = hex(hexText).asDataBytes()

    /** [3, 4]: the unit vector (0.6, 0.8). */
    private val probe = template("40400000 40800000")

    /** [4, 3]: (0.8, 0.6), sqrt(0.08) = 0.283 from the probe. */
    private val near = template("40800000 40400000")

    /** [6, 8]: the probe's direction at twice the length, distance 0. */
    private val longer = template("40C00000 41000000")

    /** [-3, -4]: the opposite direction, distance 2. */
    private val opposite = template("C0400000 C0800000")

    private fun matcher(threshold: Double): FaceMatcher = FaceMatcherImpl(FaceMatchProperties(threshold))

    @Test
    fun distancesOfReferenceTemplates() {
        val m = matcher(1.0)
        assertEquals(sqrt(0.08), m.matchScore(probe, near), 1e-6)
        assertEquals(0.0, m.matchScore(probe, longer), 1e-6)
        assertEquals(2.0, m.matchScore(probe, opposite), 1e-6)
        assertEquals(m.matchScore(probe, near), m.matchScore(near, probe))
    }

    @Test
    fun aDistanceEqualToTheThresholdMatches() {
        val distance = matcher(1.0).matchScore(probe, near)
        assertTrue(matcher(distance).matches(probe, near))
        assertFalse(matcher(distance - 1e-6).matches(probe, near))
    }

    @Test
    fun severalTemplatesAreComparedByTheClosest() {
        val enrolled = listOf(opposite, near)
        val m = matcher(0.3)
        assertEquals(m.matchScore(probe, near), m.matchScoreMin(probe, enrolled))
        assertFalse(m.matches(probe, opposite))
        assertTrue(m.matchesAny(probe, enrolled))
        assertFalse(matcher(0.25).matchesAny(probe, enrolled))
    }

    @Test
    fun anEmptyListMatchesNothing() {
        // as before anyone is enrolled; both calls used to throw NoSuchElementException
        assertFalse(matcher(1.0).matchesAny(probe, emptyList()))
        assertFalse(matcher(Double.POSITIVE_INFINITY).matchesAny(probe, emptyList()))
        assertEquals(Double.POSITIVE_INFINITY, matcher(1.0).matchScoreMin(probe, emptyList()))
    }

    @Test
    fun templatesThatCannotBeComparedAreRejected() {
        val m = matcher(1.0)
        val threeValues = template("40400000 40800000 00000000")
        assertFailsWith<IllegalArgumentException> { m.matchScore(probe, threeValues) }
        assertFailsWith<IllegalArgumentException> { m.matchesAny(probe, listOf(near, threeValues)) }
        // six bytes are not a whole number of float32 values
        assertFailsWith<IllegalArgumentException> { m.matches(probe, template("40400000 4080")) }
    }
}
