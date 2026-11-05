package net.iriscan.sdk.finger

import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.image.ImageColorType
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Synthetic fingerprints for tests: a phase model (Larkin and Fletcher 2007) whose ridge orientation,
 * ridge period and minutiae are known exactly.
 *
 * The phase ψ is a smooth [Carrier] plus one spiral p · (atan2(y − yₙ, x − xₙ) − gₙ) per planted
 * minutia, with polarity p = ±1 and gₙ the direction of the carrier's gradient at the minutia; ridges
 * are where cos ψ > 0. Coordinates are pixels, x to the right and y down. Like
 * [net.iriscan.sdk.iris.SyntheticIris], it catches regressions; it does not measure accuracy, which
 * needs real fingerprints and the benchmark.
 */
class SyntheticFingerprint(private val carrier: Carrier, planted: List<Minutia> = emptyList()) {

    /** Ridge flow without minutiae: a phase whose gradient points across the ridges. */
    sealed class Carrier {
        /** Distance between neighbouring ridge centres, pixels. */
        abstract val period: Double

        abstract fun phase(x: Double, y: Double): Double

        /** Gradient (d/dx, d/dy) of [phase]. */
        abstract fun gradient(x: Double, y: Double): Pair<Double, Double>

        /** Straight parallel ridges; the phase grows along [direction], radians in image coordinates (π/2 is down). */
        class Straight(override val period: Double, val direction: Double) : Carrier() {
            private val k = 2 * PI / period
            override fun phase(x: Double, y: Double) = k * (x * cos(direction) + y * sin(direction))
            override fun gradient(x: Double, y: Double) = Pair(k * cos(direction), k * sin(direction))
        }

        /** Concentric circular ridges around (centerX, centerY), as in the middle of a whorl. */
        class Concentric(override val period: Double, val centerX: Double, val centerY: Double) : Carrier() {
            private val k = 2 * PI / period
            override fun phase(x: Double, y: Double) = k * hypot(x - centerX, y - centerY)
            override fun gradient(x: Double, y: Double): Pair<Double, Double> {
                val r = hypot(x - centerX, y - centerY)
                return if (r == 0.0) Pair(0.0, 0.0) else Pair(k * (x - centerX) / r, k * (y - centerY) / r)
            }
        }
    }

    enum class MinutiaType { RIDGE_ENDING, BIFURCATION }

    /** A minutia at (x, y); [polarity] +1 or -1 is the sense of its spiral, which decides its direction. */
    data class Minutia(val x: Double, val y: Double, val polarity: Int, val type: MinutiaType)

    /**
     * The planted minutiae where they ended up. The phase of everything else at a minutia (carrier and
     * other spirals) decides what its spiral draws: π/2 a ridge ending, 3π/2 a bifurcation, 0 and π
     * an ambiguous fork. So each one is moved along the gradient until that phase is the one of its
     * type, in rounds, since moving one changes the phase at the others. Not at the centre of a
     * concentric carrier, where the gradient vanishes. Directions and positions as ISO/IEC 19794-2
     * records them come with the minutiae detector, whose tests check them.
     */
    val minutiae: List<Minutia>

    /** gₙ: direction of the carrier's gradient at each minutia, the angle its spiral starts from. */
    private val spiralOffsets: DoubleArray

    init {
        val xs = DoubleArray(planted.size) { planted[it].x }
        val ys = DoubleArray(planted.size) { planted[it].y }
        val offsets = DoubleArray(planted.size) { gradientDirection(xs[it], ys[it]) }
        repeat(PLACEMENT_ROUNDS) {
            for (n in planted.indices) {
                var rest = carrier.phase(xs[n], ys[n])
                for (o in planted.indices) {
                    if (o != n) rest += spiral(planted[o].polarity, xs[o], ys[o], offsets[o], xs[n], ys[n])
                }
                val delta = (targetPhase(planted[n].type) - rest + PI).mod(2 * PI) - PI
                val (gx, gy) = carrier.gradient(xs[n], ys[n])
                val k2 = gx * gx + gy * gy
                require(k2 > 0) { "Minutia $n is where the carrier has no gradient" }
                xs[n] += delta * gx / k2
                ys[n] += delta * gy / k2
                offsets[n] = gradientDirection(xs[n], ys[n])
            }
        }
        minutiae = planted.indices.map { planted[it].copy(x = xs[it], y = ys[it]) }
        spiralOffsets = offsets
    }

    /** Phase of everything but minutia [n] at its centre: π/2 for a ridge ending, 3π/2 for a bifurcation. */
    fun phaseAround(n: Int): Double {
        var rest = carrier.phase(minutiae[n].x, minutiae[n].y)
        for (o in minutiae.indices) if (o != n) rest += spiralAt(o, minutiae[n].x, minutiae[n].y)
        return rest.mod(2 * PI)
    }

    fun phase(x: Double, y: Double): Double {
        var psi = carrier.phase(x, y)
        for (n in minutiae.indices) psi += spiralAt(n, x, y)
        return psi
    }

