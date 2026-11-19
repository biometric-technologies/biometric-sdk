package net.iriscan.sdk.iris.impl

import net.iriscan.sdk.iris.IrisMatchProperties
import kotlin.math.sqrt

/**
 * Result of comparing two iris templates.
 * @property score similarity, 1 - normalized Hamming distance (0..1)
 * @property hammingDistance normalized Hamming distance at the best rotation
 * @property comparedBits number of bits valid in both templates at the best rotation
 * @property shift rotation compensation in template columns at the best rotation
 */
internal class IrisComparison(val score: Double, val hammingDistance: Double, val comparedBits: Int, val shift: Int)

private val NO_MATCH = IrisComparison(0.0, 1.0, 0, 0)

/**
 * Probe prepared for comparison against many templates: all rotations are
 * computed once.
 */
internal class IrisProbe(val template: IrisTemplate, props: IrisMatchProperties) {
    val shifts: IntArray = IntArray(2 * props.maxShift + 1) { it - props.maxShift }
    val rotated: Array<IrisTemplate> = Array(shifts.size) { template.shifted(shifts[it]) }
}

internal fun compare(probe: IrisProbe, other: IrisTemplate, props: IrisMatchProperties): IrisComparison {
    val t = probe.template
    if (t.rows != other.rows || t.cols != other.cols) return NO_MATCH
    val minBits = (props.minComparedBitsFraction * t.totalBits).toInt().coerceAtLeast(1)
    val nRef = (props.normalizationBits ?: (0.4 * t.totalBits).toInt()).coerceAtLeast(1).toDouble()
    var best: IrisComparison? = null
    for (s in probe.shifts.indices) {
        val a = probe.rotated[s]
        var diff = 0
        var n = 0
        for (w in a.code.indices) {
            val m = a.mask[w] and other.mask[w]
            n += m.countOneBits()
            diff += ((a.code[w] xor other.code[w]) and m).countOneBits()
        }
        if (n < minBits) continue
        val raw = diff.toDouble() / n
        val hd = (0.5 - (0.5 - raw) * sqrt(n / nRef)).coerceIn(0.0, 1.0)
        if (best == null || hd < best.hammingDistance) best = IrisComparison(1.0 - hd, hd, n, probe.shifts[s])
    }
    return best ?: NO_MATCH
}

/**
 * Similarity of two serialized templates (0..1).
 * Templates from SDK versions before 0.7 are compared with the legacy method;
 * a legacy template cannot be compared with a v2 template and scores 0.
 */
internal fun matchScore(template1: ByteArray, template2: ByteArray, props: IrisMatchProperties): Double {
    val v1 = IrisTemplate.isV2(template1)
    val v2 = IrisTemplate.isV2(template2)
    return when {
        v1 && v2 -> compare(IrisProbe(IrisTemplate.fromBytes(template1), props), IrisTemplate.fromBytes(template2), props).score
        !v1 && !v2 && template1.size == template2.size -> legacyMatch(template1, template2)
        else -> 0.0
    }
}

internal fun matches(template1: ByteArray, template2: ByteArray, props: IrisMatchProperties): Boolean {
    val score = matchScore(template1, template2, props)
    val threshold = if (IrisTemplate.isV2(template1)) props.threshold else props.legacyThreshold
    return score >= threshold
}

/** Best match in a gallery: index and score, or null when the gallery is empty. */
internal fun identify(probe: ByteArray, gallery: List<ByteArray>, props: IrisMatchProperties): Pair<Int, Double>? {
    if (gallery.isEmpty()) return null
    if (!IrisTemplate.isV2(probe)) {
        return gallery.indices.map { it to matchScore(probe, gallery[it], props) }.maxByOrNull { it.second }
    }
    val p = IrisProbe(IrisTemplate.fromBytes(probe), props)
    var bestIdx = -1
    var bestScore = -1.0
    for (i in gallery.indices) {
        val g = gallery[i]
        val score = if (IrisTemplate.isV2(g)) compare(p, IrisTemplate.fromBytes(g), props).score else 0.0
        if (score > bestScore) {
            bestScore = score; bestIdx = i
        }
    }
    return bestIdx to bestScore
}

/** Pre-0.7 templates: share of equal bytes. Kept only to read existing enrollments. */
internal fun legacyMatch(template1: ByteArray, template2: ByteArray): Double {
    check(template1.size == template2.size)
    var d = 0
    for (i in template1.indices) if (template1[i] == template2[i]) d++
    return d / template1.size.toDouble()
}
