package net.iriscan.sdk.iris

import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.image.ImageColorType
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Synthetic near-infrared eye images for tests.
 *
 * Each [SyntheticIris] has its own random texture defined in iris coordinates
 * (angle, normalized radius), so captures of the same iris share the pattern
 * under rotation, pupil dilation, translation, eyelid occlusion, reflections,
 * blur and sensor noise, while different irises are independent.
 * This is not a substitute for real data: use it to catch regressions, and
 * measure accuracy on a real dataset with the benchmark tool.
 */
class SyntheticIris(seed: Int) {
    private val components = 48
    private val m = IntArray(components)
    private val f = DoubleArray(components)
    private val phase = DoubleArray(components)
    private val amp = DoubleArray(components)

    init {
        val rnd = Random(seed)
        for (k in 0 until components) {
            m[k] = rnd.nextInt(4, 48) * (if (rnd.nextBoolean()) 1 else -1)
            f[k] = rnd.nextDouble(0.3, 5.0)
            phase[k] = rnd.nextDouble(0.0, 2 * PI)
            amp[k] = rnd.nextDouble(0.3, 1.0)
        }
        val norm = sqrt(amp.sumOf { it * it } / 2)
        for (k in 0 until components) amp[k] /= norm
    }

    /** Texture value (about -1..1, standard deviation 1) at angle [theta] and radius [rho] in 0..1. */
    fun texture(theta: Double, rho: Double): Double {
        var s = 0.0
        for (k in 0 until components) s += amp[k] * cos(m[k] * theta + 2 * PI * f[k] * rho + phase[k])
        return s
    }

    class Capture(
        val width: Int = 320,
        val height: Int = 280,
        val cx: Double = 160.0,
        val cy: Double = 140.0,
        val irisRadius: Double = 100.0,
        val pupilRatio: Double = 0.4,
        val pupilDx: Double = 0.0,
        val pupilDy: Double = 0.0,
        val rotationDeg: Double = 0.0,
        /** Upper eyelid height above the iris center, in iris radii (>1 = fully open). */
        val upperLid: Double = 0.8,
        val lowerLid: Double = 0.9,
        val noiseSigma: Double = 4.0,
        val reflection: Boolean = true,
        val textureContrast: Double = 22.0,
        val seed: Int = 0,
    ) {
        val pupilX get() = cx + pupilDx
        val pupilY get() = cy + pupilDy
        val pupilRadius get() = irisRadius * pupilRatio
    }

