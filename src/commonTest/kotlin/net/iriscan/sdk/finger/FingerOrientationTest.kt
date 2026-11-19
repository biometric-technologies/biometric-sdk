package net.iriscan.sdk.finger

import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.image.ImageColorType
import net.iriscan.sdk.finger.SyntheticFingerprint.Capture
import net.iriscan.sdk.finger.SyntheticFingerprint.Carrier
import net.iriscan.sdk.finger.SyntheticFingerprint.Minutia
import net.iriscan.sdk.finger.SyntheticFingerprint.MinutiaType
import net.iriscan.sdk.finger.impl.OrientationField
import net.iriscan.sdk.finger.impl.orientationField
import net.iriscan.sdk.iris.impl.toGray
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Ridge orientation and coherence, the first step of fingerprint extraction, on synthetic prints. */
class FingerOrientationTest {

    @Test
    fun anglesAreCounterclockwiseFromTheXAxisAsSeenOnTheImage() {
        // Stripes drawn here rather than by the fixture, so that a sign error shared by the fixture and
        // the code cannot pass. With y down: ridges along y = const are horizontal (0); along
        // x + y = const they rise to the right as seen on the image (π/4); along x = const they are
        // vertical (π/2); along x - y = const they fall to the right (3π/4).
        val stripes = listOf<Pair<Double, (Int, Int) -> Double>>(
            0.0 to { _, y -> y.toDouble() },
            PI / 4 to { x, y -> (x + y) / sqrt(2.0) },
            PI / 2 to { x, _ -> x.toDouble() },
            3 * PI / 4 to { x, y -> (x - y) / sqrt(2.0) },
        )
        for ((expected, across) in stripes) {
            val colors = IntArray(64 * 64) { (128 - 60 * cos(2 * PI / 9 * across(it % 64, it / 64))).roundToInt() }
            val field = orientationField(toGray(Image(64, 64, ImageColorType.GRAY, colors)))
            val got = field.orientationAt(32, 32).toDouble()
            val case = "ridges at ${degrees(expected).roundToInt()} degrees"
            assertTrue(axisError(got, expected) < 0.5, "$case: got ${degrees(got)}")
        }
    }

    @Test
    fun straightRidgesInTwelveDirections() {
        // The error is about 0.05 degrees. The limit of 1 degree catches central differences in place
        // of the derivative of a Gaussian, which turn ridges by up to 1.1 degrees at a 9 px period and
        // 2.5 at 6 px.
        for (step in 0 until 12) {
            val direction = step * PI / 12
            val period = PERIODS[step % PERIODS.size]
            val print = SyntheticFingerprint(Carrier.Straight(period, direction))
            val capture = Capture(width = 160, height = 160)
            val field = orientationField(toGray(print.render(capture)))
            val inside = pixelsInside(capture)
            val error = inside.maxOf { (x, y) -> orientationError(print, field, x, y) }
            val coherence = inside.minOf { (x, y) -> field.coherenceAt(x, y) }
            val case = "period $period, gradient at ${degrees(direction).roundToInt()} degrees"
            assertTrue(error <= 1.0, "$case: orientation error $error degrees")
            assertTrue(coherence >= 0.8, "$case: coherence $coherence")
        }
    }

    @Test
    fun concentricRingsAwayFromTheCentre() {
        val period = 9.0
        val print = SyntheticFingerprint(Carrier.Concentric(period, 80.3, 79.6))
        val capture = Capture(width = 160, height = 160)
        val field = orientationField(toGray(print.render(capture)))
        val inside = pixelsInside(capture)
        fun fromCentre(p: Pair<Int, Int>) = hypot(p.first - 80.3, p.second - 79.6) / period
        val error = inside.filter { fromCentre(it) >= 2 }.maxOf { (x, y) -> orientationError(print, field, x, y) }
        val coherence = inside.filter { fromCentre(it) >= 3 }.minOf { (x, y) -> field.coherenceAt(x, y) }
        assertTrue(error <= 5.0, "orientation error $error degrees two periods or more from the centre")
        assertTrue(coherence >= 0.8, "coherence $coherence three periods or more from the centre")
    }

    @Test
    fun noisyRidges() {
        // ridges and valleys 80 gray levels apart, as on a dry finger, with sensor noise of sigma 25
        for (step in 0 until 12) {
            val direction = step * PI / 12
            val print = SyntheticFingerprint(Carrier.Straight(9.0, direction))
            val capture = Capture(width = 160, height = 160, contrast = 40.0, noiseSigma = 25.0, seed = step)
            val field = orientationField(toGray(print.render(capture)))
            val error = pixelsInside(capture).maxOf { (x, y) -> orientationError(print, field, x, y) }
            val case = "gradient at ${degrees(direction).roundToInt()} degrees"
            assertTrue(error <= 8.0, "$case: orientation error $error degrees")
        }
    }

