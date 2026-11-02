package net.iriscan.sdk.iris.impl

import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.image.ImageColorType
import net.iriscan.sdk.iris.IrisExtractProperties
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Value used in unwrapped texture images for occluded samples. */
internal const val MASKED_PIXEL = -1

/**
 * Unwrapped iris: rows go from the pupil (0) to the limbus, columns go around
 * the eye starting at 0 degrees (to the right) in image coordinates.
 */
internal class NormalizedIris(val rows: Int, val cols: Int, val values: FloatArray, val valid: BooleanArray) {
    val validFraction: Double get() = valid.count { it }.toDouble() / valid.size

    fun toImage(): Image = Image(
        cols, rows, ImageColorType.GRAY,
        IntArray(rows * cols) { if (valid[it]) values[it].roundToInt().coerceIn(0, 255) else MASKED_PIXEL }
    )

    companion object {
        /** Reads a texture produced by [toImage] (or any grayscale texture, resampled to the given size). */
        fun fromImage(image: Image, rows: Int, cols: Int): NormalizedIris {
            val values = FloatArray(rows * cols)
            val valid = BooleanArray(rows * cols)
            for (r in 0 until rows) for (c in 0 until cols) {
                val x = min(image.width - 1, (c * image.width) / cols)
                val y = min(image.height - 1, (r * image.height) / rows)
                val raw = image[x, y]
                val i = r * cols + c
                if (image.colorType == ImageColorType.GRAY && raw == MASKED_PIXEL) continue
                valid[i] = true
                values[i] = when (image.colorType) {
                    ImageColorType.GRAY -> raw.coerceIn(0, 255).toFloat()
                    ImageColorType.BINARY -> if (raw != 0) 255f else 0f
                    ImageColorType.RGB -> {
                        val rr = (raw ushr 16) and 0xFF
                        val gg = (raw ushr 8) and 0xFF
                        val bb = raw and 0xFF
                        0.299f * rr + 0.587f * gg + 0.114f * bb
                    }
                }
            }
            return NormalizedIris(rows, cols, values, valid)
        }
    }
}

/**
 * Daugman's rubber-sheet model: each point is interpolated between the pupil
 * and the limbus boundary at the same angle, which handles non-concentric
 * circles and pupil dilation.
 */
internal fun normalizeIris(
    full: GrayImage,
    seg: IrisSegmentation,
    props: IrisExtractProperties,
): NormalizedIris {
    val rows = props.normalizedHeight
    val cols = props.normalizedWidth
    val reflections = reflectionMask(full, dilate = max(1, (min(full.width, full.height) / 120.0).roundToInt()))
    val values = FloatArray(rows * cols)
    val valid = BooleanArray(rows * cols)
    val p = seg.pupil
    val ir = seg.iris
    for (c in 0 until cols) {
        val theta = 2.0 * PI * c / cols
        val ct = cos(theta)
        val st = sin(theta)
        val px = p.x + p.r * ct
        val py = p.y + p.r * st
        val ix = ir.x + ir.r * ct
        val iy = ir.y + ir.r * st
        for (r in 0 until rows) {
            // keep away from the blurred boundaries themselves
            val rho = 0.04 + 0.92 * (r + 0.5) / rows
            val x = (1 - rho) * px + rho * ix
            val y = (1 - rho) * py + rho * iy
            val i = r * cols + c
            val v = full.bilinear(x, y)
            if (v.isNaN()) continue
            val xi = x.roundToInt().coerceIn(0, full.width - 1)
            val yi = y.roundToInt().coerceIn(0, full.height - 1)
            if (reflections[yi * full.width + xi]) continue
            if (seg.upperEyelid != null && y < seg.upperEyelid.y(x)) continue
            if (seg.lowerEyelid != null && y > seg.lowerEyelid.y(x)) continue
            values[i] = v
            valid[i] = true
        }
    }
    maskOutliers(values, valid, rows, cols, props.outlierSigma)
    return NormalizedIris(rows, cols, values, valid)
}

/**
 * Masks samples far from the typical iris intensity (eyelashes are darker, eyelid
 * skin and residual glints brighter). The reference median and spread are taken
 * from the lateral sectors, which eyelids rarely cover, so that a large occluded
 * area does not shift the statistics.
 */
private fun maskOutliers(values: FloatArray, valid: BooleanArray, rows: Int, cols: Int, k: Double) {
    val ref = ArrayList<Float>()
    for (r in 0 until rows) for (c in 0 until cols) {
        val deg = 360.0 * c / cols
        val lateral = deg <= 35.0 || deg >= 325.0 || deg in 145.0..215.0
        val i = r * cols + c
        if (lateral && valid[i]) ref += values[i]
    }
    val v = (if (ref.size >= 64) ref else values.filterIndexed { i, _ -> valid[i] }).toFloatArray()
    if (v.size < 16) return
    val med = quantile(v, 0.5)
    val mad = quantile(FloatArray(v.size) { abs(v[it] - med) }, 0.5)
    val sigma = max(2.0, 1.4826 * mad)
    for (i in values.indices) {
        if (valid[i] && abs(values[i] - med) > k * sigma) valid[i] = false
    }
}
