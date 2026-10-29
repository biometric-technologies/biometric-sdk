package net.iriscan.sdk.iris

import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.image.ImageColorType
import net.iriscan.sdk.iris.impl.assessQuality
import net.iriscan.sdk.iris.impl.gaussianBlur
import net.iriscan.sdk.iris.impl.toGray
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IrisQualityTest {
    private val extract = IrisExtractProperties()
    private val props = IrisQualityProperties()
    private val iris = SyntheticIris(77)

    private fun assess(image: Image): IrisQuality {
        val q = assessQuality(image, extract, props)
        assertNotNull(q)
        return q
    }

    private fun blurred(image: Image, sigma: Double): Image {
        val g = gaussianBlur(toGray(image), sigma)
        return Image(g.width, g.height, ImageColorType.GRAY, IntArray(g.data.size) { g.data[it].roundToInt() })
    }

    @Test
    fun goodCaptureIsAcceptable() {
        val q = assess(iris.render(SyntheticIris.Capture(upperLid = 1.0, lowerLid = 1.1, seed = 1)))
        assertTrue(q.isAcceptable, "issues: ${q.issues}")
        assertTrue(q.score >= 80, "score ${q.score}")
        assertTrue(q.irisPupilContrast > 30 && q.irisScleraContrast > 5)
    }

    @Test
    fun occlusionLowersUsableArea() {
        val open = assess(iris.render(SyntheticIris.Capture(upperLid = 1.1, lowerLid = 1.1, seed = 2)))
        val closed = assess(iris.render(SyntheticIris.Capture(upperLid = 0.45, lowerLid = 0.7, seed = 2)))
        assertTrue(closed.usableIrisArea < open.usableIrisArea - 15)
        assertTrue(IrisQualityIssue.LOW_USABLE_IRIS_AREA in closed.issues, "issues: ${closed.issues}")
        assertTrue(closed.score < open.score)
    }

    @Test
    fun defocusIsDetected() {
        val image = iris.render(SyntheticIris.Capture(upperLid = 1.0, seed = 3))
        val sharp = assess(image)
        val soft = assess(blurred(image, 4.0))
        assertTrue(sharp.sharpness > soft.sharpness + 30, "sharp ${sharp.sharpness}, soft ${soft.sharpness}")
        assertTrue(IrisQualityIssue.OUT_OF_FOCUS in soft.issues)
    }

    @Test
    fun irisAtImageEdgeIsReported() {
        val q = assess(iris.render(SyntheticIris.Capture(cx = 120.0, upperLid = 1.0, seed = 4)))
        assertTrue(IrisQualityIssue.IRIS_TOO_CLOSE_TO_EDGE in q.issues, "margin ${q.marginAdequacy}")
    }

    @Test
    fun pupilDilationIsMeasured() {
        val q = assess(iris.render(SyntheticIris.Capture(pupilRatio = 0.5, upperLid = 1.0, seed = 5)))
        assertTrue(q.pupilIrisRatio in 46.0..54.0, "ratio ${q.pupilIrisRatio}")
        assertTrue(q.irisPupilConcentricity > 95)
    }
}
