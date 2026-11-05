package net.iriscan.sdk.finger

import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.image.ImageColorType
import net.iriscan.sdk.finger.SyntheticFingerprint.Capture
import net.iriscan.sdk.finger.SyntheticFingerprint.Carrier
import net.iriscan.sdk.finger.impl.ForegroundMask
import net.iriscan.sdk.finger.impl.foregroundMask
import net.iriscan.sdk.finger.impl.orientationField
import net.iriscan.sdk.iris.impl.toGray
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Foreground mask, the step of fingerprint extraction after the orientation field, on synthetic prints. */
class FingerSegmentationTest {

    @Test
    fun maskStaysInsideThePrintAndHoldsItsInterior() {
        val captures = listOf(
            Carrier.Straight(6.0, 0.2) to Capture(noiseSigma = 5.0, seed = 1),
            Carrier.Straight(6.0, 1.0) to Capture(noiseSigma = 5.0, seed = 2),
            Carrier.Straight(9.0, 0.5) to Capture(noiseSigma = 5.0, seed = 3),
            Carrier.Straight(9.0, 2.0) to Capture(noiseSigma = 5.0, seed = 4),
            Carrier.Straight(12.0, 1.4) to Capture(noiseSigma = 5.0, seed = 5),
            Carrier.Straight(12.0, 2.7) to Capture(noiseSigma = 5.0, seed = 6),
            Carrier.Concentric(9.0, 130.3, 120.6) to Capture(noiseSigma = 5.0),
            // off centre, elongated and on a third of the image, so that most blocks are background
            Carrier.Straight(9.0, 0.5) to Capture(
                width = 200, height = 160, centerX = 60.0, centerY = 80.0, radiusX = 50.0, radiusY = 70.0,
                noiseSigma = 5.0,
            ),
            // bright ridges on a dark background, and faint ridges
            Carrier.Straight(9.0, 0.5) to Capture(
                level = 90.0, contrast = 30.0, background = 20.0, ridgesAreDark = false, noiseSigma = 3.0,
            ),
            Carrier.Straight(9.0, 2.5) to Capture(contrast = 20.0, noiseSigma = 3.0),
        )
        for ((n, case) in captures.withIndex()) {
            val (carrier, capture) = case
            val gray = toGray(SyntheticFingerprint(carrier).render(capture))
            val before = gray.data.copyOf()
            val mask = foregroundMask(gray, orientationField(gray))
            assertContentEquals(before, gray.data, "case $n: the input image must not change")
            // The design asked for a mask within one block of the drawn edge, but it also erodes the mask by
            // one block, and the block grid adds up to one more. So: the mask never leaves the print, and it
            // holds every pixel more than two blocks inside it. Measured on these captures, the edge of the
            // mask lies 2.6 to 28.4 px inside the drawn edge.
            for (y in 0 until capture.height) for (x in 0 until capture.width) {
                val px = x.toDouble()
                val py = y.toDouble()
                if (mask.contains(x, y)) assertTrue(capture.isInside(px, py), "case $n: ($x, $y) is outside the print")
                if (capture.isInside(px, py, margin = 2.0 * BLOCK)) {
                    assertTrue(mask.contains(x, y), "case $n: ($x, $y) is not in the mask")
                }
            }
        }
    }

    @Test
    fun aPrintThatFillsTheImageKeepsAllButTheBorderBlocks() {
        // A print larger than the image, pressed harder on the right: the contrast of its ridges grows from
        // 40% to 100% across the image. Nothing is background, so only the blocks along the image border,
        // which the erosion always removes, may leave the mask. Otsu's split of the block contrasts, which
        // the design planned, drops the left part of this print.
        val capture = Capture(radiusX = 1e4, radiusY = 1e4, noiseSigma = 3.0)
        val image = SyntheticFingerprint(Carrier.Straight(9.0, 1.0)).render(capture)
        val pressed = IntArray(image.size) {
            val share = 0.4 + 0.6 * (it % image.width) / (image.width - 1)
            (capture.level + share * (image.colors[it] - capture.level)).roundToInt()
        }
        val mask = maskOf(Image(image.width, image.height, ImageColorType.GRAY, pressed))
        for (row in 0 until mask.rows) for (column in 0 until mask.columns) {
            val inner = row in 1 until mask.rows - 1 && column in 1 until mask.columns - 1
            assertEquals(inner, mask.isForegroundBlock(column, row), "block ($column, $row)")
        }
    }

    @Test
    fun noMaskWithoutRidges() {
        val flat = Image(160, 160, ImageColorType.GRAY, IntArray(160 * 160) { 128 })
        assertEquals(0, maskOf(flat).blockCount, "flat image")
        // Noise has contrast but no orientation. Single blocks of it reach a mean coherence of 0.33, above
        // the threshold, but they come alone, and the mask keeps no group smaller than 3 x 3 blocks.
        val print = SyntheticFingerprint(Carrier.Straight(9.0, 0.5))
        for (seed in 0 until 5) {
            val noise = Capture(contrast = 0.0, noiseSigma = 25.0, seed = seed, radiusX = 1e4, radiusY = 1e4)
            assertEquals(0, maskOf(print.render(noise)).blockCount, "noise, seed $seed")
        }
    }

