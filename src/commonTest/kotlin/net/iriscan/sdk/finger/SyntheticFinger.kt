package net.iriscan.sdk.finger

import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.image.ImageColorType
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Synthetic fingerprints for tests: ridges are the level lines of a phase field, and each spiral
 * term of the field adds one minutia at its center. Captures of the same finger share the field
 * under shift, rotation and noise; different seeds give different fingers.
 * This is not a substitute for real data.
 */
class SyntheticFinger(seed: Int, private val width: Int = 320, private val height: Int = 420) {
    private val cx: Double
    private val cy: Double
    private val singular: List<Triple<Double, Double, Double>>

    init {
        val rnd = Random(seed)
        cx = width / 2.0 + rnd.nextInt(-20, 20)
        cy = height / 2.0 + rnd.nextInt(-20, 20)
        singular = List(14) {
            Triple(cx + rnd.nextDouble(-110.0, 110.0), cy + rnd.nextDouble(-140.0, 140.0), if (rnd.nextBoolean()) 1.0 else -1.0)
        }
    }

    /** Gray image, dark ridges on white. [noise] is the sensor noise in gray levels. */
    fun capture(dx: Int = 0, dy: Int = 0, angle: Double = 0.0, noise: Double = 0.0, noiseSeed: Int = 0): Image {
        val rnd = Random(noiseSeed)
        val ca = cos(angle)
        val sa = sin(angle)
        val colors = IntArray(width * height)
        for (y in 0 until height) for (x in 0 until width) {
            val ux = x - dx - width / 2.0
            val uy = y - dy - height / 2.0
            val px = ca * ux + sa * uy + width / 2.0
            val py = -sa * ux + ca * uy + height / 2.0
            val rx = px - cx
            val ry = (py - cy) * 0.8
            var phase = 2 * PI * sqrt(rx * rx + ry * ry) / PERIOD
            for ((sx, sy, k) in singular) phase += k * atan2(py - sy, px - sx)
            val inside = rx * rx / (130.0 * 130.0) + (py - cy) * (py - cy) / (175.0 * 175.0) < 1
            val darkness = if (inside) 0.5 + 0.4 * cos(phase) else 0.0
            val noiseValue = if (noise > 0) (rnd.nextDouble() + rnd.nextDouble() + rnd.nextDouble() - 1.5) * 2 * noise else 0.0
            colors[y * width + x] = (255 * (1 - darkness) + noiseValue).roundToInt().coerceIn(0, 255)
        }
        return Image(width, height, ImageColorType.GRAY, colors)
    }

    private companion object {
        const val PERIOD = 9.0
    }
}
