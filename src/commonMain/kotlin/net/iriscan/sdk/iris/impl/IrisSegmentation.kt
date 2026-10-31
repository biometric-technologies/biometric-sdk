package net.iriscan.sdk.iris.impl

import net.iriscan.sdk.iris.IrisExtractProperties
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal data class IrisCircle(val x: Double, val y: Double, val r: Double)

/** Eyelid boundary as a parabola y = a + b * x + c * x^2 in image coordinates. */
internal class Eyelid(val a: Double, val b: Double, val c: Double) {
    fun y(x: Double): Double = a + b * x + c * x * x

    fun scaled(scale: Double): Eyelid {
        // image coordinates: X = (x + 0.5) / s - 0.5  =>  x = s * (X + 0.5) - 0.5
        val k = 0.5 * scale - 0.5
        // y(x) in small coordinates, Y = (y + 0.5) / s - 0.5
        val a2 = a + b * k + c * k * k
        val b2 = (b + 2 * c * k) * scale
        val c2 = c * scale * scale
        return Eyelid((a2 + 0.5) / scale - 0.5, b2 / scale, c2 / scale)
    }
}

internal class IrisSegmentation(
    val pupil: IrisCircle,
    val iris: IrisCircle,
    /** Upper eyelid boundary, or null when the iris is not covered from above. */
    val upperEyelid: Eyelid?,
    /** Lower eyelid boundary, or null when the iris is not covered from below. */
    val lowerEyelid: Eyelid?,
    /** Strength of the pupil boundary (gray levels per pixel). */
    val pupilEdge: Double,
    /** Strength of the limbus boundary (gray levels per pixel). */
    val irisEdge: Double,
)

/** Sample directions on a circle, grouped into angular sectors. */
private class Ring(val cos: DoubleArray, val sin: DoubleArray, val sector: IntArray, val sectors: Int)

private fun ring(ranges: List<ClosedRange<Double>>, stepDeg: Double, sectorsPerRange: Int): Ring {
    val angles = mutableListOf<Double>()
    val sector = mutableListOf<Int>()
    ranges.forEachIndexed { ri, s ->
        val span = s.endInclusive - s.start
        var a = s.start
        while (a <= s.endInclusive + 1e-9) {
            angles += a * PI / 180.0
            val k = if (span <= 0) 0 else min(sectorsPerRange - 1, ((a - s.start) / span * sectorsPerRange).toInt())
            sector += ri * sectorsPerRange + k
            a += stepDeg
        }
    }
    return Ring(
        DoubleArray(angles.size) { cos(angles[it]) }, DoubleArray(angles.size) { sin(angles[it]) },
        sector.toIntArray(), ranges.size * sectorsPerRange,
    )
}

/** Score factor of a candidate that is dark only along a line (spectacle frame, eyelashes). */
private const val SURROUND_FLOOR = 0.25

/** Score factor of a candidate with an illuminator glint inside. */
private const val GLINT_BONUS = 1.3

/** Pupil boundary must be present in at least ~5 of 8 sectors (tolerates eyelids and glints). */
private const val PUPIL_SECTOR_QUANTILE = 0.375

/** Limbus edge strength: the weaker of the left and right halves (4 sectors). */
private const val LIMBUS_SECTOR_QUANTILE = 0.34

/** The limbus is the first strong edge outside the pupil; eyelids and eye corners lie further out. */
private const val LIMBUS_PREFER_INNER = 0.9

private val FULL_RING = ring(listOf(0.0..354.375), 5.625, 8) // 64 samples, 8 sectors

/** Mean intensity on a circle, or NaN when fewer than half of the samples are inside the image. */
private fun circleMean(img: GrayImage, cx: Double, cy: Double, r: Double, ring: Ring): Double {
    var s = 0.0
    var n = 0
    for (i in ring.cos.indices) {
        val v = img.bilinear(cx + r * ring.cos[i], cy + r * ring.sin[i])
        if (!v.isNaN()) {
            s += v; n++
        }
    }
    return if (n * 2 < ring.cos.size) Double.NaN else s / n
}

