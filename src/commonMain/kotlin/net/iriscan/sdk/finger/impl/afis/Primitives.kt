// Ported from SourceAFIS for Java (https://github.com/robertvazan/sourceafis-java),
// Copyright Robert Važan, Apache License 2.0; see NOTICE.md in this folder.
package net.iriscan.sdk.finger.impl.afis

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** Java's Math.round: half up. kotlin.math.round rounds half to even and would change templates. */
internal fun jround(value: Double): Int = floor(value + 0.5).toInt()

internal fun sq(value: Int) = value * value
internal fun sq(value: Double) = value * value
internal fun roundUpDiv(dividend: Int, divisor: Int) = (dividend + divisor - 1) / divisor

internal fun interpolate(start: Double, end: Double, position: Double) = start + position * (end - start)
internal fun interpolate(bottomleft: Double, bottomright: Double, topleft: Double, topright: Double, x: Double, y: Double): Double {
    val left = interpolate(topleft, bottomleft, y)
    val right = interpolate(topright, bottomright, y)
    return interpolate(left, right, x)
}
internal fun interpolateExponential(start: Double, end: Double, position: Double) = (end / start).pow(position) * start

internal class IntPoint(val x: Int, val y: Int) : Comparable<IntPoint> {
    fun area() = x * y
    fun lengthSq() = sq(x) + sq(y)
    fun contains(other: IntPoint) = other.x >= 0 && other.y >= 0 && other.x < x && other.y < y
    operator fun plus(other: IntPoint) = IntPoint(x + other.x, y + other.y)
    operator fun minus(other: IntPoint) = IntPoint(x - other.x, y - other.y)
    fun negate() = IntPoint(-x, -y)
    fun toDouble() = DoublePoint(x.toDouble(), y.toDouble())

    fun lineTo(to: IntPoint): Array<IntPoint> {
        val relative = to - this
        return if (abs(relative.x) >= abs(relative.y)) {
            val n = abs(relative.x)
            when {
                relative.x > 0 -> Array(n + 1) { i -> IntPoint(x + i, y + jround(i * (relative.y / relative.x.toDouble()))) }
                relative.x < 0 -> Array(n + 1) { i -> IntPoint(x - i, y - jround(i * (relative.y / relative.x.toDouble()))) }
                else -> arrayOf(this)
            }
        } else {
            val n = abs(relative.y)
            when {
                relative.y > 0 -> Array(n + 1) { i -> IntPoint(x + jround(i * (relative.x / relative.y.toDouble())), y + i) }
                relative.y < 0 -> Array(n + 1) { i -> IntPoint(x - jround(i * (relative.x / relative.y.toDouble())), y - i) }
                else -> arrayOf(this)
            }
        }
    }

    /** Every point of the rectangle from (0, 0) to this size, row by row. */
    inline fun forEach(action: (IntPoint) -> Unit) {
        for (yy in 0 until y) for (xx in 0 until x) action(IntPoint(xx, yy))
    }

    override fun equals(other: Any?) = other is IntPoint && other.x == x && other.y == y
    override fun hashCode() = 31 * x + y
    override fun compareTo(other: IntPoint): Int {
        val resultY = y.compareTo(other.y)
        return if (resultY != 0) resultY else x.compareTo(other.x)
    }
    override fun toString() = "[$x,$y]"

    companion object {
        val ZERO = IntPoint(0, 0)
        val EDGE_NEIGHBORS = arrayOf(IntPoint(0, -1), IntPoint(-1, 0), IntPoint(1, 0), IntPoint(0, 1))
        val CORNER_NEIGHBORS = arrayOf(
            IntPoint(-1, -1), IntPoint(0, -1), IntPoint(1, -1), IntPoint(-1, 0),
            IntPoint(1, 0), IntPoint(-1, 1), IntPoint(0, 1), IntPoint(1, 1)
        )
    }
}

internal class DoublePoint(val x: Double, val y: Double) {
    operator fun plus(other: DoublePoint) = DoublePoint(x + other.x, y + other.y)
    fun negate() = DoublePoint(-x, -y)
    operator fun times(factor: Double) = DoublePoint(factor * x, factor * y)
    fun round() = IntPoint(jround(x), jround(y))

    companion object {
        val ZERO = DoublePoint(0.0, 0.0)
    }
}

