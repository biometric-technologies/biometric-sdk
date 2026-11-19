package net.iriscan.sdk.iris

import net.iriscan.sdk.iris.impl.GrayImage
import net.iriscan.sdk.iris.impl.extractNormalized
import net.iriscan.sdk.iris.impl.gaussianBlur
import net.iriscan.sdk.iris.impl.quantile
import net.iriscan.sdk.iris.impl.segmentIris
import net.iriscan.sdk.iris.impl.srgbEncoded
import net.iriscan.sdk.iris.impl.toGray
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Eyes with the grey levels a sensor stores, which is what every platform passes to the
 * pipeline. The segmentation thresholds are on the sRGB-encoded scale the JVM reader used to
 * produce, so the boundary search works on an encoded copy.
 */
class IrisGrayLevelsTest {
    private val extract = IrisExtractProperties()

    @Test
    fun encodingGivesTheLevelsOfTheOldJvmReader() {
        // what BufferedImage.getRGB returned for TYPE_BYTE_GRAY pixels of these levels
        val encoded = srgbEncoded(GrayImage(5, 1, floatArrayOf(0f, 20f, 64f, 128f, 255f))).data
        assertContentEquals(floatArrayOf(0f, 79f, 137f, 188f, 255f), encoded)
        // order preserving, so a darker pixel never becomes brighter than a lighter one
        val all = srgbEncoded(GrayImage(256, 1, FloatArray(256) { it.toFloat() })).data
        for (i in 1 until 256) assertTrue(all[i] >= all[i - 1], "level $i")
    }

    @Test
    fun dilatedPupilOfADarkerIrisIsNotGrownToTheLimbus() {
        // A 640x480 eye with a dilated pupil and a darker iris. The limbus lies within the largest
        // pupil radius searched; on stored levels the glint-ring check of expandPupil accepted it as
        // the pupil edge and the iris circle was then fitted on the eyelid skin.
        for (subject in 0 until 3) {
            val cap = SyntheticIris.Capture(
                width = 640, height = 480, cx = 320.0, cy = 240.0, irisRadius = 105.0, pupilRatio = 0.58,
                upperLid = 0.9, lowerLid = 1.0, noiseSigma = 3.0, irisLevel = 75.0, seed = subject,
            )
            // optics of a real 640x480 capture: softer edges than the fixture's 3x3 blur
            val gray = gaussianBlur(toGray(SyntheticIris(40 + subject).render(cap)), 2.0)
            val seg = segmentIris(gray, extract)
            assertNotNull(seg, "subject $subject")
            assertTrue(abs(seg.pupil.r - cap.pupilRadius) < 4, "pupil radius ${seg.pupil.r}, subject $subject")
            assertTrue(hypot(seg.pupil.x - cap.pupilX, seg.pupil.y - cap.pupilY) < 4, "pupil center, subject $subject")
            assertTrue(abs(seg.iris.r - cap.irisRadius) < 6, "iris radius ${seg.iris.r}, subject $subject")
        }
    }

    @Test
    fun textureKeepsTheStoredLevels() {
        // Only the boundary search sees encoded levels; the texture the template is made from, and
        // the quality metrics, use the image as given (an encoded iris at 75 would be about 150).
        val image = SyntheticIris(7).render(SyntheticIris.Capture(irisLevel = 75.0, seed = 5))
        val texture = extractNormalized(image, extract)
        assertNotNull(texture)
        val median = quantile(texture.values.filterIndexed { i, _ -> texture.valid[i] }.toFloatArray(), 0.5)
        assertTrue(median in 60f..95f, "texture median $median")
    }
}