    @Test
    fun coherenceIsZeroOnFlatAreasAndLowOnNoise() {
        // A print in the left part of the image. The filters reach 18 px along x and y, so up to
        // 18·√2 = 25.5 px diagonally; beyond that the background is flat, and its coherence must be
        // exactly 0, not the coherence of rounding errors.
        val print = SyntheticFingerprint(Carrier.Straight(9.0, 0.5))
        val left = Capture(width = 200, height = 160, centerX = 60.0, centerY = 80.0, radiusX = 50.0, radiusY = 70.0)
        val field = orientationField(toGray(print.render(left)))
        var background = 0
        for (y in 0 until left.height) for (x in 0 until left.width) {
            if (!left.isOutside(x.toDouble(), y.toDouble(), margin = 26.0)) continue
            assertEquals(0f, field.coherenceAt(x, y), "coherence of the flat background at ($x, $y)")
            background++
        }
        assertTrue(background > 5000, "background pixels checked: $background")

        // A print without ridges, only noise of sigma 25: no direction, so a low coherence. Single
        // pixels still reach 0.4, so a threshold of 0.3 has to be applied to averages over areas.
        val noise = Capture(width = 160, height = 160, contrast = 0.0, noiseSigma = 25.0, seed = 7)
        val noiseField = orientationField(toGray(print.render(noise)))
        val values = pixelsInside(noise).map { (x, y) -> noiseField.coherenceAt(x, y).toDouble() }.sorted()
        val mean = values.average()
        val p95 = values[(0.95 * (values.size - 1)).roundToInt()]
        assertTrue(mean <= 0.2, "mean coherence of noise $mean")
        assertTrue(p95 <= 0.3, "95th percentile of the coherence of noise $p95")
    }

    @Test
    fun ridgesAroundPlantedMinutiae() {
        val period = 10.0
        val print = SyntheticFingerprint(
            Carrier.Straight(period, PI / 6),
            listOf(
                Minutia(64.0, 64.0, +1, MinutiaType.RIDGE_ENDING),
                Minutia(190.0, 70.0, -1, MinutiaType.BIFURCATION),
                Minutia(70.0, 190.0, -1, MinutiaType.RIDGE_ENDING),
                Minutia(185.0, 185.0, +1, MinutiaType.BIFURCATION),
                Minutia(128.0, 128.0, +1, MinutiaType.RIDGE_ENDING),
                Minutia(128.0, 60.0, +1, MinutiaType.BIFURCATION),
            ),
        )
        // the fixture: every minutia sits at the phase that makes its type
        for ((n, m) in print.minutiae.withIndex()) {
            val expected = if (m.type == MinutiaType.RIDGE_ENDING) PI / 2 else 3 * PI / 2
            assertEquals(expected, print.phaseAround(n), 1e-6, "phase around minutia $n")
        }
        // the field follows the flow that the minutiae bend, from one and a half periods away
        val capture = Capture()
        val field = orientationField(toGray(print.render(capture)))
        val away = pixelsInside(capture).filter { (x, y) ->
            print.minutiae.all { hypot(x - it.x, y - it.y) >= 1.5 * period }
        }
        val error = away.maxOf { (x, y) -> orientationError(print, field, x, y) }
        assertTrue(error <= 3.0, "orientation error $error degrees")
    }

    @Test
    fun sameFieldForAnyBrightnessContrastAndRidgeColour() {
        val period = 9.0
        val print = SyntheticFingerprint(Carrier.Concentric(period, 70.0, 90.0))
        val dark = Capture(width = 160, height = 160)
        val bright = Capture(width = 160, height = 160, level = 90.0, contrast = 30.0, ridgesAreDark = false)
        val gray = toGray(print.render(dark))
        val before = gray.data.copyOf()
        val a = orientationField(gray)
        assertContentEquals(before, gray.data, "the input image must not change")
        val b = orientationField(toGray(print.render(bright)))
        // two periods or more from the centre, where the rings are wide enough to have an orientation
        val worst = pixelsInside(dark).filter { (x, y) -> hypot(x - 70.0, y - 90.0) >= 2 * period }.maxOf { (x, y) ->
            axisError(a.orientationAt(x, y).toDouble(), b.orientationAt(x, y).toDouble())
        }
        assertTrue(worst <= 1.0, "orientation differs by $worst degrees")
    }

    private fun pixelsInside(capture: Capture): List<Pair<Int, Int>> {
        val pixels = ArrayList<Pair<Int, Int>>()
        for (y in 0 until capture.height) for (x in 0 until capture.width) {
            if (capture.isInside(x.toDouble(), y.toDouble(), EDGE_MARGIN)) pixels += x to y
        }
        return pixels
    }

    private fun orientationError(print: SyntheticFingerprint, field: OrientationField, x: Int, y: Int) =
        axisError(field.orientationAt(x, y).toDouble(), print.ridgeOrientation(x.toDouble(), y.toDouble()))

    /** Angle between two axes, degrees, 0 to 90. */
    private fun axisError(a: Double, b: Double): Double {
        val d = abs(a - b).mod(PI)
        return degrees(min(d, PI - d))
    }

    private fun degrees(radians: Double) = radians * 180 / PI

    private companion object {
        /** Ridge periods of the straight prints, pixels: narrow, adult, wide at 500 ppi. */
        val PERIODS = listOf(6.0, 9.0, 12.0)

        /**
         * Pixels checked are at least this far inside the print: the orientation of a pixel depends on
         * the image up to 18 px away (3 px of gradient filter, 15 px of averaging window), and the edge
         * of the print against the background has a direction of its own.
         */
        const val EDGE_MARGIN = 20.0
    }
}
