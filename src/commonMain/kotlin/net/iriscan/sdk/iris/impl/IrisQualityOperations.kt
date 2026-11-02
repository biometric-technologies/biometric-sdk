package net.iriscan.sdk.iris.impl

import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.iris.IrisExtractProperties
import net.iriscan.sdk.iris.IrisQuality
import net.iriscan.sdk.iris.IrisQualityIssue
import net.iriscan.sdk.iris.IrisQualityProperties
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Scale of the focus score: power at which the sharpness score is 50. */
private const val FOCUS_HALF_POWER = 120.0

internal fun assessQuality(sample: Image, extract: IrisExtractProperties, props: IrisQualityProperties): IrisQuality? {
    val gray = toGray(sample)
    val seg = segmentIris(gray, extract) ?: return null
    val normalized = normalizeIris(gray, seg, extract)
    val p = seg.pupil
    val ir = seg.iris

    val usable = 100.0 * normalized.validFraction

    // medians in the lateral sectors, which eyelids rarely cover
    val lateral = listOf(-30, -20, -10, 0, 10, 20, 30, 150, 160, 170, 180, 190, 200, 210)
    fun ringMedian(rho: ClosedRange<Double>, outside: Boolean): Double? {
        val v = ArrayList<Float>()
        for (deg in lateral) {
            val a = deg * PI / 180
            for (k in 0..4) {
                val t = rho.start + (rho.endInclusive - rho.start) * k / 4
                val x: Double
                val y: Double
                if (outside) {
                    x = ir.x + ir.r * t * cos(a); y = ir.y + ir.r * t * sin(a)
                } else {
                    x = (1 - t) * (p.x + p.r * cos(a)) + t * (ir.x + ir.r * cos(a))
                    y = (1 - t) * (p.y + p.r * sin(a)) + t * (ir.y + ir.r * sin(a))
                }
                val s = gray.bilinear(x, y)
                if (!s.isNaN()) v += s
            }
        }
        return if (v.size < 8) null else quantile(v.toFloatArray(), 0.5).toDouble()
    }
    val pupilValues = ArrayList<Float>()
    for (k in 0 until 32) {
        val a = 2 * PI * k / 32
        for (t in listOf(0.2, 0.45, 0.7)) {
            val s = gray.bilinear(p.x + p.r * t * cos(a), p.y + p.r * t * sin(a))
            if (!s.isNaN()) pupilValues += s
        }
    }
    // glints inside the pupil are bright; the lower half of the samples is the pupil itself
    val pupil = quantile(pupilValues.toFloatArray(), 0.25).toDouble()
    val irisOuter = ringMedian(0.7..0.9, outside = false) ?: return null
    val irisInner = ringMedian(0.1..0.4, outside = false) ?: return null
    val sclera = ringMedian(1.1..1.25, outside = true)
    val isc = sclera?.let { 100.0 * (it - irisOuter) / max(1.0, it + irisOuter) } ?: 0.0
    val ipc = 100.0 * (irisInner - pupil) / max(1.0, irisInner + pupil)

    val gsu = entropyBits(gray)
    val ratio = 100.0 * p.r / ir.r
    val concentricity = (100.0 * (1 - hypot(p.x - ir.x, p.y - ir.y) / ir.r)).coerceIn(0.0, 100.0)
    val margin = (100.0 * minOf(
        (ir.x - ir.r) / (0.6 * ir.r),
        (gray.width - 1 - ir.x - ir.r) / (0.6 * ir.r),
        (ir.y - ir.r) / (0.2 * ir.r),
        (gray.height - 1 - ir.y - ir.r) / (0.2 * ir.r),
    )).coerceIn(0.0, 100.0)
    val sharpness = focusScore(gray, seg)

    val issues = ArrayList<IrisQualityIssue>()
    if (usable < props.minUsableIrisArea) issues += IrisQualityIssue.LOW_USABLE_IRIS_AREA
    if (isc < props.minIrisScleraContrast) issues += IrisQualityIssue.LOW_IRIS_SCLERA_CONTRAST
    if (ipc < props.minIrisPupilContrast) issues += IrisQualityIssue.LOW_IRIS_PUPIL_CONTRAST
    if (gsu < props.minGreyScaleUtilisation) issues += IrisQualityIssue.LOW_GREY_SCALE_UTILISATION
    if (ir.r < props.minIrisRadius) issues += IrisQualityIssue.SMALL_IRIS
    if (ratio !in props.pupilIrisRatioRange) issues += IrisQualityIssue.PUPIL_TOO_SMALL_OR_LARGE
    if (concentricity < props.minIrisPupilConcentricity) issues += IrisQualityIssue.LOW_CONCENTRICITY
    if (margin < props.minMarginAdequacy) issues += IrisQualityIssue.IRIS_TOO_CLOSE_TO_EDGE
    if (sharpness < props.minSharpness) issues += IrisQualityIssue.OUT_OF_FOCUS

    // combined score: usable area, reduced by each metric below its threshold
    fun f(value: Double, threshold: Double) = if (threshold <= 0) 1.0 else (value / threshold).coerceIn(0.0, 1.0)
    val ratioFactor = when {
        ratio < props.pupilIrisRatioRange.start -> f(ratio, props.pupilIrisRatioRange.start)
        ratio > props.pupilIrisRatioRange.endInclusive -> f(100 - ratio, 100 - props.pupilIrisRatioRange.endInclusive)
        else -> 1.0
    }
    val score = usable *
            f(sharpness, props.minSharpness) *
            f(isc, props.minIrisScleraContrast) *
            f(ipc, props.minIrisPupilContrast) *
            f(gsu, props.minGreyScaleUtilisation) *
            f(ir.r, props.minIrisRadius) *
            f(concentricity, props.minIrisPupilConcentricity) *
            f(margin, props.minMarginAdequacy) *
            ratioFactor

    return IrisQuality(
        usableIrisArea = usable,
        irisScleraContrast = isc,
        irisPupilContrast = ipc,
        greyScaleUtilisation = gsu,
        irisRadius = ir.r,
        pupilIrisRatio = ratio,
        irisPupilConcentricity = concentricity,
        marginAdequacy = margin,
        sharpness = sharpness,
        score = score.roundToInt().coerceIn(0, 100),
        issues = issues,
    )
}