    fun render(c: Capture): Image {
        val rnd = Random(c.seed)
        val w = c.width
        val h = c.height
        val v = DoubleArray(w * h)
        val rot = c.rotationDeg * PI / 180
        val lashes = List(60) { Triple(rnd.nextDouble(-1.0, 1.0), rnd.nextDouble(0.05, 0.25), rnd.nextDouble(-0.4, 0.4)) }
        for (y in 0 until h) for (x in 0 until w) {
            val dx = x - c.cx
            val dy = y - c.cy
            val d = hypot(dx, dy)
            val dp = hypot(x - c.pupilX, y - c.pupilY)
            var value: Double
            if (dp <= c.pupilRadius) {
                value = 22.0
            } else if (d <= c.irisRadius) {
                // invert the rubber-sheet model: P = c(rho) + R(rho) * (cos t, sin t), where the center moves
                // from the pupil center to the iris center and the radius from the pupil to the iris radius
                var lo = 0.0
                var hi = 1.0
                repeat(30) {
                    val mid = (lo + hi) / 2
                    val ccx = c.pupilX + mid * (c.cx - c.pupilX)
                    val ccy = c.pupilY + mid * (c.cy - c.pupilY)
                    val rr = c.pupilRadius + mid * (c.irisRadius - c.pupilRadius)
                    if (hypot(x - ccx, y - ccy) > rr) lo = mid else hi = mid
                }
                val rho = (lo + hi) / 2
                val ccx = c.pupilX + rho * (c.cx - c.pupilX)
                val ccy = c.pupilY + rho * (c.cy - c.pupilY)
                val theta = atan2(y - ccy, x - ccx) - rot
                value = 95.0 + c.textureContrast * texture(theta, rho) - 15 * exp(-rho * 6) + 10 * rho
            } else {
                value = 175.0 - 25 * exp(-(d - c.irisRadius) / 30)
            }
            // eyelids: parabolas, skin above/below
            val nx = dx / (c.irisRadius * 1.6)
            val upperY = c.cy - c.upperLid * c.irisRadius + 0.9 * c.irisRadius * nx * nx
            val lowerY = c.cy + c.lowerLid * c.irisRadius - 0.6 * c.irisRadius * nx * nx
            if (y < upperY || y > lowerY) value = 150.0 + 10 * sin(x * 0.05) + 8 * cos(y * 0.07)
            // eyelashes along the upper lid
            for ((px, len, tilt) in lashes) {
                val lx = c.cx + px * c.irisRadius * 1.3
                val ly = c.cy - c.upperLid * c.irisRadius + 0.9 * c.irisRadius * (px / 1.6 * 1.3) * (px / 1.6 * 1.3)
                val t = (y - ly) / (len * c.irisRadius)
                if (t in 0.0..1.0 && kotlin.math.abs(x - (lx + tilt * t * c.irisRadius * len)) < 1.0) value = 35.0
            }
            v[y * w + x] = value * (1 + 0.08 * (x.toDouble() / w - 0.5))
        }
        // optics blur
        val blurred = blur(v, w, h)
        val out = IntArray(w * h)
        for (i in out.indices) {
            out[i] = (blurred[i] + c.noiseSigma * gaussian(rnd)).roundToInt().coerceIn(0, 255)
        }
        if (c.reflection) {
            val rx = c.pupilX + c.pupilRadius * 0.5 + rnd.nextDouble(-4.0, 4.0)
            val ry = c.pupilY - c.pupilRadius * 0.4 + rnd.nextDouble(-4.0, 4.0)
            for (y in 0 until h) for (x in 0 until w) {
                if (hypot(x - rx, y - ry) < 5) out[y * w + x] = 255
            }
        }
        return Image(w, h, ImageColorType.GRAY, out)
    }

    private fun blur(v: DoubleArray, w: Int, h: Int): DoubleArray {
        val k = doubleArrayOf(0.25, 0.5, 0.25)
        val t = DoubleArray(v.size)
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0.0
            for (j in -1..1) s += k[j + 1] * v[y * w + (x + j).coerceIn(0, w - 1)]
            t[y * w + x] = s
        }
        val o = DoubleArray(v.size)
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0.0
            for (j in -1..1) s += k[j + 1] * t[(y + j).coerceIn(0, h - 1) * w + x]
            o[y * w + x] = s
        }
        return o
    }

    private fun gaussian(rnd: Random): Double {
        val u = max(1e-12, rnd.nextDouble())
        return sqrt(-2 * kotlin.math.ln(u)) * cos(2 * PI * rnd.nextDouble())
    }

    companion object {
        /**
         * A random but realistic capture of one eye: position, dilation, rotation and eyelids vary.
         * [hard] adds heavier occlusion, defocus-like low texture contrast and more sensor noise.
         */
        fun randomCapture(seed: Int, hard: Boolean = false): Capture {
            val r = Random(seed * 7919 + 13)
            return Capture(
                cx = 160.0 + r.nextDouble(-25.0, 25.0),
                cy = 140.0 + r.nextDouble(-15.0, 15.0),
                irisRadius = r.nextDouble(88.0, 104.0),
                pupilRatio = r.nextDouble(0.3, 0.55),
                pupilDx = r.nextDouble(-4.0, 4.0),
                pupilDy = r.nextDouble(-4.0, 4.0),
                rotationDeg = r.nextDouble(-10.0, 10.0),
                upperLid = if (hard) r.nextDouble(0.4, 0.9) else r.nextDouble(0.55, 1.1),
                lowerLid = if (hard) r.nextDouble(0.6, 1.0) else r.nextDouble(0.75, 1.2),
                noiseSigma = if (hard) r.nextDouble(5.0, 10.0) else r.nextDouble(2.0, 6.0),
                textureContrast = if (hard) r.nextDouble(8.0, 16.0) else 22.0,
                seed = seed,
            )
        }
    }
}