    @Test
    fun aSmudgeInsideThePrintStaysInTheMask() {
        // A dark smudge without ridges, 5 blocks across: neither contrast nor coherence, but enclosed by the
        // print, so the filling of holes puts it back (the closing alone fills holes of up to 2 blocks).
        val capture = Capture(noiseSigma = 5.0)
        val image = SyntheticFingerprint(Carrier.Straight(9.0, 0.8)).render(capture)
        fun inSmudge(x: Int, y: Int) = hypot(x - SMUDGE_X, y - SMUDGE_Y) <= SMUDGE_RADIUS
        val smudged = IntArray(image.size) {
            if (inSmudge(it % image.width, it / image.width)) SMUDGE_LEVEL else image.colors[it]
        }
        val mask = maskOf(Image(image.width, image.height, ImageColorType.GRAY, smudged))
        for (y in 0 until capture.height) for (x in 0 until capture.width) {
            if (inSmudge(x, y)) assertTrue(mask.contains(x, y), "($x, $y) of the smudge")
        }
    }

    @Test
    fun aCreaseAcrossThePrintDoesNotSplitIt() {
        // A flexion crease: a band without ridges across the whole print, exactly the block rows 7 and 8, the
        // widest gap the closing bridges. Without the closing, the print would fall in two halves and only
        // the larger would be kept.
        val capture = Capture(noiseSigma = 5.0)
        val image = SyntheticFingerprint(Carrier.Straight(9.0, 0.8)).render(capture)
        val creased = IntArray(image.size) {
            val inside = capture.isInside((it % image.width).toDouble(), (it / image.width).toDouble())
            if (inside && it / image.width in 7 * BLOCK until 9 * BLOCK) 200 else image.colors[it]
        }
        val mask = maskOf(Image(image.width, image.height, ImageColorType.GRAY, creased))
        assertTrue(mask.contains(128, 60), "above the crease")
        assertTrue(mask.contains(128, 128), "on the crease")
        assertTrue(mask.contains(128, 200), "below the crease")
    }

    @Test
    fun onlyTheLargestPrintIsKept() {
        // A small second print, such as a latent left on the sensor, joined to the print by a strip of ridges
        // within one block row. The opening cuts the strip and then only the larger group is kept; without
        // either step, the small print would keep blocks after the erosion.
        val width = 360
        val height = 256
        val large = Capture(width, height, centerX = 110.0, centerY = 128.0, radiusX = 95.0, radiusY = 115.0)
        val small = Capture(width, height, centerX = 310.0, centerY = 70.0, radiusX = 40.0, radiusY = 40.0)
        val everywhere = Capture(width, height, radiusX = 1e4, radiusY = 1e4)
        val full = SyntheticFingerprint(Carrier.Straight(9.0, 0.5)).render(everywhere)
        val joined = IntArray(full.size) {
            val x = (it % full.width).toDouble()
            val y = (it / full.width).toDouble()
            val strip = y in 66.0..76.0 && x in 150.0..290.0
            val onPrint = large.isInside(x, y) || small.isInside(x, y) || strip
            if (onPrint) full.colors[it] else large.background.roundToInt()
        }
        val mask = maskOf(Image(full.width, full.height, ImageColorType.GRAY, joined))
        assertTrue(mask.contains(110, 128), "centre of the large print")
        for (y in 0 until full.height) for (x in 0 until full.width) {
            if (mask.contains(x, y)) {
                assertTrue(large.isInside(x.toDouble(), y.toDouble()), "($x, $y) is not in the large print")
            }
        }
    }

    @Test
    fun maskKeepsOneBlockAwayFromTheImageBorder() {
        // A print cut by the right and bottom borders of an image whose size is not a multiple of the block
        // size: blocks along the border, the cut ones too, are never foreground.
        val capture = Capture(
            width = 250, height = 203, centerX = 180.0, centerY = 140.0, radiusX = 140.0, radiusY = 120.0,
            noiseSigma = 5.0,
        )
        val mask = maskOf(SyntheticFingerprint(Carrier.Straight(9.0, 2.2)).render(capture))
        assertEquals(16, mask.columns)
        assertEquals(13, mask.rows)
        for (row in 0 until mask.rows) for (column in 0 until mask.columns) {
            val border = row == 0 || column == 0 || row == mask.rows - 1 || column == mask.columns - 1
            if (border) assertFalse(mask.isForegroundBlock(column, row), "border block ($column, $row)")
        }
        for (y in 0 until capture.height) for (x in 0 until capture.width) {
            val px = x.toDouble()
            val py = y.toDouble()
            if (mask.contains(x, y)) assertTrue(capture.isInside(px, py), "($x, $y) is outside the print")
            // the print up to the border blocks, where it is more than two blocks from its own edge
            val innerBlock = x / BLOCK in 1 until mask.columns - 1 && y / BLOCK in 1 until mask.rows - 1
            if (innerBlock && capture.isInside(px, py, margin = 2.0 * BLOCK)) {
                assertTrue(mask.contains(x, y), "($x, $y) is not in the mask")
            }
        }
    }

    private fun maskOf(image: Image): ForegroundMask {
        val gray = toGray(image)
        return foregroundMask(gray, orientationField(gray))
    }

    private companion object {
        /** Block size of the mask, pixels. */
        const val BLOCK = 16

        /** A dark smudge in the middle of the default 256 x 256 capture, 80 px across. */
        const val SMUDGE_X = 128.0
        const val SMUDGE_Y = 120.0
        const val SMUDGE_RADIUS = 40.0
        const val SMUDGE_LEVEL = 60
    }
}