internal class IntRange(val start: Int, val end: Int) {
    fun length() = end - start

    companion object {
        val ZERO = IntRange(0, 0)
    }
}

internal class IntRect(val x: Int, val y: Int, val width: Int, val height: Int) {
    constructor(size: IntPoint) : this(0, 0, size.x, size.y)

    fun left() = x
    fun top() = y
    fun right() = x + width
    fun bottom() = y + height
    fun area() = width * height
    fun center() = IntPoint((right() + left()) / 2, (top() + bottom()) / 2)
    fun intersect(other: IntRect) = between(
        IntPoint(max(left(), other.left()), max(top(), other.top())),
        IntPoint(min(right(), other.right()), min(bottom(), other.bottom()))
    )
    fun move(delta: IntPoint) = IntRect(x + delta.x, y + delta.y, width, height)

    /** Every point of the rectangle, row by row. */
    inline fun forEach(action: (IntPoint) -> Unit) {
        for (yy in 0 until height) for (xx in 0 until width) action(IntPoint(x + xx, y + yy))
    }

    override fun equals(other: Any?) = other is IntRect && other.x == x && other.y == y && other.width == width && other.height == height
    override fun hashCode() = ((x * 31 + y) * 31 + width) * 31 + height

    companion object {
        fun between(startX: Int, startY: Int, endX: Int, endY: Int) = IntRect(startX, startY, endX - startX, endY - startY)
        fun between(start: IntPoint, end: IntPoint) = between(start.x, start.y, end.x, end.y)
        fun around(x: Int, y: Int, radius: Int) = between(x - radius, y - radius, x + radius + 1, y + radius + 1)
        fun around(center: IntPoint, radius: Int) = around(center.x, center.y, radius)
    }
}

internal object DoubleAngle {
    const val PI2 = 2 * PI
    const val INV_PI2 = 1.0 / PI2
    const val HALF_PI = 0.5 * PI

    fun toVector(angle: Double) = DoublePoint(cos(angle), sin(angle))
    fun atan(vector: DoublePoint): Double {
        val angle = atan2(vector.y, vector.x)
        return if (angle >= 0) angle else angle + PI2
    }
    fun atan(vector: IntPoint) = atan(vector.toDouble())
    fun atan(center: IntPoint, point: IntPoint) = atan(point - center)
    fun toOrientation(angle: Double) = if (angle < PI) 2 * angle else 2 * (angle - PI)
    fun fromOrientation(angle: Double) = 0.5 * angle
    fun add(start: Double, delta: Double): Double {
        val angle = start + delta
        return if (angle < PI2) angle else angle - PI2
    }
    fun bucketCenter(bucket: Int, resolution: Int) = PI2 * (2 * bucket + 1) / (2 * resolution)
    fun quantize(angle: Double, resolution: Int): Int {
        val result = (angle * INV_PI2 * resolution).toInt()
        return if (result < 0) 0 else if (result >= resolution) resolution - 1 else result
    }
    fun opposite(angle: Double) = if (angle < PI) angle + PI else angle - PI
    fun distance(first: Double, second: Double): Double {
        val delta = abs(first - second)
        return if (delta <= PI) delta else PI2 - delta
    }
    fun difference(first: Double, second: Double): Double {
        val angle = first - second
        return if (angle >= 0) angle else angle + PI2
    }
    fun complementary(angle: Double): Double {
        val complement = PI2 - angle
        return if (complement < PI2) complement else complement - PI2
    }
}

internal object FloatAngle {
    const val PI2 = DoubleAngle.PI2.toFloat()
    const val PI = kotlin.math.PI.toFloat()
    const val HALF_PI = DoubleAngle.HALF_PI.toFloat()

    fun add(start: Float, delta: Float): Float {
        val angle = start + delta
        return if (angle < PI2) angle else angle - PI2
    }
    fun difference(first: Float, second: Float): Float {
        val angle = first - second
        return if (angle >= 0) angle else angle + PI2
    }
    fun distance(first: Float, second: Float): Float {
        val delta = abs(first - second)
        return if (delta <= PI) delta else PI2 - delta
    }
    fun opposite(angle: Float) = if (angle < PI) angle + PI else angle - PI
    fun complementary(angle: Float): Float {
        val complement = PI2 - angle
        return if (complement < PI2) complement else complement - PI2
    }
}

