package net.iriscan.sdk.face

import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.image.ImageColorType
import net.iriscan.sdk.face.impl.cropFace
import net.iriscan.sdk.face.impl.embeddingDistance
import net.iriscan.sdk.face.impl.eyeLineAngle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FaceAlignmentTest {

    /** Gray image with two dark "eyes" on a line tilted by [deg] around (100, 100). */
    private fun eyes(deg: Double): Image {
        val a = deg * PI / 180
        val e1 = 100 - 30 * cos(a) to 100 - 30 * sin(a)
        val e2 = 100 + 30 * cos(a) to 100 + 30 * sin(a)
        val colors = IntArray(200 * 200) { i ->
            val x = i % 200
            val y = i / 200
            val d1 = (x - e1.first) * (x - e1.first) + (y - e1.second) * (y - e1.second)
            val d2 = (x - e2.first) * (x - e2.first) + (y - e2.second) * (y - e2.second)
            if (d1 < 64 || d2 < 64) 0 else 200
        }
        return Image(200, 200, ImageColorType.GRAY, colors)
    }

    private fun darkCentroidRow(img: Image, xFrom: Int, xTo: Int): Double {
        var s = 0.0
        var n = 0
        for (y in 0 until img.height) for (x in xFrom until xTo) {
            if ((img[x, y] and 0xFF) < 100) {
                s += y; n++
            }
        }
        return s / n
    }

    @Test
    fun cropLevelsTheEyeLine() {
        val crop = cropFace(eyes(20.0), 100.0, 100.0, 120.0, 20.0, 120)
        val left = darkCentroidRow(crop, 0, 60)
        val right = darkCentroidRow(crop, 60, 120)
        assertEquals(left, right, 1.0)
        assertEquals(60.0, left, 1.5)
    }

    @Test
    fun eyeAngleSignAndLimits() {
        assertEquals(10.0, eyeLineAngle(0.0, 0.0, 100.0, 100 * tan(10 * PI / 180))!!, 1e-9)
        assertEquals(-10.0, eyeLineAngle(0.0, 0.0, 100.0, -100 * tan(10 * PI / 180))!!, 1e-9)
        assertNull(eyeLineAngle(0.0, 0.0, 10.0, 10.0)) // 45 degrees is not a plausible roll
        assertNull(eyeLineAngle(10.0, 0.0, 0.0, 0.0)) // eyes swapped
    }

    @Test
    fun distanceIsScaleInvariant() {
        val a = floatArrayOf(1f, 0f, 0f)
        val b = floatArrayOf(0f, 3f, 0f)
        assertEquals(sqrt(2.0), embeddingDistance(a, b).toDouble(), 1e-6)
        assertEquals(0.0, embeddingDistance(a, floatArrayOf(5f, 0f, 0f)).toDouble(), 1e-6)
        assertTrue(embeddingDistance(a, floatArrayOf(-1f, 0f, 0f)) > 1.99f)
    }
}
