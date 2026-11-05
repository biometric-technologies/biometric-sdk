package net.iriscan.sdk.finger

import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.image.ImageColorType
import net.iriscan.sdk.finger.SyntheticFingerprint.Capture
import net.iriscan.sdk.finger.SyntheticFingerprint.Carrier
import net.iriscan.sdk.finger.SyntheticFingerprint.Minutia
import net.iriscan.sdk.finger.SyntheticFingerprint.MinutiaType
import net.iriscan.sdk.finger.impl.DEFAULT_RIDGE_PERIOD
import net.iriscan.sdk.finger.impl.ForegroundMask
import net.iriscan.sdk.finger.impl.RidgePeriodField
import net.iriscan.sdk.finger.impl.foregroundMask
import net.iriscan.sdk.finger.impl.orientationField
import net.iriscan.sdk.finger.impl.ridgePeriodField
import net.iriscan.sdk.iris.impl.GrayImage
import net.iriscan.sdk.iris.impl.toGray
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Ridge period, the step of fingerprint extraction after the foreground mask, on synthetic prints. */
class FingerRidgePeriodTest {

    @Test
    fun straightRidgesInTwelveDirections() {
        // The design's target: periods of 6, 9 and 12 px within 0.5 px on an elliptic print on a flat background.
        // Measured: within 0.05 px on every block of the grid, also the blocks outside the mask, which take the period
        // of their neighbours. The mean distance between the peaks of the signature, which the design planned, misses
        // the target here (0.75 px): it finds the peaks on whole pixels.
        for (period in PERIODS) for (step in 0 until 12) {
            val capture = Capture(width = 160, height = 160, noiseSigma = 5.0, seed = step)
            val gray = toGray(SyntheticFingerprint(Carrier.Straight(period, step * PI / 12)).render(capture))
            val before = gray.data.copyOf()
            val (mask, periods) = periodsOf(gray)
            assertContentEquals(before, gray.data, "the input image must not change")
            val case = "period $period, gradient at ${step * 15} degrees"
            assertTrue(mask.blockCount > 0, "$case: no mask")
            assertEquals(mask.columns, periods.columns)
            assertEquals(mask.rows, periods.rows)
            for (row in 0 until periods.rows) for (column in 0 until periods.columns) {
                assertEquals(period, periods.blockPeriod(column, row).toDouble(), 0.5, "$case: block ($column, $row)")
            }
        }
    }

    @Test
    fun noisyRidges() {
        // A dry finger, as in the orientation test: ridges and valleys 80 gray levels apart under noise of sigma 25,
        // on a print that fills the image (on a background with such noise, the mask itself lets noise in). The mean
        // distance between the peaks of the signature, which the design planned, is up to 5.5 px off here for 12 px
        // ridges: the noise splits the flat top of a wide ridge into several peaks. The fitted sinusoid stays within
        // 0.12 px.
        for (step in 0 until 12) {
            val period = PERIODS[step % PERIODS.size]
            val capture = Capture(contrast = 40.0, noiseSigma = 25.0, seed = step, radiusX = 1e4, radiusY = 1e4)
            val (mask, periods) = periodsOf(toGray(SyntheticFingerprint(Carrier.Straight(period, step * PI / 12)).render(capture)))
            val case = "period $period, gradient at ${step * 15} degrees"
            assertTrue(mask.blockCount > 0, "$case: no mask")
            forEachForegroundBlock(mask) { column, row ->
                assertEquals(period, periods.blockPeriod(column, row).toDouble(), 0.5, "$case: block ($column, $row)")
            }
        }
    }

    @Test
    fun concentricRings() {
        // Curved ridges, as in the middle of a whorl. The window is straight, so next to the centre, where the rings
        // bend most, it averages rings of several radii, and the fit there is up to 1 px off or explains too little;
        // with the median of their neighbours, all blocks are within 0.21 px.
        for (period in PERIODS) {
            val print = SyntheticFingerprint(Carrier.Concentric(period, CENTER_X, CENTER_Y))
            val (mask, periods) = periodsOf(toGray(print.render(Capture(noiseSigma = 5.0))))
            var nearTheCentre = 0
            forEachForegroundBlock(mask) { column, row ->
                assertEquals(period, periods.blockPeriod(column, row).toDouble(), 0.5, "period $period: block ($column, $row)")
                if (hypot(centerOf(column) - CENTER_X, centerOf(row) - CENTER_Y) < 2 * period) nearTheCentre++
            }
            assertTrue(nearTheCentre > 0, "period $period: no block within two periods of the centre")
        }
    }

