// Ported from SourceAFIS for Java, Copyright Robert Važan, Apache License 2.0; see NOTICE.md.
package net.iriscan.sdk.finger.impl.afis

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

internal class MinutiaPair {
    var probe = 0
    var candidate = 0
    var probeRef = 0
    var candidateRef = 0
    var distance = 0
    var supportingEdges = 0
}

internal object EdgeHashes {
    private val COMPLEMENTARY_MAX_ANGLE_ERROR = FloatAngle.complementary(Parameters.MAX_ANGLE_ERROR)

    fun hash(edge: EdgeShape): Int {
        val lengthBin = edge.length / Parameters.MAX_DISTANCE_ERROR
        val referenceAngleBin = (edge.referenceAngle / Parameters.MAX_ANGLE_ERROR).toInt()
        val neighborAngleBin = (edge.neighborAngle / Parameters.MAX_ANGLE_ERROR).toInt()
        return (referenceAngleBin shl 24) + (neighborAngleBin shl 16) + lengthBin
    }

    fun matching(probe: EdgeShape, candidate: EdgeShape): Boolean {
        val lengthDelta = probe.length - candidate.length
        if (lengthDelta >= -Parameters.MAX_DISTANCE_ERROR && lengthDelta <= Parameters.MAX_DISTANCE_ERROR) {
            val referenceDelta = FloatAngle.difference(probe.referenceAngle, candidate.referenceAngle)
            if (referenceDelta <= Parameters.MAX_ANGLE_ERROR || referenceDelta >= COMPLEMENTARY_MAX_ANGLE_ERROR) {
                val neighborDelta = FloatAngle.difference(probe.neighborAngle, candidate.neighborAngle)
                if (neighborDelta <= Parameters.MAX_ANGLE_ERROR || neighborDelta >= COMPLEMENTARY_MAX_ANGLE_ERROR) return true
            }
        }
        return false
    }

    private fun coverage(edge: EdgeShape, action: (Int) -> Unit) {
        val err = Parameters.MAX_ANGLE_ERROR
        val minLengthBin = (edge.length - Parameters.MAX_DISTANCE_ERROR) / Parameters.MAX_DISTANCE_ERROR
        val maxLengthBin = (edge.length + Parameters.MAX_DISTANCE_ERROR) / Parameters.MAX_DISTANCE_ERROR
        val angleBins = ceil(2 * PI / err).toInt()
        val minReferenceBin = (FloatAngle.difference(edge.referenceAngle, err) / err).toInt()
        val maxReferenceBin = (FloatAngle.add(edge.referenceAngle, err) / err).toInt()
        val endReferenceBin = (maxReferenceBin + 1) % angleBins
        val minNeighborBin = (FloatAngle.difference(edge.neighborAngle, err) / err).toInt()
        val maxNeighborBin = (FloatAngle.add(edge.neighborAngle, err) / err).toInt()
        val endNeighborBin = (maxNeighborBin + 1) % angleBins
        for (lengthBin in minLengthBin..maxLengthBin) {
            var referenceBin = minReferenceBin
            while (referenceBin != endReferenceBin) {
                var neighborBin = minNeighborBin
                while (neighborBin != endNeighborBin) {
                    action((referenceBin shl 24) + (neighborBin shl 16) + lengthBin)
                    neighborBin = (neighborBin + 1) % angleBins
                }
                referenceBin = (referenceBin + 1) % angleBins
            }
        }
    }

    fun build(template: SearchTemplate): Map<Int, List<IndexedEdge>> {
        val map = HashMap<Int, MutableList<IndexedEdge>>()
        for (reference in template.minutiae.indices)
            for (neighbor in template.minutiae.indices)
                if (reference != neighbor) {
                    val edge = IndexedEdge(template.minutiae, reference, neighbor)
                    coverage(edge) { hash -> map.getOrPut(hash) { ArrayList() }.add(edge) }
                }
        return map
    }
}

/** A template prepared for one-to-many matching: SourceAFIS Probe. */
internal class Probe(val template: SearchTemplate) {
    val hash: Map<Int, List<IndexedEdge>> = EdgeHashes.build(template)
}