/** Mean intensity of each sector of a circle; NaN for sectors mostly outside the image. */
private fun sectorMeans(img: GrayImage, cx: Double, cy: Double, r: Double, ring: Ring, out: DoubleArray) {
    val sum = DoubleArray(ring.sectors)
    val cnt = IntArray(ring.sectors)
    val tot = IntArray(ring.sectors)
    for (i in ring.cos.indices) {
        val k = ring.sector[i]
        tot[k]++
        val v = img.bilinear(cx + r * ring.cos[i], cy + r * ring.sin[i])
        if (!v.isNaN()) {
            sum[k] += v.toDouble(); cnt[k]++
        }
    }
    for (k in 0 until ring.sectors) out[k] = if (cnt[k] * 2 < tot[k]) Double.NaN else sum[k] / cnt[k]
}

private class EdgeHit(val r: Double, val strength: Double)

/**
 * Daugman's integro-differential operator along the radius: the radius where the
 * smoothed derivative of the circular mean (dark inside, bright outside) is maximal.
 *
 * The derivative is computed per angular sector and the sectors are combined with
 * a low quantile ([sectorQuantile]), so that a boundary has to be present around
 * most of the circle. This rejects circles that only touch a strong partial edge
 * such as an eyelid or a cluster of eyelashes. A null quantile uses the mean.
 */
private fun strongestEdge(
    img: GrayImage, cx: Double, cy: Double, rMin: Double, rMax: Double, rStep: Double, ring: Ring,
    sectorQuantile: Double? = null,
    preferInner: Double? = null,
    minSectorFraction: Double = 0.5,
    radialScale: Int = 1,
): EdgeHit? {
    if (rMax - rMin < 3 * rStep) return null
    val profile = ArrayList<EdgeHit>()
    val k0 = radialScale
    val n = ((rMax - rMin) / rStep).toInt() + 1 + 2 * k0
    val sk = ring.sectors
    val m = Array(n) { DoubleArray(sk) }
    for (i in 0 until n) sectorMeans(img, cx, cy, rMin + (i - k0) * rStep, ring, m[i])
    // per-sector derivative
    val d = Array(n) { DoubleArray(sk) { Double.NaN } }
    // derivative over +-k0 samples: a small scale finds sharp edges (pupil), a larger
    // one measures the total step of a blurred edge (limbus) rather than iris texture
    for (i in k0 until n - k0) for (k in 0 until sk) {
        val a = m[i - k0][k]
        val b = m[i + k0][k]
        if (!a.isNaN() && !b.isNaN()) d[i][k] = (b - a) / (2 * k0 * rStep)
    }
    var best: EdgeHit? = null
    val buf = DoubleArray(sk)
    for (i in k0 + 1 until n - k0 - 1) {
        var cnt = 0
        for (k in 0 until sk) {
            val a = d[i - 1][k]
            val b = d[i][k]
            val c = d[i + 1][k]
            if (a.isNaN() || b.isNaN() || c.isNaN()) continue
            buf[cnt++] = (a + 2 * b + c) / 4 // [1 2 1] smoothing along the radius
        }
        if (cnt < sk * minSectorFraction) continue
        val v = if (sectorQuantile == null) {
            var s = 0.0
            for (k in 0 until cnt) s += buf[k]
            s / cnt
        } else {
            val sorted = buf.copyOf(cnt)
            sorted.sort()
            sorted[((cnt - 1) * sectorQuantile).toInt()]
        }
        val hit = EdgeHit(rMin + (i - k0) * rStep, v)
        profile += hit
        if (best == null || v > best.strength) best = hit
    }
    val top = best?.takeIf { it.strength > 0 } ?: return null
    if (preferInner == null) return top
    // the innermost local maximum that is nearly as strong as the strongest edge
    for (k in 1 until profile.size - 1) {
        val h = profile[k]
        if (h.strength >= preferInner * top.strength &&
            h.strength >= profile[k - 1].strength && h.strength >= profile[k + 1].strength) return h
    }
    return top
}

/**
 * A ring of illuminator glints inside the pupil can produce a circular edge
 * smaller than the pupil. If a larger concentric edge exists and the ring
 * between the two is as dark as the pupil, the larger circle is the pupil.
 */
