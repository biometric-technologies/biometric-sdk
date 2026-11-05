package net.iriscan.sdk.finger.impl

import net.iriscan.sdk.iris.impl.GrayImage
import net.iriscan.sdk.iris.impl.quantile
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Side of the square blocks of the foreground mask, pixels at 500 ppi. A block spans about two ridge
 * periods: enough pixels (256) for a stable contrast and mean coherence, few enough to follow the outline
 * of the print.
 */
private const val MASK_BLOCK_SIZE = 16

/**
 * Lowest mean coherence of a foreground block. Ridges give 0.55 to 1 even under strong noise, and 0.4 in
 * the block that holds the centre of a whorl. Blocks of pure noise average 0.12 and pass 0.3 rarely and
 * one at a time, so the opening removes them; at 0.2, 6% of them pass and they start to form groups.
 */
private const val MIN_BLOCK_COHERENCE = 0.3f

/**
 * Lowest contrast of a foreground block, as a fraction of a reference contrast, so that the threshold
 * follows the brightness and gain of the sensor. Not Otsu's split of the contrasts in two classes: when
 * the print fills the image there is no background to split off, and Otsu drops the part of the print
 * where the finger pressed less; on a bright background it can put faint ridges in the class of the
 * background. With a quarter, ridges down to a quarter of the reference stay, and a background whose
 * contrast is only sensor noise does not.
 */
private const val MIN_CONTRAST_FRACTION = 0.25f

/**
 * Quantile of the block contrasts taken as the reference: a block of the print or of its edge, as long as
 * they cover a tenth of the image. On a background much brighter than the print, the blocks across the
 * print's edge, which add that step to their ridges, set it.
 */
private const val CONTRAST_REFERENCE_QUANTILE = 0.9

/**
 * Foreground of a fingerprint image: the blocks that hold the print, where the later steps look for
 * ridges and minutiae. Blocks are [blockSize] px squares from the top left corner of the image, so those
 * of the last column and row may be cut by the image border; blocks along the image border are never
 * foreground (see [foregroundMask]).
 */
internal class ForegroundMask(
    val width: Int,
    val height: Int,
    val blockSize: Int,
    /** Foreground flag of every block, row by row. */
    private val blocks: BooleanArray,
) {
    val columns: Int = (width + blockSize - 1) / blockSize
    val rows: Int = (height + blockSize - 1) / blockSize

    /** Number of foreground blocks: 0 when the image holds no usable print. */
    val blockCount: Int = blocks.count { it }

    fun isForegroundBlock(column: Int, row: Int): Boolean = blocks[row * columns + column]

    /** True when pixel (x, y) of the image lies in a foreground block. */
    fun contains(x: Int, y: Int): Boolean = blocks[(y / blockSize) * columns + x / blockSize]
}

/**
 * Foreground mask of [image], from its block contrast and the coherence of its orientation [field].
 *
 * 1. A block is a candidate when its contrast, the standard deviation of its gray values, is above
 *    [MIN_CONTRAST_FRACTION] of the reference contrast and its mean coherence at least
 *    [MIN_BLOCK_COHERENCE]. Each test covers what the other misses: next to the print, a flat
 *    background has the coherent orientation of the print's edge but no contrast; noise has contrast
 *    but no coherence.
 * 2. Closing, then opening, by one block: gaps of up to two blocks across the print, such as a crease,
 *    are closed; then groups and strips less than 3 blocks wide are removed, so that a strip does not
 *    join something else to the print.
 * 3. Holes filled: cores, deltas, scars and smudges inside the print belong to the finger. Whether
 *    minutiae there are reliable is for their quality to tell, not for the mask.
 * 4. Largest group kept: one finger per image.
 * 5. Eroded by one block: along the edge of the print the background bends the orientation and the
 *    filters, and most false minutiae appear there.
 *
 * Everything outside the image counts as background, so the erosion also keeps the mask one block away
 * from the image border, where the filters see the clamped border pixels instead of ridges. [image] is
 * not modified.
 */