internal class BooleanMatrix(val width: Int, val height: Int) {
    private val cells = BooleanArray(width * height)

    constructor(size: IntPoint) : this(size.x, size.y)
    constructor(other: BooleanMatrix) : this(other.width, other.height) {
        other.cells.copyInto(cells)
    }

    fun size() = IntPoint(width, height)
    operator fun get(x: Int, y: Int) = cells[y * width + x]
    operator fun get(at: IntPoint) = get(at.x, at.y)
    fun get(x: Int, y: Int, fallback: Boolean) = if (x < 0 || y < 0 || x >= width || y >= height) fallback else cells[y * width + x]
    fun get(at: IntPoint, fallback: Boolean) = get(at.x, at.y, fallback)
    operator fun set(x: Int, y: Int, value: Boolean) { cells[y * width + x] = value }
    operator fun set(at: IntPoint, value: Boolean) = set(at.x, at.y, value)
    fun invert() { for (i in cells.indices) cells[i] = !cells[i] }
    fun merge(other: BooleanMatrix) {
        require(other.width == width && other.height == height)
        for (i in cells.indices) cells[i] = cells[i] or other.cells[i]
    }
}

internal class DoubleMatrix(val width: Int, val height: Int) {
    private val cells = DoubleArray(width * height)

    constructor(size: IntPoint) : this(size.x, size.y)

    fun size() = IntPoint(width, height)
    operator fun get(x: Int, y: Int) = cells[y * width + x]
    operator fun get(at: IntPoint) = get(at.x, at.y)
    operator fun set(x: Int, y: Int, value: Double) { cells[y * width + x] = value }
    operator fun set(at: IntPoint, value: Double) = set(at.x, at.y, value)
    fun add(x: Int, y: Int, value: Double) { cells[y * width + x] += value }
    fun add(at: IntPoint, value: Double) = add(at.x, at.y, value)
    fun multiply(x: Int, y: Int, value: Double) { cells[y * width + x] *= value }
    fun multiply(at: IntPoint, value: Double) = multiply(at.x, at.y, value)
}

internal class IntMatrix(val width: Int, val height: Int) {
    private val array = IntArray(width * height)

    constructor(size: IntPoint) : this(size.x, size.y)

    fun size() = IntPoint(width, height)
    operator fun get(x: Int, y: Int) = array[y * width + x]
    operator fun get(at: IntPoint) = get(at.x, at.y)
    operator fun set(x: Int, y: Int, value: Int) { array[y * width + x] = value }
    operator fun set(at: IntPoint, value: Int) = set(at.x, at.y, value)
}

internal class DoublePointMatrix(val width: Int, val height: Int) {
    private val vectors = DoubleArray(2 * width * height)

    constructor(size: IntPoint) : this(size.x, size.y)

    fun size() = IntPoint(width, height)
    operator fun get(x: Int, y: Int): DoublePoint {
        val i = 2 * (y * width + x)
        return DoublePoint(vectors[i], vectors[i + 1])
    }
    operator fun get(at: IntPoint) = get(at.x, at.y)
    fun set(x: Int, y: Int, px: Double, py: Double) {
        val i = 2 * (y * width + x)
        vectors[i] = px
        vectors[i + 1] = py
    }
    operator fun set(x: Int, y: Int, point: DoublePoint) = set(x, y, point.x, point.y)
    operator fun set(at: IntPoint, point: DoublePoint) = set(at.x, at.y, point)
    fun add(x: Int, y: Int, px: Double, py: Double) {
        val i = 2 * (y * width + x)
        vectors[i] += px
        vectors[i + 1] += py
    }
    fun add(x: Int, y: Int, point: DoublePoint) = add(x, y, point.x, point.y)
    fun add(at: IntPoint, point: DoublePoint) = add(at.x, at.y, point)
}

internal class HistogramCube(val width: Int, val height: Int, val bins: Int) {
    private val counts = IntArray(width * height * bins)

    constructor(size: IntPoint, bins: Int) : this(size.x, size.y, bins)