private fun expandPupil(img: GrayImage, found: PupilHit, rpMax: Double): PupilHit {
    var current = found
    repeat(2) {
        val c = current.c
        val inner = discMean(img, c.x, c.y, c.r)
        var best: PupilHit? = null
        for (dy in -3..3) for (dx in -3..3) {
            val cx = c.x + dx
            val cy = c.y + dy
            val e = strongestEdge(img, cx, cy, c.r * 1.15, min(rpMax, c.r * 2.2), 1.0, FULL_RING, PUPIL_SECTOR_QUANTILE) ?: continue
            if (e.strength < 0.5 * current.edge) continue
            // the annulus between the two circles must look like pupil, not iris
            // glints sit somewhere between the two circles: use the darkest intermediate ring
            val ring = listOf(0.3, 0.5, 0.7, 0.85).map { circleMean(img, cx, cy, c.r + (e.r - c.r) * it, FULL_RING) }
                .filter { !it.isNaN() }.minOrNull() ?: continue
            val outside = circleMean(img, cx, cy, e.r + 4, FULL_RING)
            if (outside.isNaN()) continue
            if (ring - inner > 0.5 * (outside - inner)) continue
            if (best == null || e.strength > best.edge) best = PupilHit(IrisCircle(cx, cy, e.r), e.strength, e.strength)
        }
        current = best ?: return current
    }
    return current
}

/** Mean intensity of a disc, approximated by three concentric circles. */
private fun discMean(img: GrayImage, cx: Double, cy: Double, r: Double): Double {
    val a = circleMean(img, cx, cy, r * 0.3, FULL_RING)
    val b = circleMean(img, cx, cy, r * 0.6, FULL_RING)
    val c = circleMean(img, cx, cy, r * 0.85, FULL_RING)
    val v = listOf(a, b, c).filter { !it.isNaN() }
    return if (v.isEmpty()) Double.NaN else v.average()
}

private class PupilHit(val c: IrisCircle, val edge: Double, val score: Double)

private fun pupilAt(
    img: GrayImage, glints: BooleanArray?, cx: Double, cy: Double, rMin: Double, rMax: Double, rStep: Double,
): PupilHit? {
    val e = strongestEdge(img, cx, cy, rMin, rMax, rStep, FULL_RING, sectorQuantile = PUPIL_SECTOR_QUANTILE) ?: return null
    val inner = discMean(img, cx, cy, e.r)
    if (inner.isNaN()) return null
    // prefer a dark interior: separates the pupil from the limbus and from eyelash clusters
    val score = e.strength * (255.0 - inner).coerceAtLeast(0.0) / 255.0 *
            surroundFactor(img, cx, cy, e.r) * glintFactor(glints, img.width, img.height, cx, cy, e.r)
    return PupilHit(IrisCircle(cx, cy, e.r), e.strength, score)
}

/**
 * The pupil is darker than its surroundings on every side. A spectacle frame or an
 * eyelash band is dark along a line, so the surroundings are dark on two opposite
 * sides. One side may be covered by an eyelid, so the second weakest of the eight
 * sector contrasts is compared with the median one.
 */
private fun surroundFactor(img: GrayImage, cx: Double, cy: Double, r: Double): Double {
    val sk = FULL_RING.sectors
    val inside = DoubleArray(sk)
    val outside = DoubleArray(sk)
    sectorMeans(img, cx, cy, 0.5 * r, FULL_RING, inside)
    sectorMeans(img, cx, cy, 1.4 * r, FULL_RING, outside)
    val c = ArrayList<Double>(sk)
    for (k in 0 until sk) if (!inside[k].isNaN() && !outside[k].isNaN()) c += outside[k] - inside[k]
    if (c.size < 6) return SURROUND_FLOOR
    c.sort()
    val med = c[c.size / 2]
    if (med <= 0) return SURROUND_FLOOR
    return SURROUND_FLOOR + (1 - SURROUND_FLOOR) * (c[1] / med).coerceIn(0.0, 1.0)
}

/**
 * Near-infrared illuminators leave small specular glints, almost always inside the
 * pupil or on its border. A candidate with a small glint inside gets a bonus; large
 * reflections (spectacle lenses) do not count.
 */