internal class PairingGraph {
    var count = 0
    var tree = arrayOfNulls<MinutiaPair>(1)
    var byProbe = arrayOfNulls<MinutiaPair>(1)
    var byCandidate = arrayOfNulls<MinutiaPair>(1)

    fun reserve(probe: Probe, candidate: SearchTemplate) {
        val capacity = probe.template.minutiae.size
        if (capacity > tree.size) {
            tree = arrayOfNulls(capacity)
            byProbe = arrayOfNulls(capacity)
        }
        if (byCandidate.size < candidate.minutiae.size) byCandidate = arrayOfNulls(candidate.minutiae.size)
    }

    fun addPair(pair: MinutiaPair) {
        tree[count] = pair
        byProbe[pair.probe] = pair
        byCandidate[pair.candidate] = pair
        ++count
    }

    fun support(pair: MinutiaPair) {
        val existing = byProbe[pair.probe]
        if (existing != null && existing.candidate == pair.candidate) {
            ++existing.supportingEdges
            ++byProbe[pair.probeRef]!!.supportingEdges
        }
    }

    fun clear() {
        for (i in 0 until count) {
            val pair = tree[i]!!
            byProbe[pair.probe] = null
            byCandidate[pair.candidate] = null
            if (i == 0) pair.supportingEdges = 0
            tree[i] = null
        }
        count = 0
    }
}

internal object EdgeSpider {
    private val COMPLEMENTARY_MAX_ANGLE_ERROR = FloatAngle.complementary(Parameters.MAX_ANGLE_ERROR)

    private fun matchPairs(pstar: Array<NeighborEdge>, cstar: Array<NeighborEdge>): List<MinutiaPair> {
        val results = ArrayList<MinutiaPair>()
        var start = 0
        var end = 0
        for (cedge in cstar) {
            while (start < pstar.size && pstar[start].length < cedge.length - Parameters.MAX_DISTANCE_ERROR) ++start
            if (end < start) end = start
            while (end < pstar.size && pstar[end].length <= cedge.length + Parameters.MAX_DISTANCE_ERROR) ++end
            for (pindex in start until end) {
                val pedge = pstar[pindex]
                val rdiff = FloatAngle.difference(pedge.referenceAngle, cedge.referenceAngle)
                if (rdiff <= Parameters.MAX_ANGLE_ERROR || rdiff >= COMPLEMENTARY_MAX_ANGLE_ERROR) {
                    val ndiff = FloatAngle.difference(pedge.neighborAngle, cedge.neighborAngle)
                    if (ndiff <= Parameters.MAX_ANGLE_ERROR || ndiff >= COMPLEMENTARY_MAX_ANGLE_ERROR) {
                        val pair = MinutiaPair()
                        pair.probe = pedge.neighbor.toInt()
                        pair.candidate = cedge.neighbor.toInt()
                        pair.distance = cedge.length.toInt()
                        results.add(pair)
                    }
                }
            }
        }
        return results
    }

    private fun collectEdges(
        pedges: Array<Array<NeighborEdge>>, cedges: Array<Array<NeighborEdge>>,
        pairing: PairingGraph, queue: JavaPriorityQueue<MinutiaPair>
    ) {
        val reference = pairing.tree[pairing.count - 1]!!
        for (pair in matchPairs(pedges[reference.probe], cedges[reference.candidate])) {
            pair.probeRef = reference.probe
            pair.candidateRef = reference.candidate
            if (pairing.byCandidate[pair.candidate] == null && pairing.byProbe[pair.probe] == null) queue.add(pair)
            else pairing.support(pair)
        }
    }

    private fun skipPaired(pairing: PairingGraph, queue: JavaPriorityQueue<MinutiaPair>) {
        while (!queue.isEmpty() &&
            (pairing.byProbe[queue.peek()!!.probe] != null || pairing.byCandidate[queue.peek()!!.candidate] != null)
        ) pairing.support(queue.remove())
    }

