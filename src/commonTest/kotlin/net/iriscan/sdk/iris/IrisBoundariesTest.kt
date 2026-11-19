package net.iriscan.sdk.iris

import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.image.ImageColorType
import net.iriscan.sdk.iris.impl.extractInternal
import net.iriscan.sdk.iris.impl.segmentBoundaries
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IrisBoundariesTest {
    private val props = IrisExtractProperties()

    @Test
    fun boundariesAreWhereTheSyntheticEyeWasDrawn() {
        for (k in 0 until 6) {
            val cap = SyntheticIris.randomCapture(700 + k)
            val b = segmentBoundaries(SyntheticIris(k).render(cap), props)
            assertNotNull(b, "capture $k")
            assertTrue(hypot(b.pupil.x - cap.pupilX, b.pupil.y - cap.pupilY) < 3, "pupil centre, capture $k")
            assertTrue(abs(b.pupil.radius - cap.pupilRadius) < 3, "pupil radius, capture $k")
            assertTrue(hypot(b.limbus.x - cap.cx, b.limbus.y - cap.cy) < 5, "limbus centre, capture $k")
            assertTrue(abs(b.limbus.radius - cap.irisRadius) < 5, "limbus radius, capture $k")
        }
    }

    @Test
    fun anUpperEyelidCoveringTheIrisIsReportedAboveThePupil() {
        // the same capture as IrisPipelineTest.eyelidIsMaskedWhenItCoversTheIris: the lid comes
        // down to half the iris radius above the centre
        val cap = SyntheticIris.Capture(upperLid = 0.5, seed = 1)
        val b = assertNotNull(segmentBoundaries(SyntheticIris(3).render(cap), props))
        val upper = assertNotNull(b.upperEyelid, "upper eyelid")
        val atPupil = upper.y(b.pupil.x)
        assertTrue(atPupil < b.pupil.y, "lid edge above the pupil centre: $atPupil vs ${b.pupil.y}")
        assertTrue(atPupil > b.limbus.y - b.limbus.radius, "lid edge inside the iris: $atPupil")
    }

    @Test
    fun noIrisNoBoundariesAndNoTexture() {
        val flat = Image(200, 160, ImageColorType.GRAY, IntArray(200 * 160) { 128 })
        assertNull(segmentBoundaries(flat, props))
        assertNull(extractInternal(flat, props))
    }

    @Test
    fun eyelidCurveIsTheParabolaItDescribes() {
        val lid = EyelidCurve(a = 10.0, b = 0.5, c = 0.01)
        assertEquals(10.0, lid.y(0.0))
        assertEquals(10.0 + 5.0 + 1.0, lid.y(10.0))
    }
}
