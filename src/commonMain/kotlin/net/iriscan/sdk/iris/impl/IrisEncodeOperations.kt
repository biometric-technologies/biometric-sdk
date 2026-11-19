package net.iriscan.sdk.iris.impl

import net.iriscan.sdk.iris.IrisEncodeProperties
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin

/**
 * Encodes an unwrapped iris into a phase code with a validity mask.
 *
 * 1. The texture is averaged into [IrisEncodeProperties.templateRows] radial bands.
 * 2. Each band (a periodic signal around the eye) is filtered with a 1D log-Gabor
 *    filter in the frequency domain; the filter has no DC response, so the code
 *    does not depend on brightness or contrast.
 * 3. The phase of the response is quantized to two bits at each of
 *    [IrisEncodeProperties.templateCols] angular positions.
 * 4. Bits from occluded areas and fragile bits (weakest responses) are masked.
 */
internal fun encodeNormalized(n: NormalizedIris, props: IrisEncodeProperties): IrisTemplate {
    val tRows = props.templateRows
    val tCols = props.templateCols
    val cols = n.cols
    require(cols >= tCols && cols % tCols == 0) { "Texture width must be a multiple of templateCols" }
    require(cols and (cols - 1) == 0) { "Texture width must be a power of two" }
    val colStep = cols / tCols
    val filter = logGabor(cols, props.wavelength, props.sigmaOnF)
    val halfWindow = max(1, (props.wavelength / 2).toInt())

    val re = FloatArray(tRows * tCols)
    val im = FloatArray(tRows * tCols)
    val cellValid = BooleanArray(tRows * tCols)

    val sig = DoubleArray(cols)
    val ok = BooleanArray(cols)
    val fr = DoubleArray(cols)
    val fi = DoubleArray(cols)
    for (t in 0 until tRows) {
        val r0 = t * n.rows / tRows
        val r1 = max(r0 + 1, (t + 1) * n.rows / tRows)
        var sum = 0.0
        var cnt = 0
        for (c in 0 until cols) {
            var s = 0.0
            var k = 0
            for (r in r0 until r1) {
                val i = r * cols + c
                if (n.valid[i]) {
                    s += n.values[i]; k++
                }
            }
            ok[c] = k * 2 >= (r1 - r0)
            sig[c] = if (k > 0) s / k else 0.0
            if (ok[c]) {
                sum += sig[c]; cnt++
            }
        }
        if (cnt < cols / 4) continue // band mostly occluded: all bits stay masked
        val mean = sum / cnt
        for (c in 0 until cols) {
            fr[c] = if (ok[c]) sig[c] - mean else 0.0
            fi[c] = 0.0
        }
        fft(fr, fi, inverse = false)
        for (k in 0 until cols) {
            fr[k] *= filter[k]; fi[k] *= filter[k]
        }
        fft(fr, fi, inverse = true)
        for (tc in 0 until tCols) {
            val c = tc * colStep
            // a cell is valid when most of the filter footprint saw real iris
            var good = 0
            for (d in -halfWindow..halfWindow) if (ok[(c + d + cols) % cols]) good++
            val idx = t * tCols + tc
            cellValid[idx] = ok[c] && good * 4 >= (2 * halfWindow + 1) * 3
            re[idx] = fr[c].toFloat()
            im[idx] = fi[c].toFloat()
        }
    }

    // fragile bits: weakest responses of each bit plane among valid cells
    val reThr = fragileThreshold(re, cellValid, props.fragileBitFraction)
    val imThr = fragileThreshold(im, cellValid, props.fragileBitFraction)

    val wordsPerRow = tCols * 2 / 64
    val code = LongArray(tRows * wordsPerRow)
    val mask = LongArray(tRows * wordsPerRow)
    for (t in 0 until tRows) for (tc in 0 until tCols) {
        val idx = t * tCols + tc
        val bitRe = tc * 2
        val bitIm = tc * 2 + 1
        val base = t * wordsPerRow
        if (re[idx] >= 0f) code[base + bitRe / 64] = code[base + bitRe / 64] or (1L shl (bitRe % 64))
        if (im[idx] >= 0f) code[base + bitIm / 64] = code[base + bitIm / 64] or (1L shl (bitIm % 64))
        if (!cellValid[idx]) continue
        if (abs(re[idx]) >= reThr) mask[base + bitRe / 64] = mask[base + bitRe / 64] or (1L shl (bitRe % 64))
        if (abs(im[idx]) >= imThr) mask[base + bitIm / 64] = mask[base + bitIm / 64] or (1L shl (bitIm % 64))
    }
    return IrisTemplate(tRows, tCols, code, mask)
}

private fun fragileThreshold(v: FloatArray, valid: BooleanArray, fraction: Double): Float {
    if (fraction <= 0.0) return 0f
    val mags = v.filterIndexed { i, _ -> valid[i] }.map { abs(it) }.toFloatArray()
    if (mags.isEmpty()) return 0f
    return quantile(mags, fraction)
}

/** One-sided log-Gabor transfer function for an N-point FFT (positive frequencies only). */
internal fun logGabor(n: Int, wavelength: Double, sigmaOnF: Double): DoubleArray {
    val f0 = 1.0 / wavelength
    val denom = 2 * ln(sigmaOnF) * ln(sigmaOnF)
    return DoubleArray(n) { k ->
        if (k == 0 || k >= n / 2) 0.0 else {
            val f = k.toDouble() / n
            val l = ln(f / f0)
            exp(-(l * l) / denom)
        }
    }
}

/** In-place iterative radix-2 complex FFT. Inverse transform is scaled by 1/N. */
internal fun fft(re: DoubleArray, im: DoubleArray, inverse: Boolean) {
    val n = re.size
    require(n == im.size && n and (n - 1) == 0)
    var j = 0
    for (i in 1 until n) {
        var bit = n shr 1
        while (j and bit != 0) {
            j = j xor bit; bit = bit shr 1
        }
        j = j xor bit
        if (i < j) {
            var t = re[i]; re[i] = re[j]; re[j] = t
            t = im[i]; im[i] = im[j]; im[j] = t
        }
    }
    var len = 2
    while (len <= n) {
        val ang = 2 * PI / len * (if (inverse) 1 else -1)
        val wr = cos(ang)
        val wi = sin(ang)
        var i = 0
        while (i < n) {
            var cr = 1.0
            var ci = 0.0
            for (k in 0 until len / 2) {
                val ur = re[i + k]
                val ui = im[i + k]
                val vr = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                val vi = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                re[i + k] = ur + vr; im[i + k] = ui + vi
                re[i + k + len / 2] = ur - vr; im[i + k + len / 2] = ui - vi
                val ncr = cr * wr - ci * wi
                ci = cr * wi + ci * wr
                cr = ncr
            }
            i += len
        }
        len = len shl 1
    }
    if (inverse) for (i in 0 until n) {
        re[i] /= n.toDouble(); im[i] /= n.toDouble()
    }
}