private fun glintFactor(glints: BooleanArray?, w: Int, h: Int, cx: Double, cy: Double, r: Double): Double {
    if (glints == null) return 1.0
    val rr = 1.1 * r
    var n = 0
    var tot = 0
    val y0 = max(0, (cy - rr).toInt())
    val y1 = min(h - 1, (cy + rr).toInt())
    val x0 = max(0, (cx - rr).toInt())
    val x1 = min(w - 1, (cx + rr).toInt())
    for (y in y0..y1) for (x in x0..x1) {
        if (hypot(x - cx, y - cy) > rr) continue
        tot++
        if (glints[y * w + x]) n++
    }
    if (tot == 0 || n == 0) return 1.0
    return if (n.toDouble() / tot <= 0.3) GLINT_BONUS else 1.0
}

/**
 * Locates pupil and iris circles. Returns null when no plausible pair is found.
 */
internal fun segmentIris(full: GrayImage, props: IrisExtractProperties): IrisSegmentation? {
    val minSideFull = min(full.width, full.height).toDouble()
    val scale = min(1.0, props.workingSize / minSideFull)
    val small = downscale(full, scale)
    val refl = reflectionMask(small, dilate = 3)
    val glints = smallReflections(refl, small.width, small.height, maxArea = (0.004 * small.width * small.height).toInt())
    val clean = inpaint(small, refl)
    val img = gaussianBlur(clean, 1.2)
    val minSide = min(img.width, img.height).toDouble()

    val rpMin = max(3.0, (props.minPupilRadius?.times(scale)) ?: (0.03 * minSide))
    val rpMax = max(rpMin + 4, (props.maxPupilRadius?.times(scale)) ?: (0.22 * minSide))
    val riMinAbs = (props.minIrisRadius?.times(scale)) ?: (0.12 * minSide)
    val riMaxAbs = (props.maxIrisRadius?.times(scale)) ?: (0.60 * minSide)

    // 1. pupil candidates: dark blobs. Each blob is grown from a dark seed and
    //    represented by its centroid and equivalent radius, so the search starts
    //    near the true center even when the darkest pixel is off-center.
    val dark = gaussianBlur(clean, 2.0)
    val candidates = darkBlobs(dark, rpMin, rpMax, maxBlobs = 12)
    if (candidates.isEmpty()) return null

    // 2. coarse pupil search around each blob
    var bestPupil: PupilHit? = null
    for (blob in candidates) {
        val reach = max(3, (0.3 * blob.r).toInt())
        val rLo = max(rpMin, 0.5 * blob.r)
        val rHi = min(rpMax, max(rLo + 4, 1.6 * blob.r))
        for (dy in -reach..reach step 2) for (dx in -reach..reach step 2) {
            val hit = pupilAt(img, glints, blob.x + dx, blob.y + dy, rLo, rHi, 1.0) ?: continue
            if (bestPupil == null || hit.score > bestPupil.score) bestPupil = hit
        }
    }
    var pupil = bestPupil ?: return null
    // 3. refine
    for (dy in -4..4) for (dx in -4..4) {
        val cx = pupil.c.x + dx * 0.5
        val cy = pupil.c.y + dy * 0.5
        val hit = pupilAt(img, glints, cx, cy, max(rpMin, pupil.c.r - 4), min(rpMax, pupil.c.r + 4), 0.5) ?: continue
        if (hit.score > pupil.score) pupil = hit
    }
    pupil = expandPupil(img, pupil, rpMax)
    val p = pupil.c

    // 4. limbus, in two stages: first the innermost strong edge around the pupil
    //    center, then the center and radius refined near it. Searching all centers
    //    at once lets eyelid and eye-corner edges win.
    // both sides must show the limbus: corners of the eye and eyelid edges usually appear on one side only
    val irisRing = ring(props.irisAnglesToSearch.toList(), 2.5, 2)
    val riMin = max(riMinAbs, p.r / props.maxPupilIrisRatio)
    val riMax = min(riMaxAbs, p.r / props.minPupilIrisRatio)
    if (riMax - riMin < 4) return null
    var bestIris: EdgeHit? = null
    var bestCenter = p.x to p.y
    var quantile = LIMBUS_SECTOR_QUANTILE
    // if eyelids hide two of the four lateral sectors, accept the edge seen in the other two
    for (q in listOf(LIMBUS_SECTOR_QUANTILE, 0.67)) {
        for (dy in -2..2) for (dx in -2..2) {
            val cx = p.x + dx
            val cy = p.y + dy
            val lo = max(riMin, p.r + hypot(dx.toDouble(), dy.toDouble()) + 3)
            val hit = strongestEdge(img, cx, cy, lo, riMax, 1.0, irisRing, q, LIMBUS_PREFER_INNER, 0.75, 6) ?: continue
            if (bestIris == null || hit.r < bestIris.r - 2 || (kotlin.math.abs(hit.r - bestIris.r) <= 2 && hit.strength > bestIris.strength)) {
                bestIris = hit; bestCenter = cx to cy
            }
        }
        if (bestIris != null) {
            quantile = q; break
        }
    }
    var iris = bestIris ?: return null
    // lateral arcs fix the horizontal position well but the vertical one only weakly,
    // so the vertical search range is kept small
    val off = max(2, (0.12 * iris.r).toInt())
    val offY = max(2, (0.04 * iris.r).toInt())
    val r0 = iris.r
    for (dy in -offY..offY) for (dx in -off..off) {
        val cx = p.x + dx
        val cy = p.y + dy
        val d = hypot(dx.toDouble(), dy.toDouble())
        val lo = max(max(riMin, r0 * 0.9), p.r + d + 3)
        val hit = strongestEdge(img, cx, cy, lo, min(riMax, r0 * 1.1), 0.5, irisRing, quantile, null, 0.75, 3) ?: continue
        if (hit.strength > iris.strength) {
            iris = hit; bestCenter = cx to cy
        }
    }
    val irisCircle = IrisCircle(bestCenter.first, bestCenter.second, iris.r)
    if (hypot(irisCircle.x - p.x, irisCircle.y - p.y) + p.r >= irisCircle.r) return null

    // 5. eyelids
    val upper = if (props.detectEyelids) eyelid(img, p, irisCircle, upper = true) else null
    val lower = if (props.detectEyelids) eyelid(img, p, irisCircle, upper = false) else null

    // back to input image coordinates
    fun up(v: Double) = (v + 0.5) / scale - 0.5
    return IrisSegmentation(
        pupil = IrisCircle(up(p.x), up(p.y), p.r / scale),
        iris = IrisCircle(up(irisCircle.x), up(irisCircle.y), irisCircle.r / scale),
        upperEyelid = upper?.scaled(scale),
        lowerEyelid = lower?.scaled(scale),
        pupilEdge = pupil.edge,
        irisEdge = iris.strength,
    )
}