    /**
     * Orientation of the ridges at (x, y), across the gradient of the phase, in the convention of the
     * SDK: radians in [0, π), counterclockwise from the x axis as seen on the image.
     */
    fun ridgeOrientation(x: Double, y: Double): Double {
        val (gx, gy) = phaseGradient(x, y)
        // the gradient angle is measured with y down; the ridges run across it, mirrored to y up
        return (PI / 2 - atan2(gy, gx)).mod(PI)
    }

    /**
     * Ridge period at (x, y), pixels: the distance over which the phase grows by one cycle, 2π over the length of
     * its gradient. The carrier's [Carrier.period] everywhere without minutiae; around a minutia its spiral makes the
     * ridges denser on one side and sparser on the other.
     */
    fun ridgePeriod(x: Double, y: Double): Double {
        val (gx, gy) = phaseGradient(x, y)
        return 2 * PI / hypot(gx, gy)
    }

    /** Gradient (d/dx, d/dy) of [phase]: the carrier's, plus p · (−(y − yₙ), x − xₙ) / r² for each spiral. */
    private fun phaseGradient(x: Double, y: Double): Pair<Double, Double> {
        var (gx, gy) = carrier.gradient(x, y)
        for (m in minutiae) {
            val dx = x - m.x
            val dy = y - m.y
            val r2 = dx * dx + dy * dy
            if (r2 == 0.0) continue
            gx -= m.polarity * dy / r2
            gy += m.polarity * dx / r2
        }
        return Pair(gx, gy)
    }

    /** How a print is captured: its area, gray levels and noise. */
    class Capture(
        val width: Int = 256,
        val height: Int = 256,
        /** The print is an ellipse with this centre and these semi-axes; outside it the flat [background]. */
        val centerX: Double = (width - 1) / 2.0,
        val centerY: Double = (height - 1) / 2.0,
        val radiusX: Double = 0.45 * width,
        val radiusY: Double = 0.45 * height,
        val background: Double = 230.0,
        /** Mean gray level of the print. */
        val level: Double = 128.0,
        /** Amplitude of the ridges: ridge centres at [level] − [contrast], valleys at [level] + [contrast]. */
        val contrast: Double = 60.0,
        /** Standard deviation of Gaussian sensor noise, gray levels, on the print and the background. */
        val noiseSigma: Double = 0.0,
        /** False for a sensor that shows ridges bright. */
        val ridgesAreDark: Boolean = true,
        val seed: Int = 0,
    ) {
        /** 1 on the edge of the print, below 1 inside; it changes by at most 1 / min(radiusX, radiusY) per pixel. */
        private fun ellipticRadius(x: Double, y: Double) = hypot((x - centerX) / radiusX, (y - centerY) / radiusY)

        /** Inside the print, at least [margin] px from its edge: exact for a circle, conservative for an ellipse. */
        fun isInside(x: Double, y: Double, margin: Double = 0.0) =
            ellipticRadius(x, y) <= 1 - margin / min(radiusX, radiusY)

        /** Outside the print, at least [margin] px from its edge: exact for a circle, conservative for an ellipse. */
        fun isOutside(x: Double, y: Double, margin: Double = 0.0) =
            ellipticRadius(x, y) >= 1 + margin / min(radiusX, radiusY)
    }

    fun render(c: Capture): Image {
        val rnd = Random(c.seed)
        val sign = if (c.ridgesAreDark) -1 else 1
        val colors = IntArray(c.width * c.height)
        for (y in 0 until c.height) for (x in 0 until c.width) {
            val px = x.toDouble()
            val py = y.toDouble()
            var value = if (c.isInside(px, py)) c.level + sign * c.contrast * cos(phase(px, py)) else c.background
            if (c.noiseSigma > 0) value += c.noiseSigma * gaussian(rnd)
            colors[y * c.width + x] = value.roundToInt().coerceIn(0, 255)
        }
        return Image(c.width, c.height, ImageColorType.GRAY, colors)
    }

    private fun spiralAt(n: Int, x: Double, y: Double) =
        spiral(minutiae[n].polarity, minutiae[n].x, minutiae[n].y, spiralOffsets[n], x, y)

    private fun spiral(polarity: Int, mx: Double, my: Double, offset: Double, x: Double, y: Double) =
        polarity * (atan2(y - my, x - mx) - offset)

    private fun gradientDirection(x: Double, y: Double): Double {
        val (gx, gy) = carrier.gradient(x, y)
        return atan2(gy, gx)
    }

    private fun targetPhase(type: MinutiaType) = if (type == MinutiaType.RIDGE_ENDING) PI / 2 else 3 * PI / 2

    private fun gaussian(rnd: Random): Double {
        val u = max(1e-12, rnd.nextDouble())
        return sqrt(-2 * ln(u)) * cos(2 * PI * rnd.nextDouble())
    }

    private companion object {
        /** Rounds of the placement, each moving every minutia; 8 bring the minutiae of the tests within 1e-9 rad. */
        const val PLACEMENT_ROUNDS = 8
    }
}
