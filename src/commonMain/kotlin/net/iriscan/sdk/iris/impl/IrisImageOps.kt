package net.iriscan.sdk.iris.impl

import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.image.ImageColorType
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Floating point grayscale image used internally by the iris pipeline.
 * Values are in the 0..255 range.
 */
internal class GrayImage(val width: Int, val height: Int, val data: FloatArray) {

    operator fun get(x: Int, y: Int): Float = data[y * width + x]

    operator fun set(x: Int, y: Int, v: Float) {
        data[y * width + x] = v
    }

    fun inBounds(x: Double, y: Double): Boolean =
        x >= 0.0 && y >= 0.0 && x <= width - 1.0 && y <= height - 1.0

    /** Bilinear sample, or NaN when the point is outside the image. */
    fun bilinear(x: Double, y: Double): Float {
        if (!inBounds(x, y)) return Float.NaN
        val x0 = min(floor(x).toInt(), width - 2).coerceAtLeast(0)
        val y0 = min(floor(y).toInt(), height - 2).coerceAtLeast(0)
        val fx = (x - x0).toFloat()
        val fy = (y - y0).toFloat()
        val x1 = min(x0 + 1, width - 1)
        val y1 = min(y0 + 1, height - 1)
        val top = this[x0, y0] * (1 - fx) + this[x1, y0] * fx
        val bottom = this[x0, y1] * (1 - fx) + this[x1, y1] * fx
        return top * (1 - fy) + bottom * fy
    }
}

/** Converts an SDK image to grayscale without modifying the input. */
internal fun toGray(image: Image): GrayImage {
    val out = FloatArray(image.width * image.height)
    for (i in out.indices) {
        val c = image.colors[i]
        out[i] = when (image.colorType) {
            ImageColorType.GRAY -> c.coerceIn(0, 255).toFloat()
            ImageColorType.BINARY -> if (c != 0) 255f else 0f
            ImageColorType.RGB -> {
                val r = (c ushr 16) and 0xFF
                val g = (c ushr 8) and 0xFF
                val b = c and 0xFF
                // ITU-R BT.601 luma
                0.299f * r + 0.587f * g + 0.114f * b
            }
        }
    }
    return GrayImage(image.width, image.height, out)
}

/** Area-averaging downscale by [factor] (0 < factor <= 1). */
internal fun downscale(img: GrayImage, factor: Double): GrayImage {
    if (factor >= 1.0) return GrayImage(img.width, img.height, img.data.copyOf())
    val w = max(1, (img.width * factor).roundToInt())
    val h = max(1, (img.height * factor).roundToInt())
    val sx = img.width.toDouble() / w
    val sy = img.height.toDouble() / h
    val out = FloatArray(w * h)
    for (y in 0 until h) {
        val ys = floor(y * sy).toInt()
        val ye = min(img.height, max(ys + 1, ceil((y + 1) * sy).toInt()))
        for (x in 0 until w) {
            val xs = floor(x * sx).toInt()
            val xe = min(img.width, max(xs + 1, ceil((x + 1) * sx).toInt()))
            var sum = 0f
            for (yy in ys until ye) for (xx in xs until xe) sum += img[xx, yy]
            out[y * w + x] = sum / ((ye - ys) * (xe - xs))
        }
    }
    return GrayImage(w, h, out)
}

/** Separable Gaussian blur with clamped borders. */
internal fun gaussianBlur(img: GrayImage, sigma: Double): GrayImage {
    if (sigma <= 0.0) return GrayImage(img.width, img.height, img.data.copyOf())
    val radius = max(1, ceil(3 * sigma).toInt())
    val kernel = FloatArray(2 * radius + 1) { exp(-((it - radius) * (it - radius)) / (2 * sigma * sigma)).toFloat() }
    val ks = kernel.sum()
    for (i in kernel.indices) kernel[i] /= ks
    val w = img.width
    val h = img.height
    val tmp = FloatArray(w * h)
    for (y in 0 until h) for (x in 0 until w) {
        var s = 0f
        for (k in -radius..radius) s += kernel[k + radius] * img[(x + k).coerceIn(0, w - 1), y]
        tmp[y * w + x] = s
    }
    val out = FloatArray(w * h)
    for (y in 0 until h) for (x in 0 until w) {
        var s = 0f
        for (k in -radius..radius) s += kernel[k + radius] * tmp[(y + k).coerceIn(0, h - 1) * w + x]
        out[y * w + x] = s
    }
    return GrayImage(w, h, out)
}

/** Value at quantile q (0..1) of the given values. */
internal fun quantile(values: FloatArray, q: Double): Float {
    if (values.isEmpty()) return Float.NaN
    val sorted = values.copyOf()
    sorted.sort()
    val idx = ((sorted.size - 1) * q).roundToInt().coerceIn(0, sorted.size - 1)
    return sorted[idx]
}

/**
 * Marks specular reflections: very bright pixels, dilated by [dilate] pixels.
 * Returns true for reflection pixels.
 */