private fun entropyBits(img: GrayImage): Double {
    val hist = IntArray(256)
    for (v in img.data) hist[v.roundToInt().coerceIn(0, 255)]++
    val n = img.data.size.toDouble()
    var e = 0.0
    for (c in hist) if (c > 0) {
        val p = c / n
        e -= p * ln(p) / ln(2.0)
    }
    return e
}

/**
 * Daugman's focus measure: power of the response to an 8x8 kernel (-1 outside,
 * +3 in the central 4x4) over the iris, mapped to 0-100 by x^2 / (x^2 + c^2).
 * The image is rescaled so that the iris radius is about 100 pixels, which makes
 * the score comparable across resolutions.
 */
private fun focusScore(gray: GrayImage, seg: IrisSegmentation): Double {
    val x2 = focusPower(gray, seg)
    return 100.0 * x2 * x2 / (x2 * x2 + FOCUS_HALF_POWER * FOCUS_HALF_POWER)
}

internal fun focusPower(gray: GrayImage, seg: IrisSegmentation): Double {
    val scale = min(1.0, 100.0 / seg.iris.r)
    val img = downscale(gray, scale)
    val px = (seg.pupil.x + 0.5) * scale - 0.5
    val py = (seg.pupil.y + 0.5) * scale - 0.5
    val pr = seg.pupil.r * scale
    val ix = (seg.iris.x + 0.5) * scale - 0.5
    val iy = (seg.iris.y + 0.5) * scale - 0.5
    val irr = seg.iris.r * scale
    var power = 0.0
    var n = 0
    var y = 0
    while (y + 8 <= img.height) {
        var x = 0
        while (x + 8 <= img.width) {
            val cx = x + 3.5
            val cy = y + 3.5
            val inIris = hypot(cx - ix, cy - iy) < irr - 5 && hypot(cx - px, cy - py) > pr + 5
            val belowLid = seg.upperEyelid?.let { lid -> (cy - 4) / scale > lid.y(cx / scale) } ?: true
            val aboveLid = seg.lowerEyelid?.let { lid -> (cy + 4) / scale < lid.y(cx / scale) } ?: true
            if (inIris && belowLid && aboveLid) {
                var r = 0.0
                for (j in 0 until 8) for (i in 0 until 8) {
                    val k = if (i in 2..5 && j in 2..5) 3.0 else -1.0
                    r += k * img[x + i, y + j]
                }
                r /= 16.0
                power += r * r
                n++
            }
            x += 4
        }
        y += 4
    }
    return if (n == 0) 0.0 else power / n
}