    fun crawl(
        pedges: Array<Array<NeighborEdge>>, cedges: Array<Array<NeighborEdge>>,
        pairing: PairingGraph, root: MinutiaPair, queue: JavaPriorityQueue<MinutiaPair>
    ) {
        queue.add(root)
        do {
            pairing.addPair(queue.remove())
            collectEdges(pedges, cedges, pairing, queue)
            skipPaired(pairing, queue)
        } while (!queue.isEmpty())
    }
}

internal object RootEnumerator {
    fun enumerate(probe: Probe, candidate: SearchTemplate): List<MinutiaPair> {
        val roots = ArrayList<MinutiaPair>()
        val duplicates = HashSet<Int>()
        val cminutiae = candidate.minutiae
        var lookups = 0
        var tried = 0
        for (shortEdges in booleanArrayOf(false, true)) {
            for (period in 1 until cminutiae.size) {
                for (phase in 0..period) {
                    var creference = phase
                    while (creference < cminutiae.size) {
                        val cneighbor = (creference + period) % cminutiae.size
                        val cedge = EdgeShape(cminutiae[creference], cminutiae[cneighbor])
                        if ((cedge.length >= Parameters.MIN_ROOT_EDGE_LENGTH) xor shortEdges) {
                            val matches = probe.hash[EdgeHashes.hash(cedge)]
                            if (matches != null) {
                                for (match in matches) {
                                    if (EdgeHashes.matching(match, cedge)) {
                                        val duplicateKey = (match.reference() shl 16) or creference
                                        if (duplicates.add(duplicateKey)) {
                                            val pair = MinutiaPair()
                                            pair.probe = match.reference()
                                            pair.candidate = creference
                                            roots.add(pair)
                                        }
                                        ++tried
                                        if (tried >= Parameters.MAX_TRIED_ROOTS) return roots
                                    }
                                }
                            }
                            ++lookups
                            if (lookups >= Parameters.MAX_ROOT_EDGE_LOOKUPS) return roots
                        }
                        creference += period + 1
                    }
                }
            }
        }
        return roots
    }
}

internal object Scoring {
    fun compute(probe: SearchTemplate, candidate: SearchTemplate, pairing: PairingGraph): Double {
        val pminutiae = probe.minutiae
        val cminutiae = candidate.minutiae
        val minutiaCount = pairing.count
        val minutiaScore = Parameters.MINUTIA_SCORE * minutiaCount
        val minutiaFractionInProbe = pairing.count / pminutiae.size.toDouble()
        val minutiaFractionInCandidate = pairing.count / cminutiae.size.toDouble()
        val minutiaFraction = 0.5 * (minutiaFractionInProbe + minutiaFractionInCandidate)
        val minutiaFractionScore = Parameters.MINUTIA_FRACTION_SCORE * minutiaFraction
        var supportingEdgeSum = 0
        var supportedMinutiaCount = 0
        var minutiaTypeHits = 0
        for (i in 0 until pairing.count) {
            val pair = pairing.tree[i]!!
            supportingEdgeSum += pair.supportingEdges
            if (pair.supportingEdges >= Parameters.MIN_SUPPORTING_EDGES) ++supportedMinutiaCount
            if (pminutiae[pair.probe].type == cminutiae[pair.candidate].type) ++minutiaTypeHits
        }
        val edgeCount = pairing.count + supportingEdgeSum
        val edgeScore = Parameters.EDGE_SCORE * edgeCount
        val supportedMinutiaScore = Parameters.SUPPORTED_MINUTIA_SCORE * supportedMinutiaCount
        val minutiaTypeScore = Parameters.MINUTIA_TYPE_SCORE * minutiaTypeHits
        val innerDistanceRadius = jround(Parameters.DISTANCE_ERROR_FLATNESS * Parameters.MAX_DISTANCE_ERROR)
        val innerAngleRadius = (Parameters.ANGLE_ERROR_FLATNESS * Parameters.MAX_ANGLE_ERROR).toFloat()
        var distanceErrorSum = 0
        var angleErrorSum = 0f
        for (i in 1 until pairing.count) {
            val pair = pairing.tree[i]!!
            val probeEdge = EdgeShape(pminutiae[pair.probeRef], pminutiae[pair.probe])
            val candidateEdge = EdgeShape(cminutiae[pair.candidateRef], cminutiae[pair.candidate])
            distanceErrorSum += max(innerDistanceRadius, abs(probeEdge.length - candidateEdge.length))
            angleErrorSum += max(innerAngleRadius, FloatAngle.distance(probeEdge.referenceAngle, candidateEdge.referenceAngle))
            angleErrorSum += max(innerAngleRadius, FloatAngle.distance(probeEdge.neighborAngle, candidateEdge.neighborAngle))
        }
        val distanceErrorPotential = Parameters.MAX_DISTANCE_ERROR * max(0, pairing.count - 1)
        val distanceAccuracySum = distanceErrorPotential - distanceErrorSum
        val distanceAccuracyScore = Parameters.DISTANCE_ACCURACY_SCORE *
            (if (distanceErrorPotential > 0) distanceAccuracySum / distanceErrorPotential.toDouble() else 0.0)
        val angleErrorPotential: Float = Parameters.MAX_ANGLE_ERROR * max(0, pairing.count - 1) * 2
        val angleAccuracySum: Float = angleErrorPotential - angleErrorSum
        val angleAccuracyScore = Parameters.ANGLE_ACCURACY_SCORE *
            (if (angleErrorPotential > 0) (angleAccuracySum / angleErrorPotential).toDouble() else 0.0)
        val totalScore = minutiaScore + minutiaFractionScore + supportedMinutiaScore + edgeScore +
            minutiaTypeScore + distanceAccuracyScore + angleAccuracyScore
        return shape(totalScore)
    }

