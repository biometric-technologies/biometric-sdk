package net.iriscan.sdk.finger.impl

import net.iriscan.sdk.iris.impl.GrayImage
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Length of the signature window across the ridges, pixels at 500 ppi (Hong, Wan and Jain 1998): two blocks, so that
 * it holds 2.7 periods of 12 px ridges and 5.3 periods of 6 px ridges.
 */
private const val SIGNATURE_LENGTH = 32

/**
 * Width of the signature window along the ridges, pixels at 500 ppi: one block. Averaging each line along the ridges
 * divides sensor noise by about 4, and ridges curve little over 16 px.
 */
private const val SIGNATURE_WIDTH = 16

/** Ridge periods that can be measured, pixels at 500 ppi: 3 to 25 (Hong, Wan and Jain 1998). */
private const val MIN_RIDGE_PERIOD = 3.0
private const val MAX_RIDGE_PERIOD = 25.0

/** Period of every block when no block could be measured, pixels: the ridge period of adults at 500 ppi. */
internal const val DEFAULT_RIDGE_PERIOD = 9f

/**
 * Frequencies tried from 1 / [MAX_RIDGE_PERIOD] to 1 / [MIN_RIDGE_PERIOD] cycles per pixel. Steps of 0.0073 cycles per
 * pixel are about a quarter of the distance from the top of the peak that a signature of 32 lines gives to its first
 * zero (1/32), so the grid cannot step over the peak, and a parabola through the best step and its two neighbours finds
 * its top.
 */
private const val FREQUENCY_STEPS = 40

/**
 * Least share of the variance of a signature that the fitted sinusoid must explain for its period to count: above what
 * noise alone reaches in a window (0.28 on average, 0.62 at most), below what the least sinusoidal ridges give (a
 * square wave, black ridges on white, has 8/π² = 0.81 of its variance in its fundamental; the noisy ridges of a dry
 * finger give 0.9 or more). A window on a smudge explains 0.16 to 0.55, and some windows across a minutia or the centre
 * of a whorl 0.53 to 0.7; such blocks take the period of their neighbours.
 */
private const val MIN_EXPLAINED_SHARE = 0.7

/**
 * Ridge period, the distance between neighbouring ridge centres in pixels, of every block of the foreground mask's
 * grid: [blockSize] px squares from the top left corner of the image. The Gabor filter of the next step is tuned to
 * it, and later steps measure distances in ridge periods.
 */
internal class RidgePeriodField(
    val width: Int,
    val height: Int,
    val blockSize: Int,
    /** Period of every block, row by row. */
    private val periods: FloatArray,
) {
    val columns: Int = (width + blockSize - 1) / blockSize
    val rows: Int = (height + blockSize - 1) / blockSize

    fun blockPeriod(column: Int, row: Int): Float = periods[row * columns + column]

    /** Period of the block that holds pixel (x, y). */
    fun periodAt(x: Int, y: Int): Float = periods[(y / blockSize) * columns + x / blockSize]
}

/**
 * Ridge period of every block of [mask], after Hong, Wan and Jain 1998.
 *
 * 1. Signature: for every foreground block, a window of [SIGNATURE_LENGTH] px across the ridges and
 *    [SIGNATURE_WIDTH] px along them, centred on the block and turned to the orientation of [field] there; the mean
 *    gray value of each line along the ridges gives a profile across the ridges. Where the ridges are parallel it is
 *    a sinusoid with the period of the ridges.
 * 2. Period: the one whose sinusoid, with amplitude, phase and offset fitted by least squares, explains the signature
 *    best. Hong, Wan and Jain take the mean distance between the peaks of the signature instead, but noise splits the
 *    flat top of a wide ridge into several peaks, and the period then comes out too short. A block whose sinusoid
 *    explains less than [MIN_EXPLAINED_SHARE] of its signature gets no period of its own.
 * 3. Each measured period is replaced by the median of the measured periods around it ([medianOfNeighbours]).
 * 4. Blocks without a period, those outside the mask included, take the mean of their neighbours
 *    ([fillFromNeighbours]), so that every block has one; [DEFAULT_RIDGE_PERIOD] when no block could be measured.
 *
 * The parameters are for images at 500 ppi; [image] is not modified.
 */
