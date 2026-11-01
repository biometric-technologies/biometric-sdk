package net.iriscan.sdk.face.haar

import net.iriscan.sdk.face.impl.haar.Box
import net.iriscan.sdk.face.impl.haar.GrayPlane
import net.iriscan.sdk.face.impl.haar.HaarCascade
import net.iriscan.sdk.face.impl.haar.HaarDetector
import net.iriscan.sdk.face.impl.haar.IntegralImage
import net.iriscan.sdk.face.impl.haar.equalizeHistogram
import net.iriscan.sdk.face.impl.haar.groupBoxes
import net.iriscan.sdk.face.impl.haar.resizeBilinear
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HaarDetectorTest {

    @Test
    fun `integral image sums equal direct sums`() {
        val random = Random(7)
        val plane = GrayPlane(13, 9, IntArray(13 * 9) { random.nextInt(256) })
        val ii = IntegralImage(plane)
        for ((x, y, w, h) in listOf(listOf(0, 0, 13, 9), listOf(2, 3, 4, 5), listOf(12, 8, 1, 1), listOf(5, 0, 8, 2))) {
            var sum = 0
            var squares = 0.0
            for (yy in y until y + h) for (xx in x until x + w) {
                sum += plane[xx, yy]; squares += plane[xx, yy] * plane[xx, yy]
            }
            assertEquals(sum, ii.sum(x, y, w, h), "sum of $x,$y ${w}x$h")
            assertEquals(squares, ii.squareSum(x, y, w, h), "squares of $x,$y ${w}x$h")
        }
    }

    @Test
    fun `histogram equalization spreads the levels like OpenCV`() {
        // levels 10, 20, 30, 40 once each: OpenCV maps the lowest to 0 and the rest to 255 * (rank / 3)
        val out = equalizeHistogram(GrayPlane(4, 1, intArrayOf(40, 10, 30, 20)))
        assertContentEquals(intArrayOf(255, 0, 170, 85), out.pixels)
    }

    @Test
    fun `a flat image stays flat after equalization`() {
        val out = equalizeHistogram(GrayPlane(3, 2, IntArray(6) { 77 }))
        assertContentEquals(IntArray(6) { 77 }, out.pixels)
    }

    @Test
    fun `bilinear resize keeps a constant image and averages neighbors when halving`() {
        assertContentEquals(IntArray(4) { 90 }, resizeBilinear(GrayPlane(4, 4, IntArray(16) { 90 }), 2, 2).pixels)
        // halving a 0/100 column pattern samples between the columns: 50 everywhere
        val stripes = GrayPlane(4, 2, intArrayOf(0, 100, 0, 100, 0, 100, 0, 100))
        assertContentEquals(intArrayOf(50, 50), resizeBilinear(stripes, 2, 1).pixels)
    }

    @Test
    fun `similar boxes are merged into their average and lonely boxes dropped`() {
        val face = listOf(Box(100, 100, 50, 50), Box(102, 99, 50, 50), Box(98, 101, 52, 52), Box(101, 100, 48, 48))
        val noise = listOf(Box(300, 20, 30, 30))
        val grouped = groupBoxes(face + noise, minNeighbors = 3, eps = 0.2)
        assertEquals(listOf(Box(100, 100, 50, 50)), grouped)
        // a group needs more than minNeighbors hits
        assertEquals(emptyList(), groupBoxes(face, minNeighbors = 4, eps = 0.2))
        assertEquals(face + noise, groupBoxes(face + noise, minNeighbors = 0, eps = 0.2))
    }

    @Test
    fun `a small group inside a better supported box is dropped`() {
        val face = List(8) { Box(100 + it % 2, 100, 100, 100) }
        val eye = List(4) { Box(120, 120 + it % 2, 30, 30) }
        assertEquals(listOf(Box(101, 100, 100, 100)), groupBoxes(face + eye, minNeighbors = 3, eps = 0.2))
    }

    @Test
    fun `finds a pattern the cascade describes, at its position and size`() {
        // one stage, one stump: "the right half of the window is brighter than the window on average"
        val cascade = HaarCascade(
            20, 20,
            listOf(HaarCascade.Stage(0.5, listOf(HaarCascade.WeakClassifier(listOf(HaarCascade.Node(0, -1, 0, 0.05)), doubleArrayOf(0.0, 1.0))))),
            listOf(HaarCascade.Feature(listOf(HaarCascade.WeightedRect(0, 0, 20, 20, -1.0), HaarCascade.WeightedRect(10, 0, 10, 20, 2.0)))),
        )
        // mid-gray noise with one 40 x 40 square: dark left half, bright right half
        val random = Random(1)
        val size = 160
        val pixels = IntArray(size * size) { 110 + random.nextInt(20) }
        for (y in 60 until 100) for (x in 50 until 90) pixels[y * size + x] = if (x < 70) 20 else 230
        val boxes = HaarDetector(cascade).detect(GrayPlane(size, size, pixels), minSize = 30)

        val best = boxes.maxByOrNull { it.area }
        assertTrue(best != null, "no detection")
        assertTrue(abs(best.x + best.width / 2 - 70) <= 6 && abs(best.y + best.height / 2 - 80) <= 6, "detection $best is off the square")
        // this one-feature cascade also accepts somewhat larger windows around the square
        assertTrue(best.width in 30..64, "detection $best has the wrong size")
    }

    @Test
    fun `flat windows are never detections`() {
        val cascade = HaarCascade(
            20, 20,
            listOf(HaarCascade.Stage(-1.0, listOf(HaarCascade.WeakClassifier(listOf(HaarCascade.Node(0, -1, 0, 0.0)), doubleArrayOf(0.0, 0.0))))),
            listOf(HaarCascade.Feature(listOf(HaarCascade.WeightedRect(0, 0, 20, 20, 1.0)))),
        )
        // the stage always passes, but a uniform image has no contrast to normalize by
        assertEquals(emptyList(), HaarDetector(cascade).detect(GrayPlane(64, 64, IntArray(64 * 64) { 128 }), minNeighbors = 0))
    }
}