/** Median and robust spread of iris intensity in the lateral sectors, which eyelids rarely cover. */
private fun irisReference(img: GrayImage, p: IrisCircle, iris: IrisCircle): Pair<Double, Double>? {
    val v = ArrayList<Float>()
    for (deg in listOf(-35, -25, -15, -5, 5, 15, 25, 35, 145, 155, 165, 175, 185, 195, 205, 215)) {
        val a = deg * PI / 180
        for (k in 2..8) {
            val rho = k / 10.0
            val x = (1 - rho) * (p.x + p.r * cos(a)) + rho * (iris.x + iris.r * cos(a))
            val y = (1 - rho) * (p.y + p.r * sin(a)) + rho * (iris.y + iris.r * sin(a))
            val s = img.bilinear(x, y)
            if (!s.isNaN()) v += s
        }
    }
    if (v.size < 16) return null
    val arr = v.toFloatArray()
    val med = quantile(arr, 0.5)
    val mad = quantile(FloatArray(arr.size) { kotlin.math.abs(arr[it] - med) }, 0.5)
    return med.toDouble() to max(2.0, 1.4826 * mad)
}

/** Longest to shortest axis ratio of a dark patch that can still be a pupil. */
private const val MAX_BLOB_ELONGATION = 3.0

/** Reflection mask without its large components (spectacle lenses), i.e. the illuminator glints. */
private fun smallReflections(mask: BooleanArray, w: Int, h: Int, maxArea: Int): BooleanArray {
    val out = BooleanArray(mask.size)
    val seen = BooleanArray(mask.size)
    val queue = IntArray(mask.size)
    for (s in mask.indices) {
        if (!mask[s] || seen[s]) continue
        var head = 0
        var tail = 0
        queue[tail++] = s
        seen[s] = true
        while (head < tail) {
            val i = queue[head++]
            val x = i % w
            val y = i / w
            for ((nx, ny) in arrayOf(x - 1 to y, x + 1 to y, x to y - 1, x to y + 1)) {
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                val j = ny * w + nx
                if (mask[j] && !seen[j]) {
                    seen[j] = true
                    queue[tail++] = j
                }
            }
        }
        if (tail <= maxArea) for (k in 0 until tail) out[queue[k]] = true
    }
    return out
}