internal fun reflectionMask(img: GrayImage, dilate: Int): BooleanArray {
    // Saturated pixels, plus small spots much brighter than their surroundings
    // (illuminator glints inside the dark pupil are often well below saturation).
    val threshold = max(230f, quantile(img.data, 0.995))
    val w = img.width
    val h = img.height
    // Glints: saturated pixels, and spots clearly brighter than their surroundings
    // (illuminator glints inside the dark pupil are often well below saturation and
    // have halos). Only compact spots count, so a bright band of sclera along the
    // limbus or bright iris texture is not removed.
    val local = gaussianBlur(img, max(3.0, min(w, h) / 60.0))
    val candidate = BooleanArray(w * h) { img.data[it] > 170f && img.data[it] - local.data[it] > 40f }
    val seed = keepCompactComponents(candidate, w, h, maxExtent = max(6, min(w, h) / 20))
    for (i in seed.indices) if (img.data[i] >= threshold) seed[i] = true
    if (dilate <= 0) return seed
    val out = BooleanArray(w * h)
    for (y in 0 until h) for (x in 0 until w) {
        if (!seed[y * w + x]) continue
        for (dy in -dilate..dilate) for (dx in -dilate..dilate) {
            val xx = x + dx
            val yy = y + dy
            if (xx in 0 until w && yy in 0 until h) out[yy * w + xx] = true
        }
    }
    return out
}

/**
 * Fills masked pixels from their unmasked neighbours, layer by layer, so that
 * reflections do not create false edges for the boundary search. A low quantile
 * of the neighbourhood is used: glints are bright and sit on darker tissue, and
 * their halo should not spread into the fill.
 */
internal fun inpaint(img: GrayImage, mask: BooleanArray): GrayImage {
    val w = img.width
    val h = img.height
    val data = img.data.copyOf()
    val pending = mask.copyOf()
    var remaining = pending.count { it }
    var iterations = 0
    val buf = FloatArray(49)
    while (remaining > 0 && iterations < 60) {
        iterations++
        val filled = ArrayList<Int>()
        val values = ArrayList<Float>()
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if (!pending[i]) continue
            var n = 0
            for (dy in -3..3) for (dx in -3..3) {
                val xx = x + dx
                val yy = y + dy
                if (xx in 0 until w && yy in 0 until h && !pending[yy * w + xx]) buf[n++] = data[yy * w + xx]
            }
            if (n >= 3) {
                // insertion sort of at most 49 values (common code, no platform sort for ranges)
                for (a in 1 until n) {
                    val v = buf[a]
                    var b = a - 1
                    while (b >= 0 && buf[b] > v) {
                        buf[b + 1] = buf[b]; b--
                    }
                    buf[b + 1] = v
                }
                filled += i; values += buf[n / 4]
            }
        }
        if (filled.isEmpty()) break
        for (k in filled.indices) {
            data[filled[k]] = values[k]; pending[filled[k]] = false
        }
        remaining -= filled.size
    }
    if (remaining > 0) {
        val mean = data.filterIndexed { i, _ -> !pending[i] }.average().toFloat().takeIf { !it.isNaN() } ?: 128f
        for (i in data.indices) if (pending[i]) data[i] = mean
    }
    return GrayImage(w, h, data)
}

/** Minimum over a (2r+1) x (2r+1) window (separable). */
internal fun minFilter(img: GrayImage, r: Int): GrayImage {
    val w = img.width
    val h = img.height
    val tmp = FloatArray(w * h)
    for (y in 0 until h) for (x in 0 until w) {
        var m = Float.MAX_VALUE
        for (k in max(0, x - r)..min(w - 1, x + r)) m = min(m, img.data[y * w + k])
        tmp[y * w + x] = m
    }
    val out = FloatArray(w * h)
    for (y in 0 until h) for (x in 0 until w) {
        var m = Float.MAX_VALUE
        for (k in max(0, y - r)..min(h - 1, y + r)) m = min(m, tmp[k * w + x])
        out[y * w + x] = m
    }
    return GrayImage(w, h, out)
}

/** Clears connected components (8-connected) whose bounding box is larger than [maxExtent]. */
internal fun keepCompactComponents(mask: BooleanArray, w: Int, h: Int, maxExtent: Int): BooleanArray {
    val out = BooleanArray(mask.size)
    val seen = BooleanArray(mask.size)
    val queue = IntArray(mask.size)
    val members = IntArray(mask.size)
    for (start in mask.indices) {
        if (!mask[start] || seen[start]) continue
        var head = 0
        var tail = 0
        var count = 0
        queue[tail++] = start
        seen[start] = true
        var x0 = w; var x1 = -1; var y0 = h; var y1 = -1
        while (head < tail) {
            val i = queue[head++]
            members[count++] = i
            val x = i % w
            val y = i / w
            if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y
            for (dy in -1..1) for (dx in -1..1) {
                val xx = x + dx
                val yy = y + dy
                if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue
                val j = yy * w + xx
                if (mask[j] && !seen[j]) {
                    seen[j] = true; queue[tail++] = j
                }
            }
        }
        if (x1 - x0 < maxExtent && y1 - y0 < maxExtent) for (k in 0 until count) out[members[k]] = true
    }
    return out
}