    fun constrain(z: Int) = max(0, min(bins - 1, z))
    operator fun get(x: Int, y: Int, z: Int) = counts[(y * width + x) * bins + z]
    operator fun get(at: IntPoint, z: Int) = get(at.x, at.y, z)
    fun sum(x: Int, y: Int): Int {
        var sum = 0
        for (i in 0 until bins) sum += get(x, y, i)
        return sum
    }
    fun sum(at: IntPoint) = sum(at.x, at.y)
    operator fun set(x: Int, y: Int, z: Int, value: Int) { counts[(y * width + x) * bins + z] = value }
    fun add(x: Int, y: Int, z: Int, value: Int) { counts[(y * width + x) * bins + z] += value }
    fun add(at: IntPoint, z: Int, value: Int) = add(at.x, at.y, z, value)
    fun increment(x: Int, y: Int, z: Int) = add(x, y, z, 1)
    fun increment(at: IntPoint, z: Int) = increment(at.x, at.y, z)
}

internal class BlockGrid(val blocks: IntPoint) {
    val corners = IntPoint(blocks.x + 1, blocks.y + 1)
    val x = IntArray(blocks.x + 1)
    val y = IntArray(blocks.y + 1)

    fun corner(atX: Int, atY: Int) = IntPoint(x[atX], y[atY])
    fun corner(at: IntPoint) = corner(at.x, at.y)
    fun block(atX: Int, atY: Int) = IntRect.between(corner(atX, atY), corner(atX + 1, atY + 1))
    fun block(at: IntPoint) = block(at.x, at.y)
}

internal class BlockMap(width: Int, height: Int, maxBlockSize: Int) {
    val pixels = IntPoint(width, height)
    val primary = BlockGrid(IntPoint(roundUpDiv(pixels.x, maxBlockSize), roundUpDiv(pixels.y, maxBlockSize)))
    val secondary: BlockGrid

    init {
        for (y in 0..primary.blocks.y) primary.y[y] = y * pixels.y / primary.blocks.y
        for (x in 0..primary.blocks.x) primary.x[x] = x * pixels.x / primary.blocks.x
        secondary = BlockGrid(primary.corners)
        secondary.y[0] = 0
        for (y in 0 until primary.blocks.y) secondary.y[y + 1] = primary.block(0, y).center().y
        secondary.y[secondary.blocks.y] = pixels.y
        secondary.x[0] = 0
        for (x in 0 until primary.blocks.x) secondary.x[x + 1] = primary.block(x, 0).center().x
        secondary.x[secondary.blocks.x] = pixels.x
    }
}

/** java.util.PriorityQueue's binary heap, so that ties come out in the same order as in Java. */
internal class JavaPriorityQueue<T>(private val comparator: Comparator<in T>) {
    private var queue = arrayOfNulls<Any?>(11)
    var size = 0
        private set

    fun isEmpty() = size == 0

    fun add(item: T) {
        if (size >= queue.size) queue = queue.copyOf(if (queue.size < 64) queue.size + queue.size + 2 else queue.size + (queue.size shr 1))
        siftUp(size, item)
        size++
    }

    @Suppress("UNCHECKED_CAST")
    fun peek(): T? = if (size == 0) null else queue[0] as T

    @Suppress("UNCHECKED_CAST")
    fun remove(): T {
        if (size == 0) throw NoSuchElementException()
        val result = queue[0] as T
        val n = --size
        val x = queue[n] as T
        queue[n] = null
        if (n > 0) siftDown(0, x, n)
        return result
    }

    @Suppress("UNCHECKED_CAST")
    private fun siftUp(start: Int, x: T) {
        var k = start
        while (k > 0) {
            val parent = (k - 1) ushr 1
            val e = queue[parent] as T
            if (comparator.compare(x, e) >= 0) break
            queue[k] = e
            k = parent
        }
        queue[k] = x
    }

    @Suppress("UNCHECKED_CAST")
    private fun siftDown(start: Int, x: T, n: Int) {
        var k = start
        val half = n ushr 1
        while (k < half) {
            var child = (k shl 1) + 1
            var c = queue[child] as T
            val right = child + 1
            if (right < n && comparator.compare(c, queue[right] as T) > 0) {
                child = right
                c = queue[child] as T
            }
            if (comparator.compare(x, c) <= 0) break
            queue[k] = c
            k = child
        }
        queue[k] = x
    }
}