/**
 * Dark connected regions, grown from the darkest seeds. Returns up to [maxBlobs]
 * blobs with plausible pupil size as circles (centroid, equivalent radius).
 */
private fun darkBlobs(img: GrayImage, rMin: Double, rMax: Double, maxBlobs: Int): List<IrisCircle> {
    val w = img.width
    val h = img.height
    val median = quantile(img.data, 0.5)
    val order = (0 until w * h).filter { it % w % 2 == 0 && it / w % 2 == 0 }.sortedBy { img.data[it] }
    val visited = BooleanArray(w * h)
    val maxArea = (PI * rMax * rMax * 2).toInt()
    val minArea = (PI * rMin * rMin * 0.3).toInt()
    val out = mutableListOf<IrisCircle>()
    val queue = IntArray(w * h)
    for (seed in order) {
        if (out.size >= maxBlobs) break
        if (visited[seed]) continue
        val seedValue = img.data[seed]
        if (seedValue > median) break
        val limit = seedValue + max(12f, 0.3f * (median - seedValue))
        var head = 0
        var tail = 0
        queue[tail++] = seed
        visited[seed] = true
        var sx = 0.0
        var sy = 0.0
        var sxx = 0.0
        var syy = 0.0
        var sxy = 0.0
        var area = 0
        // breadth-first growth: the first maxArea pixels form a compact patch around the seed and
        // give the candidate; the rest of the region is only marked, so that one large dark region
        // (a spectacle frame, hair, an eyebrow) does not seed every candidate
        while (head < tail) {
            val i = queue[head++]
            val x = i % w
            val y = i / w
            if (area < maxArea) {
                sx += x; sy += y
                sxx += x.toDouble() * x; syy += y.toDouble() * y; sxy += x.toDouble() * y
                area++
            }
            for ((nx, ny) in arrayOf(x - 1 to y, x + 1 to y, x to y - 1, x to y + 1)) {
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                val j = ny * w + nx
                if (!visited[j] && img.data[j] <= limit) {
                    visited[j] = true
                    queue[tail++] = j
                }
            }
        }
        if (area < minArea) continue
        val mx = sx / area
        val my = sy / area
        // elongation of the patch: a pupil, even half covered by an eyelid, is not a line
        val vxx = sxx / area - mx * mx
        val vyy = syy / area - my * my
        val vxy = sxy / area - mx * my
        val tr = vxx + vyy
        val det = vxx * vyy - vxy * vxy
        val disc = sqrt(max(0.0, tr * tr / 4 - det))
        val l1 = tr / 2 + disc
        val l2 = tr / 2 - disc
        if (l2 <= 0 || l1 / l2 > MAX_BLOB_ELONGATION * MAX_BLOB_ELONGATION) continue
        val r = min(rMax, sqrt(area / PI))
        out += IrisCircle(mx, my, r)
        // the darkest point of the region, when it is away from the centroid (pupil merged with a dark iris or eyelashes)
        val seedX = (seed % w).toDouble()
        val seedY = (seed / w).toDouble()
        if (hypot(seedX - mx, seedY - my) > 0.3 * r) out += IrisCircle(seedX, seedY, r)
    }
    return out
}

/**
 * Finds an eyelid crossing the iris. The iris width is split into three vertical
 * bands; in each band the eyelid is the row with the strongest intensity step
 * between the limbus and the pupil (in near-infrared images eyelid skin is
 * brighter than the iris). Three points give a parabola, one central point a
 * horizontal line. Returns null when no clear step is found.
 */