    private fun shape(raw: Double): Double {
        if (raw < Parameters.THRESHOLD_FMR_MAX) return 0.0
        if (raw < Parameters.THRESHOLD_FMR_2) return interpolate(raw, Parameters.THRESHOLD_FMR_MAX, Parameters.THRESHOLD_FMR_2, 0.0, 3.0)
        if (raw < Parameters.THRESHOLD_FMR_10) return interpolate(raw, Parameters.THRESHOLD_FMR_2, Parameters.THRESHOLD_FMR_10, 3.0, 7.0)
        if (raw < Parameters.THRESHOLD_FMR_100) return interpolate(raw, Parameters.THRESHOLD_FMR_10, Parameters.THRESHOLD_FMR_100, 10.0, 10.0)
        if (raw < Parameters.THRESHOLD_FMR_1000) return interpolate(raw, Parameters.THRESHOLD_FMR_100, Parameters.THRESHOLD_FMR_1000, 20.0, 10.0)
        if (raw < Parameters.THRESHOLD_FMR_10_000) return interpolate(raw, Parameters.THRESHOLD_FMR_1000, Parameters.THRESHOLD_FMR_10_000, 30.0, 10.0)
        if (raw < Parameters.THRESHOLD_FMR_100_000) return interpolate(raw, Parameters.THRESHOLD_FMR_10_000, Parameters.THRESHOLD_FMR_100_000, 40.0, 10.0)
        return (raw - Parameters.THRESHOLD_FMR_100_000) / (Parameters.THRESHOLD_FMR_100_000 - Parameters.THRESHOLD_FMR_100) * 30 + 50
    }

    private fun interpolate(raw: Double, min: Double, max: Double, start: Double, length: Double) =
        (raw - min) / (max - min) * length + start
}

/**
 * Compares one probe against candidates. Holds scratch buffers, so an instance must not be shared
 * between threads; SourceAFIS keeps one per thread for the same reason.
 */
internal class MatcherEngine {
    private val pairing = PairingGraph()
    private val queue = JavaPriorityQueue<MinutiaPair>(compareBy { it.distance })

    fun match(probe: Probe, candidate: SearchTemplate): Double {
        pairing.reserve(probe, candidate)
        val roots = RootEnumerator.enumerate(probe, candidate)
        var high = 0.0
        var best = -1
        for (i in roots.indices) {
            EdgeSpider.crawl(probe.template.edges, candidate.edges, pairing, roots[i], queue)
            val partial = Scoring.compute(probe.template, candidate, pairing)
            if (best < 0 || partial > high) {
                high = partial
                best = i
            }
            pairing.clear()
        }
        return high
    }
}
