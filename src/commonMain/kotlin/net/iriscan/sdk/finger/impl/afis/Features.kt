// Ported from SourceAFIS for Java, Copyright Robert Važan, Apache License 2.0; see NOTICE.md.
package net.iriscan.sdk.finger.impl.afis

import kotlin.math.sqrt

internal enum class MinutiaType { ENDING, BIFURCATION }

internal class FeatureMinutia(val position: IntPoint, val direction: Float, val type: MinutiaType)

internal class SearchMinutia(val x: Short, val y: Short, val direction: Float, val type: MinutiaType) {
    constructor(feature: FeatureMinutia) : this(feature.position.x.toShort(), feature.position.y.toShort(), feature.direction, feature.type)

    fun feature() = FeatureMinutia(IntPoint(x.toInt(), y.toInt()), direction, type)
}

internal open class EdgeShape {
    val length: Short
    val referenceAngle: Float
    val neighborAngle: Float

    constructor(length: Short, referenceAngle: Float, neighborAngle: Float) {
        this.length = length
        this.referenceAngle = referenceAngle
        this.neighborAngle = neighborAngle
    }

    constructor(reference: SearchMinutia, neighbor: SearchMinutia) {
        var quadrant = 0f
        var x = neighbor.x - reference.x
        var y = neighbor.y - reference.y
        if (y < 0) {
            x = -x
            y = -y
            quadrant = FloatAngle.PI
        }
        if (x < 0) {
            val tmp = -x
            x = y
            y = tmp
            quadrant += FloatAngle.HALF_PI
        }
        val shift = 32 - ((x or y) ushr POLAR_CACHE_BITS).countLeadingZeroBits()
        val offset = (y shr shift) * POLAR_CACHE_RADIUS + (x shr shift)
        length = (POLAR_DISTANCE_CACHE[offset] shl shift).toShort()
        val angle = POLAR_ANGLE_CACHE[offset] + quadrant
        referenceAngle = FloatAngle.difference(reference.direction, angle)
        neighborAngle = FloatAngle.difference(neighbor.direction, FloatAngle.opposite(angle))
    }

    companion object {
        private const val POLAR_CACHE_BITS = 8
        private const val POLAR_CACHE_RADIUS = 1 shl POLAR_CACHE_BITS
        private val POLAR_DISTANCE_CACHE = IntArray(POLAR_CACHE_RADIUS * POLAR_CACHE_RADIUS)
        private val POLAR_ANGLE_CACHE = FloatArray(POLAR_CACHE_RADIUS * POLAR_CACHE_RADIUS)

        init {
            for (y in 0 until POLAR_CACHE_RADIUS) for (x in 0 until POLAR_CACHE_RADIUS) {
                POLAR_DISTANCE_CACHE[y * POLAR_CACHE_RADIUS + x] = jround(sqrt((sq(x) + sq(y)).toDouble()))
                POLAR_ANGLE_CACHE[y * POLAR_CACHE_RADIUS + x] =
                    if (y > 0 || x > 0) DoubleAngle.atan(DoublePoint(x.toDouble(), y.toDouble())).toFloat() else 0f
            }
        }
    }
}

internal class IndexedEdge(minutiae: Array<SearchMinutia>, reference: Int, neighbor: Int) :
    EdgeShape(minutiae[reference], minutiae[neighbor]) {
    private val referenceIndex = reference.toByte()
    private val neighborIndex = neighbor.toByte()
    fun reference() = referenceIndex.toInt() and 0xff
    fun neighbor() = neighborIndex.toInt() and 0xff
}

internal class NeighborEdge(minutiae: Array<SearchMinutia>, reference: Int, neighbor: Int) :
    EdgeShape(minutiae[reference], minutiae[neighbor]) {
    val neighbor: Short = neighbor.toShort()

    companion object {
        fun buildTable(minutiae: Array<SearchMinutia>): Array<Array<NeighborEdge>> {
            val star = ArrayList<NeighborEdge>()
            val allSqDistances = IntArray(minutiae.size)
            return Array(minutiae.size) { reference ->
                val rminutia = minutiae[reference]
                var maxSqDistance = Int.MAX_VALUE
                if (minutiae.size - 1 > Parameters.EDGE_TABLE_NEIGHBORS) {
                    for (neighbor in minutiae.indices) {
                        val nminutia = minutiae[neighbor]
                        allSqDistances[neighbor] = sq(rminutia.x - nminutia.x) + sq(rminutia.y - nminutia.y)
                    }
                    allSqDistances.sort()
                    maxSqDistance = allSqDistances[Parameters.EDGE_TABLE_NEIGHBORS]
                }
                for (neighbor in minutiae.indices) {
                    val nminutia = minutiae[neighbor]
                    if (neighbor != reference && sq(rminutia.x - nminutia.x) + sq(rminutia.y - nminutia.y) <= maxSqDistance)
                        star.add(NeighborEdge(minutiae, reference, neighbor))
                }
                star.sortWith(compareBy<NeighborEdge> { it.length }.thenBy { it.neighbor })
                while (star.size > Parameters.EDGE_TABLE_NEIGHBORS) star.removeAt(star.size - 1)
                val row = star.toTypedArray()
                star.clear()
                row
            }
        }
    }
}