private fun eyelid(img: GrayImage, p: IrisCircle, iris: IrisCircle, upper: Boolean): Eyelid? {
    val ref = irisReference(img, p, iris) ?: return null
    val half = 0.75 * iris.r
    val bandW = 2 * half / 3
    val points = arrayOfNulls<Pair<Double, Double>>(3)
    for (b in 0 until 3) {
        val x0 = iris.x - half + b * bandW
        val y = eyelidInBand(img, p, iris, x0.toInt(), (x0 + bandW).toInt(), upper, ref) ?: continue
        points[b] = (x0 + bandW / 2) to y
    }
    val (l, c, r) = points
    if (l != null && c != null && r != null) {
        // parabola through three points
        val (x1, y1) = l
        val (x2, y2) = c
        val (x3, y3) = r
        val d = (x1 - x2) * (x1 - x3) * (x2 - x3)
        val cc = (x3 * (y2 - y1) + x2 * (y1 - y3) + x1 * (y3 - y2)) / d
        val bb = (x3 * x3 * (y1 - y2) + x2 * x2 * (y3 - y1) + x1 * x1 * (y2 - y3)) / d
        val aa = (x2 * x3 * (x2 - x3) * y1 + x3 * x1 * (x3 - x1) * y2 + x1 * x2 * (x1 - x2) * y3) / d
        // an eyelid is convex towards the pupil: reject implausible shapes
        if ((upper && cc >= 0) || (!upper && cc <= 0)) return Eyelid(aa, bb, cc)
    }
    // fall back to the most occluding detected point as a horizontal line
    val ys = points.filterNotNull().map { it.second }
    if (ys.isEmpty()) return null
    return Eyelid(if (upper) ys.max() else ys.min(), 0.0, 0.0)
}

private fun eyelidInBand(
    img: GrayImage, p: IrisCircle, iris: IrisCircle, xa: Int, xb: Int, upper: Boolean, ref: Pair<Double, Double>,
): Double? {
    val x0 = max(0, xa)
    val x1 = min(img.width - 1, xb)
    val guard = 0.08 * iris.r
    val yFrom: Int
    val yTo: Int
    if (upper) {
        // an eyelid can cover the top of the pupil: search down to the pupil center
        yFrom = max(1, (iris.y - iris.r + guard).toInt())
        yTo = min(img.height - 2, p.y.toInt())
    } else {
        yFrom = max(1, p.y.toInt())
        yTo = min(img.height - 2, (iris.y + iris.r - guard).toInt())
    }
    if (yTo - yFrom < 8 || x1 - x0 < 3) return null
    val rows = DoubleArray(yTo - yFrom + 1) { k ->
        val y = yFrom + k
        var s = 0.0
        var n = 0
        for (x in x0..x1) {
            if (hypot(x - iris.x, y - iris.y) <= iris.r && hypot(x - p.x, y - p.y) > p.r) {
                s += img[x, y]; n++
            }
        }
        if (n < 2) Double.NaN else s / n
    }
    val w = 5
    var bestY: Double? = null
    var best = 0.0
    var bestIrisSide = 0.0
    var bestSkinSide = 0.0
    for (k in 1 until rows.size) {
        var a = 0.0
        var na = 0
        var b = 0.0
        var nb = 0
        for (j in 1..w) {
            val before = rows.getOrNull(k - j) ?: Double.NaN
            val after = rows.getOrNull(k + j - 1) ?: Double.NaN
            if (!before.isNaN()) {
                a += before; na++
            }
            if (!after.isNaN()) {
                b += after; nb++
            }
        }
        if (na < 2 || nb < 2) continue
        a /= na
        b /= nb
        // upper eyelid: bright skin above, iris below; lower eyelid: iris above, skin below
        val step = if (upper) a - b else b - a
        if (step > best) {
            best = step
            bestY = (yFrom + k).toDouble()
            bestIrisSide = if (upper) b else a
            bestSkinSide = if (upper) a else b
        }
    }
    // the eyelid side must be clearly brighter than typical iris, not just the outer iris ring
    val (median, sigma) = ref
    val skinIsBright = bestSkinSide - median >= max(15.0, 1.5 * sigma)
    return bestY?.takeIf { best >= max(15.0, 0.2 * bestIrisSide) && skinIsBright }
}