internal fun foregroundMask(image: GrayImage, field: OrientationField): ForegroundMask {
    require(field.width == image.width && field.height == image.height) { "Orientation field and image differ in size" }
    val blockSize = MASK_BLOCK_SIZE
    val columns = (image.width + blockSize - 1) / blockSize
    val rows = (image.height + blockSize - 1) / blockSize
    val contrast = FloatArray(columns * rows)
    val coherence = FloatArray(columns * rows)
    for (row in 0 until rows) for (column in 0 until columns) {
        val x0 = column * blockSize
        val y0 = row * blockSize
        val x1 = min(x0 + blockSize, image.width)
        val y1 = min(y0 + blockSize, image.height)
        val n = (x1 - x0) * (y1 - y0)
        var sum = 0.0
        var coherenceSum = 0.0
        for (y in y0 until y1) for (x in x0 until x1) {
            sum += image[x, y]
            coherenceSum += field.coherenceAt(x, y)
        }
        // a second pass for the deviations, so that a flat block has a contrast of exactly 0
        val mean = sum / n
        var squares = 0.0
        for (y in y0 until y1) for (x in x0 until x1) squares += (image[x, y] - mean) * (image[x, y] - mean)
        contrast[row * columns + column] = sqrt(squares / n).toFloat()
        coherence[row * columns + column] = (coherenceSum / n).toFloat()
    }
    val minContrast = MIN_CONTRAST_FRACTION * quantile(contrast, CONTRAST_REFERENCE_QUANTILE)
    var blocks = BooleanArray(columns * rows) { contrast[it] > minContrast && coherence[it] >= MIN_BLOCK_COHERENCE }
    blocks = close(blocks, columns, rows)
    blocks = morph(morph(blocks, columns, rows, erode = true), columns, rows, erode = false)
    blocks = fillHoles(blocks, columns, rows)
    blocks = largestGroup(blocks, columns, rows)
    blocks = morph(blocks, columns, rows, erode = true)
    return ForegroundMask(image.width, image.height, blockSize, blocks)
}

/**
 * Erosion (a block stays when its whole 3 x 3 neighbourhood is foreground) or dilation (a block becomes
 * foreground when some block of it is) of a grid of [columns] x [rows] blocks. Blocks outside the grid
 * count as background.
 */
private fun morph(blocks: BooleanArray, columns: Int, rows: Int, erode: Boolean): BooleanArray =
    BooleanArray(blocks.size) { i ->
        var result = erode
        for (row in i / columns - 1..i / columns + 1) for (column in i % columns - 1..i % columns + 1) {
            val set = row in 0 until rows && column in 0 until columns && blocks[row * columns + column]
            result = if (erode) result && set else result || set
        }
        result
    }

/**
 * Closing by one block, computed on a grid with one more ring of background blocks, so that the
 * dilation of a block on the image border reaches past the border and the erosion keeps that block. On
 * the grid alone, the erosion would remove every block along the border; counting blocks outside as
 * foreground instead, it would add background blocks along the border next to a round print, and then
 * blocks that the print's edge crosses would survive the final erosion.
 */
private fun close(blocks: BooleanArray, columns: Int, rows: Int): BooleanArray {
    val wide = columns + 2
    val padded = BooleanArray(wide * (rows + 2))
    for (i in blocks.indices) padded[(i / columns + 1) * wide + i % columns + 1] = blocks[i]
    val closed = morph(morph(padded, wide, rows + 2, erode = false), wide, rows + 2, erode = true)
    return BooleanArray(blocks.size) { closed[(it / columns + 1) * wide + it % columns + 1] }
}

/** Background blocks that are not connected to the image border (4-connected) become foreground. */
private fun fillHoles(blocks: BooleanArray, columns: Int, rows: Int): BooleanArray {
    val label = IntArray(blocks.size)
    for (i in blocks.indices) {
        val onBorder = i % columns == 0 || i % columns == columns - 1 || i / columns == 0 || i / columns == rows - 1
        if (onBorder && !blocks[i] && label[i] == 0) flood(blocks, columns, rows, i, diagonal = false, label, 1)
    }
    return BooleanArray(blocks.size) { blocks[it] || label[it] == 0 }
}

/** The largest 8-connected group of foreground blocks; of two as large, the first in row order. */
private fun largestGroup(blocks: BooleanArray, columns: Int, rows: Int): BooleanArray {
    val label = IntArray(blocks.size)
    var groups = 0
    var largest = 0
    var largestSize = 0
    for (i in blocks.indices) {
        if (!blocks[i] || label[i] != 0) continue
        val size = flood(blocks, columns, rows, i, diagonal = true, label, ++groups)
        if (size > largestSize) {
            largest = groups
            largestSize = size
        }
    }
    return BooleanArray(blocks.size) { blocks[it] && label[it] == largest }
}

/**
 * Labels with [id] the blocks that have the flag of block [first] and are connected to it, through
 * blocks of that flag still unlabelled (0); 8-connected when [diagonal], else 4-connected. Returns their
 * number. Foreground groups are 8-connected and background ones 4-connected, so that a ring of blocks
 * touching only at corners still encloses a hole.
 */
private fun flood(
    blocks: BooleanArray,
    columns: Int,
    rows: Int,
    first: Int,
    diagonal: Boolean,
    label: IntArray,
    id: Int,
): Int {
    val queue = IntArray(blocks.size)
    var head = 0
    var tail = 0
    label[first] = id
    queue[tail++] = first
    while (head < tail) {
        val i = queue[head++]
        for (row in i / columns - 1..i / columns + 1) for (column in i % columns - 1..i % columns + 1) {
            if (row !in 0 until rows || column !in 0 until columns) continue
            if (!diagonal && row != i / columns && column != i % columns) continue
            val j = row * columns + column
            if (blocks[j] == blocks[first] && label[j] == 0) {
                label[j] = id
                queue[tail++] = j
            }
        }
    }
    return tail
}