internal fun ridgePeriodField(image: GrayImage, field: OrientationField, mask: ForegroundMask): RidgePeriodField {
    require(field.width == image.width && field.height == image.height) { "Orientation field and image differ in size" }
    require(mask.width == image.width && mask.height == image.height) { "Foreground mask and image differ in size" }
    val periods = FloatArray(mask.columns * mask.rows) { Float.NaN }
    val signature = DoubleArray(SIGNATURE_LENGTH)
    for (row in 0 until mask.rows) for (column in 0 until mask.columns) {
        if (!mask.isForegroundBlock(column, row)) continue
        readSignature(image, field, mask.blockSize, column, row, signature)
        periods[row * mask.columns + column] = fittedPeriod(signature)
    }
    medianOfNeighbours(periods, mask.columns, mask.rows)
    fillFromNeighbours(periods, mask.columns, mask.rows)
    return RidgePeriodField(image.width, image.height, mask.blockSize, periods)
}

/**
 * Signature of block ([column], [row]) of a grid of [size] px blocks, into [signature]: the mean gray value of each
 * line along the ridges, from one side of the window to the other.
 */
private fun readSignature(
    image: GrayImage,
    field: OrientationField,
    size: Int,
    column: Int,
    row: Int,
    signature: DoubleArray,
) {
    val centerX = column * size + (size - 1) / 2.0
    val centerY = row * size + (size - 1) / 2.0
    // The field averages the gradients over about two ridge periods around each pixel, so its orientation in the
    // middle of the block stands for the whole block.
    val angle = field.orientationAt(column * size + size / 2, row * size + size / 2).toDouble()
    // Unit steps along and across the ridges in image coordinates: the angle is counterclockwise as seen on the
    // image, and y points down.
    val alongX = cos(angle)
    val alongY = -sin(angle)
    val acrossX = sin(angle)
    val acrossY = cos(angle)
    for (k in 0 until SIGNATURE_LENGTH) {
        val across = k - (SIGNATURE_LENGTH - 1) / 2.0
        var sum = 0.0
        for (d in 0 until SIGNATURE_WIDTH) {
            val along = d - (SIGNATURE_WIDTH - 1) / 2.0
            // The window reaches 17.2 px from the block centre. Foreground blocks keep one block away from the image
            // border, which leaves 23.5 px on the left and top but only 8.5 px on the right and bottom when the last
            // block column or row is cut short; there the border pixels are repeated, as in the other filters.
            val x = (centerX + across * acrossX + along * alongX).coerceIn(0.0, image.width - 1.0)
            val y = (centerY + across * acrossY + along * alongY).coerceIn(0.0, image.height - 1.0)
            sum += image.bilinear(x, y)
        }
        signature[k] = sum / SIGNATURE_WIDTH
    }
}

/**
 * Period in pixels of the sinusoid that fits [signature] best, or NaN when there is none: the signature is flat, the
 * best frequency of the grid is at one of its ends (the period is outside the accepted range, or there is only a
 * slope), or the sinusoid explains less than [MIN_EXPLAINED_SHARE] of its variance.
 */
private fun fittedPeriod(signature: DoubleArray): Float {
    val mean = signature.average()
    // the squared deviations from the mean, all that a sinusoid could explain
    var totalEnergy = 0.0
    for (value in signature) totalEnergy += (value - mean) * (value - mean)
    if (totalEnergy == 0.0) return Float.NaN
    val lowest = 1 / MAX_RIDGE_PERIOD
    val step = (1 / MIN_RIDGE_PERIOD - lowest) / FREQUENCY_STEPS
    val energy = DoubleArray(FREQUENCY_STEPS + 1) { explainedEnergy(signature, mean, lowest + it * step) }
    var best = 0
    for (i in energy.indices) if (energy[i] > energy[best]) best = i
    if (best == 0 || best == FREQUENCY_STEPS || energy[best] < MIN_EXPLAINED_SHARE * totalEnergy) return Float.NaN
    // Top of the parabola through the best step and its two neighbours, at most half a step away from the best.
    val curvature = energy[best - 1] - 2 * energy[best] + energy[best + 1]
    val offset = if (curvature < 0) 0.5 * (energy[best - 1] - energy[best + 1]) / curvature else 0.0
    return (1 / (lowest + (best + offset) * step)).toFloat()
}