internal enum class SkeletonType { RIDGES, VALLEYS }

/**
 * The points of a ridge, seen from either end. Java SourceAFIS uses a circular list and a
 * reversed view of it; here both directions share one deque.
 */
internal class RidgePoints(private val deque: ArrayDeque<IntPoint>, private val reversed: Boolean) : Iterable<IntPoint> {
    val size get() = deque.size
    operator fun get(index: Int): IntPoint = if (reversed) deque[deque.size - 1 - index] else deque[index]
    fun add(point: IntPoint) { if (reversed) deque.addFirst(point) else deque.addLast(point) }
    fun removeLast(): IntPoint = if (reversed) deque.removeFirst() else deque.removeLast()
    override fun iterator(): Iterator<IntPoint> = (0 until size).asSequence().map { get(it) }.iterator()
}

internal class SkeletonRidge private constructor(deque: ArrayDeque<IntPoint>, isReversed: Boolean, other: SkeletonRidge?) {
    val points = RidgePoints(deque, isReversed)
    val reversed: SkeletonRidge = other ?: SkeletonRidge(deque, !isReversed, this)
    private var startMinutia: SkeletonMinutia? = null
    private var endMinutia: SkeletonMinutia? = null

    constructor() : this(ArrayDeque(), false, null)

    fun start() = startMinutia
    fun start(value: SkeletonMinutia?) {
        if (startMinutia !== value) {
            if (startMinutia != null) {
                val detachFrom = startMinutia!!
                startMinutia = null
                detachFrom.detachStart(this)
            }
            startMinutia = value
            startMinutia?.attachStart(this)
            reversed.endMinutia = value
        }
    }
    fun end() = endMinutia
    fun end(value: SkeletonMinutia?) {
        if (endMinutia !== value) {
            endMinutia = value
            reversed.start(value)
        }
    }
    fun detach() {
        start(null)
        end(null)
    }
    fun direction(): Float {
        var first = Parameters.RIDGE_DIRECTION_SKIP
        var last = Parameters.RIDGE_DIRECTION_SKIP + Parameters.RIDGE_DIRECTION_SAMPLE - 1
        if (last >= points.size) {
            val shift = last - points.size + 1
            last -= shift
            first -= shift
        }
        if (first < 0) first = 0
        return DoubleAngle.atan(points[first], points[last]).toFloat()
    }
}

internal class SkeletonMinutia(val position: IntPoint) {
    val ridges = ArrayList<SkeletonRidge>()

    fun attachStart(ridge: SkeletonRidge) {
        if (!ridges.containsRef(ridge)) {
            ridges.add(ridge)
            ridge.start(this)
        }
    }
    fun detachStart(ridge: SkeletonRidge) {
        if (ridges.containsRef(ridge)) {
            ridges.removeRef(ridge)
            if (ridge.start() === this) ridge.start(null)
        }
    }
}

/** Identity lookups: ridges and minutiae have no equals of their own, as in Java. */
internal fun <T> MutableList<T>.containsRef(item: T) = any { it === item }
internal fun <T> MutableList<T>.removeRef(item: T): Boolean {
    val i = indexOfFirst { it === item }
    if (i < 0) return false
    removeAt(i)
    return true
}

internal class Skeleton(val type: SkeletonType, val size: IntPoint) {
    val minutiae = ArrayList<SkeletonMinutia>()

    fun addMinutia(minutia: SkeletonMinutia) { minutiae.add(minutia) }
    fun removeMinutia(minutia: SkeletonMinutia) { minutiae.removeRef(minutia) }
    fun shadow(): BooleanMatrix {
        val shadow = BooleanMatrix(size)
        for (minutia in minutiae) {
            shadow[minutia.position] = true
            for (ridge in minutia.ridges)
                if (ridge.start()!!.position.y <= ridge.end()!!.position.y)
                    for (point in ridge.points) shadow[point] = true
        }
        return shadow
    }
}