    @Test
    fun ridgesAroundPlantedMinutiae() {
        // Each minutia's spiral makes the ridges denser on one side of it and sparser on the other, also far from it: at
        // the blocks more than one and a half blocks from every minutia, the period of the fixture runs from 9.3 to
        // 11.1 px, and the measured period stays within 0.21 px of it. Closer, the period of the fixture runs from a
        // few pixels to tens of pixels within one window, which gives one period; there the blocks stay within 1.01 px
        // of the 10 px of the carrier.
        val print = SyntheticFingerprint(
            Carrier.Straight(10.0, PI / 6),
            listOf(
                Minutia(64.0, 64.0, +1, MinutiaType.RIDGE_ENDING),
                Minutia(190.0, 70.0, -1, MinutiaType.BIFURCATION),
                Minutia(70.0, 190.0, -1, MinutiaType.RIDGE_ENDING),
                Minutia(185.0, 185.0, +1, MinutiaType.BIFURCATION),
                Minutia(128.0, 128.0, +1, MinutiaType.RIDGE_ENDING),
                Minutia(128.0, 60.0, +1, MinutiaType.BIFURCATION),
            ),
        )
        val (mask, periods) = periodsOf(toGray(print.render(Capture(noiseSigma = 5.0))))
        var away = 0
        var close = 0
        forEachForegroundBlock(mask) { column, row ->
            val x = centerOf(column)
            val y = centerOf(row)
            val period = periods.blockPeriod(column, row).toDouble()
            if (print.minutiae.all { hypot(x - it.x, y - it.y) > 1.5 * BLOCK }) {
                assertEquals(print.ridgePeriod(x, y), period, 0.5, "block ($column, $row)")
                away++
            } else {
                assertEquals(10.0, period, 1.5, "block ($column, $row) next to a minutia")
                close++
            }
        }
        assertTrue(away > 80, "$away blocks away from the minutiae")
        assertTrue(close > 15, "$close blocks next to a minutia")
    }

    @Test
    fun smudgesCreasesAndScarsTakeThePeriodOfTheRidgesAroundThem() {
        // Three defects of real prints, each on a print with 12 px ridges: a dark smudge 80 px across, a bright crease
        // 10 px wide and a dark scar 3 px wide, both across the ridges. The mask holds all three. A window on the smudge
        // explains too little to count, and its block takes the period of its neighbours: not a period of the smudge,
        // nor the default of 9 px. A window that crosses a defect from the ridges beside it can still fit a sinusoid
        // well, with a period up to 11 px off; the median of its neighbours outvotes it (a mean of them would leave
        // blocks up to 2.1 px off). Every block of the mask ends within 0.29 px.
        val capture = Capture(noiseSigma = 5.0)
        val image = SyntheticFingerprint(Carrier.Straight(12.0, 0.8)).render(capture)
        val defects = listOf<Triple<String, Int, (Double, Double) -> Boolean>>(
            Triple("smudge", SMUDGE_LEVEL) { x, y -> hypot(x - SMUDGE_X, y - SMUDGE_Y) <= SMUDGE_RADIUS },
            Triple("crease", CREASE_LEVEL) { x, y -> capture.isInside(x, y) && acrossLine(x, y, 128.0, 128.0, 0.35) <= 5.0 },
            Triple("scar", SCAR_LEVEL) { x, y -> capture.isInside(x, y) && acrossLine(x, y, 140.0, 120.0, 1.05) <= 1.5 },
        )
        for ((name, level, onDefect) in defects) {
            val damaged = IntArray(image.size) {
                if (onDefect((it % image.width).toDouble(), (it / image.width).toDouble())) level else image.colors[it]
            }
            val (mask, periods) = periodsOf(toGray(Image(image.width, image.height, ImageColorType.GRAY, damaged)))
            var onIt = 0
            forEachForegroundBlock(mask) { column, row ->
                assertEquals(12.0, periods.blockPeriod(column, row).toDouble(), 0.5, "$name: block ($column, $row)")
                if (onDefect(centerOf(column), centerOf(row))) onIt++
            }
            assertTrue(onIt > 0, "$name: no block of the mask on it")
        }
    }

    @Test
    fun theDefaultPeriodWithoutRidges() {
        // Nothing to measure, so every block gets the period of adult ridges at 500 ppi.
        val flat = toGray(Image(160, 160, ImageColorType.GRAY, IntArray(160 * 160) { 128 }))
        val noise = toGray(
            SyntheticFingerprint(Carrier.Straight(12.0, 0.5))
                .render(Capture(width = 160, height = 160, contrast = 0.0, noiseSigma = 25.0, radiusX = 1e4, radiusY = 1e4)),
        )
        for ((name, gray) in listOf("flat image" to flat, "noise" to noise)) {
            val (mask, periods) = periodsOf(gray)
            assertEquals(0, mask.blockCount, "$name: mask")
            for (row in 0 until periods.rows) for (column in 0 until periods.columns) {
                assertEquals(DEFAULT_RIDGE_PERIOD, periods.blockPeriod(column, row), "$name: block ($column, $row)")
            }
        }
        assertEquals(9f, DEFAULT_RIDGE_PERIOD)
    }