/**
 * Part of the squared deviations of [signature] from its [mean] that a sinusoid of [frequency] cycles per pixel
 * explains, with its amplitude, phase and offset fitted by least squares. Cos and sin are taken without their own
 * means, which fits the offset, and the 2 x 2 normal equations of their weights are solved in closed form.
 */
private fun explainedEnergy(signature: DoubleArray, mean: Double, frequency: Double): Double {
    var sumC = 0.0
    var sumS = 0.0
    var sumCC = 0.0
    var sumCS = 0.0
    var sumSS = 0.0
    var sumXC = 0.0
    var sumXS = 0.0
    for (k in signature.indices) {
        // positions from the middle of the window, which keeps the sums of cos and sin small
        val phase = 2 * PI * frequency * (k - (signature.size - 1) / 2.0)
        val c = cos(phase)
        val s = sin(phase)
        val x = signature[k] - mean
        sumC += c
        sumS += s
        sumCC += c * c
        sumCS += c * s
        sumSS += s * s
        sumXC += x * c
        sumXS += x * s
    }
    val n = signature.size
    val cc = sumCC - sumC * sumC / n
    val cs = sumCS - sumC * sumS / n
    val ss = sumSS - sumS * sumS / n
    val determinant = cc * ss - cs * cs
    if (determinant <= 0.0) return 0.0
    return (ss * sumXC * sumXC - 2 * cs * sumXC * sumXS + cc * sumXS * sumXS) / determinant
}

/**
 * Replaces every measured period (not NaN) by the median of the measured periods of its 3 x 3 neighbourhood, read
 * before this step. A window that crosses a crease, a scar or the edge of a smudge from the ridges beside it can still
 * fit a sinusoid well, with a period several pixels off; its neighbours outvote it. Hong, Wan and Jain smooth the
 * frequencies with a mean instead, which would spread such a period over its neighbours.
 */
private fun medianOfNeighbours(periods: FloatArray, columns: Int, rows: Int) {
    val measured = periods.copyOf()
    val neighbourhood = FloatArray(9)
    for (i in periods.indices) {
        if (measured[i].isNaN()) continue
        var count = 0
        forEachNeighbour(i, columns, rows) { if (!measured[it].isNaN()) neighbourhood[count++] = measured[it] }
        neighbourhood.sort(0, count)
        periods[i] = if (count % 2 == 1) neighbourhood[count / 2] else (neighbourhood[count / 2 - 1] + neighbourhood[count / 2]) / 2
    }
}

/**
 * Gives every block without a period (NaN) the mean of those of its 8 neighbours that have one, in rounds, so that
 * the periods spread from the measured blocks over the blocks between them and out to the image border. Each round
 * reads the periods of the round before, so the result does not depend on the order of the blocks. Without any
 * measured block, every block gets [DEFAULT_RIDGE_PERIOD].
 */
private fun fillFromNeighbours(periods: FloatArray, columns: Int, rows: Int) {
    if (periods.all { it.isNaN() }) {
        periods.fill(DEFAULT_RIDGE_PERIOD)
        return
    }
    while (periods.any { it.isNaN() }) {
        val before = periods.copyOf()
        for (i in periods.indices) {
            if (!before[i].isNaN()) continue
            var sum = 0f
            var count = 0
            forEachNeighbour(i, columns, rows) {
                if (!before[it].isNaN()) {
                    sum += before[it]
                    count++
                }
            }
            if (count > 0) periods[i] = sum / count
        }
    }
}

/** Calls [action] with every block of the 3 x 3 neighbourhood of block [i] that lies on the grid, [i] included. */
private inline fun forEachNeighbour(i: Int, columns: Int, rows: Int, action: (Int) -> Unit) {
    for (row in i / columns - 1..i / columns + 1) for (column in i % columns - 1..i % columns + 1) {
        if (row in 0 until rows && column in 0 until columns) action(row * columns + column)
    }
}
