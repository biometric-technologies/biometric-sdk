package net.iriscan.sdk.face

import net.iriscan.sdk.FaceConfig
import net.iriscan.sdk.core.io.DataBytes
import net.iriscan.sdk.core.io.asDataBytes
import net.iriscan.sdk.face.impl.FaceOperationsImpl
import net.iriscan.sdk.testutil.hex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Templates without a direction through the matcher that `face().matcher()` returns, set up alone
 * (as on a server that compares templates made on phones) with the threshold of 1.0 the README
 * suggests. On the JVM only: FaceOperationsImpl refers to the platform encoder and liveness classes,
 * and on iOS those need TensorFlow Lite in the test executable. FaceEmbeddingDistanceTest checks the
 * distances on every platform.
 */
class FaceMatcherDamagedTemplateTest {

    private val matcher = FaceOperationsImpl(
        FaceConfig(extractor = null, encoder = null, matcher = FaceMatchProperties(1.0), liveness = null)
    ).matcher()

    private fun template(hexText: String): DataBytes = hex(hexText).asDataBytes()

    /** [3, 4] as big-endian float32 values, as the encoders write them. */
    private val probe = template("40400000 40800000")

    /** [4, 3]: sqrt(0.08) = 0.283 from the probe. */
    private val near = template("40800000 40400000")

    private val zeros = template("00000000 00000000")

    /** NaN as the first value. */
    private val notANumber = template("7FC00000 40800000")

    @Test
    fun aTemplateWithoutDirectionMatchesNoFace() {
        assertFalse(matcher.matches(zeros, probe)) // a match before: the distance was 1.0
        assertFalse(matcher.matches(zeros, zeros)) // a match before: the distance was 0.0
        assertFalse(matcher.matchesAny(zeros, listOf(probe, near)))
        assertFalse(matcher.matches(notANumber, notANumber))
        assertEquals(Double.POSITIVE_INFINITY, matcher.matchScore(zeros, probe))
        assertEquals(Double.POSITIVE_INFINITY, matcher.matchScoreMin(notANumber, listOf(probe, near)))
    }

    @Test
    fun aDamagedTemplateInTheListDoesNotHideTheOthers() {
        val enrolled = listOf(notANumber, zeros, near)
        // false before: the NaN distance made the minimum NaN, so no enrolled face could match
        assertTrue(matcher.matchesAny(probe, enrolled))
        assertEquals(matcher.matchScore(probe, near), matcher.matchScoreMin(probe, enrolled))
    }
}
