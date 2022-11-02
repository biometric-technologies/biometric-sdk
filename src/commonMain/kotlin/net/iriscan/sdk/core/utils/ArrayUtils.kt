package net.iriscan.sdk.core.utils

import kotlin.math.abs

/**
 * @author Slava Gornostal
 */
internal operator fun Array<IntArray>.times(array: Array<IntArray>): Array<IntArray> =
    Array(array.size) { i ->
        IntArray(array[0].size) { j ->
            this[i][j] * array[i][j]
        }
    }

internal operator fun IntArray.minus(array: IntArray): IntArray =
    IntArray(array.size) { i -> abs(this[i] - array[i]) }


internal operator fun IntArray.get(range: IntRange): IntArray =
    this.sliceArray(range)

internal fun Array<IntArray>.sum(): Int = this.sumOf { it.sum() }

internal fun IntArray.median(): Int = this.sortedArray().let {
    if (it.size % 2 == 0)
        (it[it.size / 2] + it[(it.size - 1) / 2]) / 2
    else
        it[it.size / 2]
}

internal infix fun IntArray.convolve(kernel: DoubleArray): IntArray {
    val result = IntArray(size)
    for (i in indices) {
        result[i] = kernel.indices
            .map { it to i + it - kernel.size / 2 }
            .filter { it.second in indices }
            .sumOf { (this[it.second] * kernel[it.first]).toInt() }
    }
    return result
}

internal fun IntArray.smooth(kernel: Int): IntArray {
    val result = IntArray(this.size)
    for (index in this.indices) {
        var acc = 0
        var count = 0
        for (b in kotlin.math.max(0, index - kernel) until index) {
            acc += this[b]
            count++
        }
        for (a in index..kotlin.math.min(index + kernel, this.size - 1)) {
            acc += this[a]
            count++
        }
        result[index] = acc / count
    }
    return result
}

internal fun IntArray.findSegmentsGte(value: Int): List<IntRange> {
    val result = mutableListOf<IntRange>()
    var index = 0
    var start = 0
    while (index < this.size) {
        when {
            this[index] > value && start == 0 -> {
                start = index
            }

            start > 0 && (this[index] < value || index == this.size - 1) -> {
                result += start until index
                start = 0
            }
        }
        index++
    }
    return result
}
