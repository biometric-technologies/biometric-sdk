package net.iriscan.sdk.finger.impl

import net.iriscan.sdk.iris.impl.GrayImage
import net.iriscan.sdk.iris.impl.gaussianBlur
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Scale of the gradients, in pixels at 500 ppi: the image is differentiated with the derivative of a
 * Gaussian of this sigma. Ridges are about 4 to 5 px wide at 500 ppi (a ridge period of 9 px for
 * adults), so one pixel smooths sensor noise and 8-bit steps while narrow ridges keep their gradient.
 * Central differences would turn the orientation of ridges with a 6 px period by up to 2.5 degrees,
 * depending on their direction; the derivative of a Gaussian turns it by less than 0.1 degree.
 */
private const val GRADIENT_SIGMA = 1.0

/**
 * Size of the area that one orientation describes, in pixels at 500 ppi: the products of the gradients
 * are averaged with a Gaussian of this sigma. It spans about two ridge periods: enough to average noise
 * and the gaps of broken ridges, small enough to follow ridges that curve around a core.
 */
private const val ORIENTATION_WINDOW_SIGMA = 5.0

/**
 * Ridge orientation and its coherence at every pixel of a fingerprint image.
 *
 * Angles follow ISO/IEC 19794-2, like every step of the fingerprint pipeline: counterclockwise from the
 * x axis as seen on the image, that is atan2(-dy, dx) for a step (dx, dy) in image coordinates, where
 * y points down. A ridge orientation is an axis, so [orientation] holds radians in [0, π): 0 for
 * horizontal ridges, π/4 for ridges rising to the right, π/2 for vertical ridges.
 *
 * [coherence], from 0 to 1, tells how well that one orientation explains the gradients around the
 * pixel: close to 1 on parallel ridges, low on noise and where the flow turns sharply (cores, deltas),
 * 0 where the image is flat. Where it is low, the orientation means little.
 */
internal class OrientationField(
    val width: Int,
    val height: Int,
    val orientation: FloatArray,
    val coherence: FloatArray,
) {
    fun orientationAt(x: Int, y: Int): Float = orientation[y * width + x]

    fun coherenceAt(x: Int, y: Int): Float = coherence[y * width + x]
}

/**
 * Orientation field of [image] from the averaged structure tensor of its gradients (Bazen and Gerez
 * 2002). Gxx, Gyy and Gxy, the averages of gx², gy² and gx·gy around a pixel, give the dominant
 * gradient angle ½·atan2(2·Gxy, Gxx − Gyy), across the ridges, and the coherence
 * √((Gxx − Gyy)² + 4·Gxy²) / (Gxx + Gyy). Averaging the squared gradients, in effect gradients with
 * doubled angles, is what lets the two flanks of a ridge add up: their gradients point in opposite
 * directions and would cancel in a plain average.
 *
 * Dark and bright ridges give the same field. The parameters are for images at 500 ppi; [image] is not
 * modified.
 */
internal fun orientationField(
    image: GrayImage,
    gradientSigma: Double = GRADIENT_SIGMA,
    windowSigma: Double = ORIENTATION_WINDOW_SIGMA,
): OrientationField {
    val w = image.width
    val h = image.height
    val (gx, gy) = gradients(image, gradientSigma)
    val gxx = gaussianBlur(GrayImage(w, h, FloatArray(w * h) { gx[it] * gx[it] }), windowSigma).data
    val gyy = gaussianBlur(GrayImage(w, h, FloatArray(w * h) { gy[it] * gy[it] }), windowSigma).data
    val gxy = gaussianBlur(GrayImage(w, h, FloatArray(w * h) { gx[it] * gy[it] }), windowSigma).data
    val orientation = FloatArray(w * h)
    val coherence = FloatArray(w * h)
    for (i in 0 until w * h) {
        val difference = gxx[i].toDouble() - gyy[i]
        val cross = 2.0 * gxy[i]
        val energy = gxx[i].toDouble() + gyy[i]
        // The gradient angle is measured in image coordinates, y down. The ridges run across it, and
        // seen on the image, y up, the angle changes sign: -(angle + π/2) is π/2 - angle as an axis.
        orientation[i] = axisToFloat(PI / 2 - 0.5 * atan2(cross, difference))
        val anisotropy = sqrt(difference * difference + cross * cross)
        coherence[i] = if (energy > 0.0) min(1.0, anisotropy / energy).toFloat() else 0f
    }
    return OrientationField(w, h, orientation, coherence)
}

/** An axis angle in [0, π] as a Float in [0, π): an angle that is or rounds to π is the axis 0. */
private fun axisToFloat(angle: Double): Float {
    val value = angle.toFloat()
    return if (value >= PI.toFloat()) 0f else value
}

/**
 * Derivatives of [image] along x and y: the derivative of a Gaussian across, the Gaussian along,
 * borders clamped. Both use the same weights, so the factor by which they differ from the true
 * derivatives is common and cancels in the orientation and the coherence.
 */
private fun gradients(image: GrayImage, sigma: Double): Pair<FloatArray, FloatArray> {
    val w = image.width
    val h = image.height
    val radius = max(1, ceil(3 * sigma).toInt())
    val smooth = FloatArray(radius + 1) { exp(-(it * it) / (2 * sigma * sigma)).toFloat() }
    var sum = smooth[0]
    for (n in 1..radius) sum += 2 * smooth[n]
    for (n in smooth.indices) smooth[n] /= sum
    val slope = FloatArray(radius + 1) { it * smooth[it] }
    val derivativeAlongX = filter1D(image.data, w, h, slope, odd = true, alongX = true)
    val smoothAlongX = filter1D(image.data, w, h, smooth, odd = false, alongX = true)
    val gx = filter1D(derivativeAlongX, w, h, smooth, odd = false, alongX = false)
    val gy = filter1D(smoothAlongX, w, h, slope, odd = true, alongX = false)
    return gx to gy
}

/**
 * One-dimensional filter along rows ([alongX]) or columns, borders clamped. [weights] are for the
 * offsets 0 to r and mirrored for -r to -1: added for a smoothing filter, subtracted for a derivative
 * ([odd]). A derivative subtracts each pixel pair before weighting it, so that flat areas get
 * gradients of exactly 0: weighted one by one, rounding would leave them a tiny gradient of the same
 * direction everywhere, which has a coherence of 1.
 */
private fun filter1D(
    src: FloatArray,
    w: Int,
    h: Int,
    weights: FloatArray,
    odd: Boolean,
    alongX: Boolean,
): FloatArray {
    val out = FloatArray(src.size)
    for (y in 0 until h) for (x in 0 until w) {
        var s = if (odd) 0f else weights[0] * src[y * w + x]
        for (n in 1 until weights.size) {
            val before: Float
            val after: Float
            if (alongX) {
                before = src[y * w + max(x - n, 0)]
                after = src[y * w + min(x + n, w - 1)]
            } else {
                before = src[max(y - n, 0) * w + x]
                after = src[min(y + n, h - 1) * w + x]
            }
            s += weights[n] * (if (odd) after - before else after + before)
        }
        out[y * w + x] = s
    }
    return out
}
