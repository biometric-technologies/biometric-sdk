package net.iriscan.sdk.face

import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.image.ImageColorType
import net.iriscan.sdk.face.impl.BlazeFace
import kotlin.math.abs
import kotlin.math.ln
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BlazeFaceTest {
    private val n = BlazeFace.NUM_ANCHORS
    private val v = BlazeFace.VALUES_PER_ANCHOR

    private fun near(expected: Double, actual: Double, tolerance: Double = 1e-4) =
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected, got $actual")

    /** Raw score whose sigmoid is [p]. */
    private fun logit(p: Double) = ln(p / (1 - p)).toFloat()

    /** Output with nothing found: all scores far below the threshold. */
    private fun empty() = FloatArray(n * v) to FloatArray(n) { -10f }

    /** Writes a detection at [anchor]: box center offset and size, and every keypoint offset, in input pixels. */
    private fun put(reg: FloatArray, anchor: Int, dx: Float, dy: Float, w: Float, h: Float, kdx: Float = 0f, kdy: Float = 0f) {
        val r = anchor * v
        reg[r] = dx; reg[r + 1] = dy; reg[r + 2] = w; reg[r + 3] = h
        for (k in 0 until BlazeFace.NUM_KEYPOINTS) { reg[r + 4 + 2 * k] = kdx; reg[r + 5 + 2 * k] = kdy }
    }

    @Test
    fun anchorsFollowMediaPipe() {
        val a = BlazeFace.anchors
        assertEquals(2 * n, a.size)
        // first: 16 x 16 grid, two anchors per cell
        near(0.5 / 16, a[0].toDouble()); near(0.5 / 16, a[1].toDouble())
        near(0.5 / 16, a[2].toDouble()); near(1.5 / 16, a[4].toDouble())
        // the 8 x 8 grid starts after 512 anchors, six per cell
        near(0.5 / 8, a[2 * 512].toDouble()); near(0.5 / 8, a[2 * 517].toDouble()); near(1.5 / 8, a[2 * 518].toDouble())
        // last anchor: bottom right cell of the 8 x 8 grid
        near(7.5 / 8, a[2 * (n - 1)].toDouble()); near(7.5 / 8, a[2 * (n - 1) + 1].toDouble())
    }

    @Test
    fun oneDetectionIsPlacedOnItsAnchor() {
        val (reg, scores) = empty()
        val anchor = 512 + 6 * (3 * 8 + 4) // 8 x 8 grid, cell x = 4, y = 3: center (72, 56) in input pixels
        put(reg, anchor, dx = 2f, dy = -4f, w = 40f, h = 48f, kdx = -10f, kdy = 6f)
        scores[anchor] = logit(0.9)
        val box = BlazeFace.letterbox(128, 128)
        val faces = BlazeFace.decode(reg, scores, box)
        assertEquals(1, faces.size)
        val f = faces[0]
        near(0.9, f.score.toDouble())
        near(74.0 - 20, f.left); near(52.0 - 24, f.top); near(74.0 + 20, f.right); near(52.0 + 24, f.bottom)
        near(62.0, f.keypointsX[0]); near(62.0, f.keypointsY[0])
    }

    @Test
    fun overlappingDetectionsAreAveragedByScore() {
        val (reg, scores) = empty()
        // two neighbouring anchors of the 16 x 16 grid at the same cell, boxes 10 px apart
        put(reg, 200, dx = 0f, dy = 0f, w = 40f, h = 40f)
        put(reg, 201, dx = 10f, dy = 0f, w = 40f, h = 40f)
        scores[200] = logit(0.9); scores[201] = logit(0.6)
        val faces = BlazeFace.decode(reg, scores, BlazeFace.letterbox(128, 128))
        assertEquals(1, faces.size)
        val cx = BlazeFace.anchors[400] * 128.0
        near(cx + 10 * 0.6 / 1.5 - 20, faces[0].left)
        near(0.9, faces[0].score.toDouble())
    }

    @Test
    fun separateFacesAndWeakDetections() {
        val (reg, scores) = empty()
        put(reg, 0, 0f, 0f, 12f, 12f)
        put(reg, n - 1, 0f, 0f, 12f, 12f)
        put(reg, 300, 0f, 0f, 12f, 12f)
        scores[0] = logit(0.8); scores[n - 1] = logit(0.95); scores[300] = logit(0.4)
        val faces = BlazeFace.decode(reg, scores, BlazeFace.letterbox(128, 128))
        assertEquals(2, faces.size)
        assertTrue(faces[0].score > faces[1].score, "strongest first")
    }

    @Test
    fun letterboxMapsBackToImagePixels() {
        // a 400 x 200 image is scaled by 0.32 to 128 x 64 and centered 32 px down
        val box = BlazeFace.letterbox(400, 200)
        assertEquals(0, box.offsetX); assertEquals(32, box.offsetY)
        near(200.0, box.toImageX(64.0)); near(100.0, box.toImageY(64.0))
        val (reg, scores) = empty()
        val anchor = 512 + 6 * (4 * 8 + 4) // center (72, 72) in input pixels
        put(reg, anchor, dx = -8f, dy = -8f, w = 32f, h = 32f)
        scores[anchor] = logit(0.9)
        val f = BlazeFace.decode(reg, scores, box).single()
        near((64.0 - 16) / 0.32, f.left); near((64.0 - 16 - 32) / 0.32, f.top)
        near((64.0 + 16) / 0.32, f.right); near((64.0 + 16 - 32) / 0.32, f.bottom)
    }

    @Test
    fun inputIsLetterboxedRgbInMinusOneToOne() {
        val colors = IntArray(40 * 20) { 0xFF0080 }
        val input = BlazeFace.input(Image(40, 20, ImageColorType.RGB, colors))
        val size = BlazeFace.INPUT_SIZE
        assertEquals(size * size * 3, input.size)
        // padding rows above the image stay 0
        assertEquals(0f, input[0]); assertEquals(0f, input[(31 * size) * 3])
        // image rows: red 1, green -1, blue 128 / 127.5 - 1
        val o = (64 * size + 64) * 3
        near(1.0, input[o].toDouble()); near(-1.0, input[o + 1].toDouble()); near(128 / 127.5 - 1, input[o + 2].toDouble())
        val gray = BlazeFace.input(Image(4, 4, ImageColorType.GRAY, IntArray(16) { 255 }))
        near(1.0, gray[(64 * size + 64) * 3 + 1].toDouble())
    }

    @Test
    fun wrongOutputSizesAreRejected() {
        assertFailsWith<IllegalArgumentException> { BlazeFace.decode(FloatArray(10), FloatArray(n), BlazeFace.letterbox(1, 1)) }
    }
}
