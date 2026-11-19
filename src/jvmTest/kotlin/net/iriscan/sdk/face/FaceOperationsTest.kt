package net.iriscan.sdk.face

import net.iriscan.sdk.FaceConfig
import net.iriscan.sdk.core.io.DataBytes
import net.iriscan.sdk.core.io.asDataBytes
import net.iriscan.sdk.face.impl.FaceOperationsImpl
import net.iriscan.sdk.testutil.hex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Face operations set up with a matcher only, as on a server that compares templates made on
 * phones: the matcher uses the configured threshold, and the parts that need a model ask to be
 * initialized. These tests run on the JVM only: FaceOperationsImpl refers to the platform encoder
 * and liveness classes, and on iOS that needs TensorFlow Lite in the test executable (see
 * FaceMatcherImpl). The matcher itself is tested on every platform in FaceMatcherTest.
 */
class FaceOperationsTest {

    private fun template(hexText: String): DataBytes = hex(hexText).asDataBytes()

    /** [3, 4] as big-endian float32 values, as the encoders write them. */
    private val probe = template("40400000 40800000")

    /** [4, 3]: sqrt(0.08) = 0.283 from the probe. */
    private val near = template("40800000 40400000")

    /** [6, 8]: the probe's direction at twice the length, distance 0. */
    private val longer = template("40C00000 41000000")

    private fun matcherOnly(threshold: Double): FaceOperations = FaceOperationsImpl(
        FaceConfig(extractor = null, encoder = null, matcher = FaceMatchProperties(threshold), liveness = null)
    )

    @Test
    fun theMatcherUsesTheConfiguredThreshold() {
        assertTrue(matcherOnly(0.3).matcher().matches(probe, near))
        assertFalse(matcherOnly(0.25).matcher().matches(probe, near))
        assertTrue(matcherOnly(0.3).matcher().matchesAny(probe, listOf(near)))
        assertFalse(matcherOnly(0.25).matcher().matchesAny(probe, listOf(near)))
    }

    @Test
    fun theMatcherWorksWithoutTheModels() {
        val face = matcherOnly(1.0)
        assertTrue(face.matcher().matches(probe, longer))
        val error = assertFailsWith<IllegalStateException> { face.encoder() }
        assertEquals("Please initialize SDK with face encoder", error.message)
        assertFailsWith<IllegalStateException> { face.extractor() }
        assertFailsWith<IllegalStateException> { face.livenessPhoto() }
        assertFailsWith<IllegalStateException> { face.livenessPosition() }
    }

    @Test
    fun withoutMatchPropertiesTheMatcherAsksToBeInitialized() {
        val face = FaceOperationsImpl(FaceConfig(extractor = null, encoder = null, matcher = null, liveness = null))
        val error = assertFailsWith<IllegalStateException> { face.matcher() }
        assertEquals("Please initialize SDK with face matcher", error.message)
    }
}