    @Test
    fun eachPartOfThePrintKeepsItsOwnPeriod() {
        // Ridges 6 px apart on the left half of the image and 12 px apart on the right half, which no single period
        // fits. The windows of the two block columns next to the seam mix both; farther out each half keeps its own
        // period (within 0.03 px), and [RidgePeriodField.periodAt] gives every pixel the period of its block.
        val capture = Capture(width = 320, height = 192, noiseSigma = 5.0, radiusX = 1e4, radiusY = 1e4)
        val narrow = SyntheticFingerprint(Carrier.Straight(6.0, 0.3)).render(capture)
        val wide = SyntheticFingerprint(Carrier.Straight(12.0, 0.3)).render(capture)
        val halves = IntArray(narrow.size) { if (it % narrow.width < SEAM) narrow.colors[it] else wide.colors[it] }
        val (mask, periods) = periodsOf(toGray(Image(narrow.width, narrow.height, ImageColorType.GRAY, halves)))
        var checked = 0
        forEachForegroundBlock(mask) { column, row ->
            val left = (column + 1) * BLOCK <= SEAM - BLOCK
            val right = column * BLOCK >= SEAM + BLOCK
            if (left || right) {
                val expected = if (left) 6.0 else 12.0
                assertEquals(expected, periods.blockPeriod(column, row).toDouble(), 0.5, "block ($column, $row)")
                val x = column * BLOCK + BLOCK / 2
                val y = row * BLOCK + BLOCK / 2
                assertEquals(periods.blockPeriod(column, row), periods.periodAt(x, y), "pixel ($x, $y)")
                checked++
            }
        }
        assertTrue(checked > 100, "$checked blocks checked")
    }

    @Test
    fun brightRidgesOnADarkBackground() {
        // A sensor that shows ridges bright, with faint ridges 60 gray levels deep: the sinusoid fits either sign, and
        // the periods are within 0.05 px.
        var worst = 0.0
        for (period in PERIODS) {
            val capture = Capture(level = 90.0, contrast = 30.0, background = 20.0, ridgesAreDark = false, noiseSigma = 3.0)
            val (mask, periods) = periodsOf(toGray(SyntheticFingerprint(Carrier.Straight(period, 0.5)).render(capture)))
            assertTrue(mask.blockCount > 0, "period $period: no mask")
            forEachForegroundBlock(mask) { column, row ->
                worst = max(worst, abs(periods.blockPeriod(column, row) - period))
            }
        }
        assertTrue(worst <= 0.5, "period off by $worst px")
    }

    private fun periodsOf(gray: GrayImage): Pair<ForegroundMask, RidgePeriodField> {
        val field = orientationField(gray)
        val mask = foregroundMask(gray, field)
        return mask to ridgePeriodField(gray, field, mask)
    }

    private fun forEachForegroundBlock(mask: ForegroundMask, action: (Int, Int) -> Unit) {
        for (row in 0 until mask.rows) for (column in 0 until mask.columns) {
            if (mask.isForegroundBlock(column, row)) action(column, row)
        }
    }

    /** Centre of the blocks of column or row [index], pixels. */
    private fun centerOf(index: Int) = index * BLOCK + (BLOCK - 1) / 2.0

    /** Distance of (x, y) from the line through ([x0], [y0]) at [angle] radians from the x axis (image coordinates, y down). */
    private fun acrossLine(x: Double, y: Double, x0: Double, y0: Double, angle: Double) =
        abs((y - y0) * cos(angle) - (x - x0) * sin(angle))

    private companion object {
        /** Block size of the mask and of the period field, pixels. */
        const val BLOCK = 16

        /** Ridge periods of the prints, pixels: narrow, adult, wide at 500 ppi. */
        val PERIODS = listOf(6.0, 9.0, 12.0)

        /** Centre of the concentric rings, off the block grid. */
        const val CENTER_X = 130.3
        const val CENTER_Y = 120.6

        /** A dark smudge in the middle of the default 256 x 256 capture, 80 px across. */
        const val SMUDGE_X = 128.0
        const val SMUDGE_Y = 120.0
        const val SMUDGE_RADIUS = 40.0
        const val SMUDGE_LEVEL = 60

        /** Gray levels of a crease, brighter than the valleys, and of a scar, darker than the ridges. */
        const val CREASE_LEVEL = 210
        const val SCAR_LEVEL = 40

        /** Column where the left half of the two-period image ends, a block boundary. */
        const val SEAM = 160
    }
}
