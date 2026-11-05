// Ported from SourceAFIS for Java, Copyright Robert Važan, Apache License 2.0; see NOTICE.md.
package net.iriscan.sdk.finger.impl.afis

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Image to minutiae, step by step as in SourceAFIS FeatureExtractor. */
internal object FeatureExtractor {
    fun extract(input: DoubleMatrix, dpi: Double): FeatureTemplate {
        val raw = ImageResizer.resize(input, dpi)
        val blocks = BlockMap(raw.width, raw.height, Parameters.BLOCK_SIZE)
        val histogram = LocalHistograms.create(blocks, raw)
        val smoothHistogram = LocalHistograms.smooth(blocks, histogram)
        val mask = SegmentationMask.compute(blocks, histogram)
        val equalized = ImageEqualization.equalize(blocks, raw, smoothHistogram, mask)
        val orientation = BlockOrientations.compute(equalized, mask, blocks)
        val smoothed = OrientedSmoothing.parallel(equalized, orientation, mask, blocks)
        val orthogonal = OrientedSmoothing.orthogonal(smoothed, orientation, mask, blocks)
        val binary = BinarizedImage.binarize(smoothed, orthogonal, mask, blocks)
        val pixelMask = SegmentationMask.pixelwise(mask, blocks)
        BinarizedImage.cleanup(binary, pixelMask)
        val inverted = BinarizedImage.invert(binary, pixelMask)
        val innerMask = SegmentationMask.inner(pixelMask)
        val ridges = Skeletons.create(binary, SkeletonType.RIDGES)
        val valleys = Skeletons.create(inverted, SkeletonType.VALLEYS)
        val minutiae = MinutiaCollector.collect(ridges, valleys)
        InnerMinutiaeFilter.apply(minutiae, innerMask)
        MinutiaCloudFilter.apply(minutiae)
        return FeatureTemplate(raw.size(), TopMinutiaeFilter.apply(minutiae))
    }
}

internal class FeatureTemplate(val size: IntPoint, val minutiae: MutableList<FeatureMinutia>)

internal object ImageResizer {
    private fun resize(input: DoubleMatrix, newWidth: Int, newHeight: Int): DoubleMatrix {
        if (newWidth == input.width && newHeight == input.height) return input
        val output = DoubleMatrix(newWidth, newHeight)
        val scaleX = newWidth / input.width.toDouble()
        val scaleY = newHeight / input.height.toDouble()
        val descaleX = 1 / scaleX
        val descaleY = 1 / scaleY
        for (y in 0 until newHeight) {
            val y1 = y * descaleY
            val y2 = y1 + descaleY
            val y1i = y1.toInt()
            val y2i = min(ceil(y2).toInt(), input.height)
            for (x in 0 until newWidth) {
                val x1 = x * descaleX
                val x2 = x1 + descaleX
                val x1i = x1.toInt()
                val x2i = min(ceil(x2).toInt(), input.width)
                var sum = 0.0
                for (oy in y1i until y2i) {
                    val ry = min(oy + 1.0, y2) - max(oy.toDouble(), y1)
                    for (ox in x1i until x2i) {
                        val rx = min(ox + 1.0, x2) - max(ox.toDouble(), x1)
                        sum += rx * ry * input[ox, oy]
                    }
                }
                output[x, y] = sum * (scaleX * scaleY)
            }
        }
        return output
    }

    fun resize(input: DoubleMatrix, dpi: Double) =
        resize(input, jround(500.0 / dpi * input.width), jround(500.0 / dpi * input.height))
}

internal object LocalHistograms {
    fun create(blocks: BlockMap, image: DoubleMatrix): HistogramCube {
        val histogram = HistogramCube(blocks.primary.blocks, Parameters.HISTOGRAM_DEPTH)
        blocks.primary.blocks.forEach { block ->
            val area = blocks.primary.block(block)
            for (y in area.top() until area.bottom())
                for (x in area.left() until area.right()) {
                    val depth = (image[x, y] * histogram.bins).toInt()
                    histogram.increment(block, histogram.constrain(depth))
                }
        }
        return histogram
    }

    fun smooth(blocks: BlockMap, input: HistogramCube): HistogramCube {
        val around = arrayOf(IntPoint(0, 0), IntPoint(-1, 0), IntPoint(0, -1), IntPoint(-1, -1))
        val output = HistogramCube(blocks.secondary.blocks, input.bins)
        blocks.secondary.blocks.forEach { corner ->
            for (relative in around) {
                val block = corner + relative
                if (blocks.primary.blocks.contains(block))
                    for (i in 0 until input.bins) output.add(corner, i, input[block, i])
            }
        }
        return output
    }
}

internal object SegmentationMask {
    private fun clippedContrast(blocks: BlockMap, histogram: HistogramCube): DoubleMatrix {
        val result = DoubleMatrix(blocks.primary.blocks)
        blocks.primary.blocks.forEach { block ->
            val volume = histogram.sum(block)
            val clipLimit = jround(volume * Parameters.CLIPPED_CONTRAST)
            var accumulator = 0
            var lowerBound = histogram.bins - 1
            for (i in 0 until histogram.bins) {
                accumulator += histogram[block, i]
                if (accumulator > clipLimit) {
                    lowerBound = i
                    break
                }
            }
            accumulator = 0
            var upperBound = 0
            for (i in histogram.bins - 1 downTo 0) {
                accumulator += histogram[block, i]
                if (accumulator > clipLimit) {
                    upperBound = i
                    break
                }
            }
            result[block] = (upperBound - lowerBound) * (1.0 / (histogram.bins - 1))
        }
        return result
    }

    private fun absoluteContrastMask(contrast: DoubleMatrix): BooleanMatrix {
        val result = BooleanMatrix(contrast.size())
        contrast.size().forEach { block -> if (contrast[block] < Parameters.MIN_ABSOLUTE_CONTRAST) result[block] = true }
        return result
    }

    private fun relativeContrastMask(contrast: DoubleMatrix, blocks: BlockMap): BooleanMatrix {
        val sorted = ArrayList<Double>()
        contrast.size().forEach { sorted.add(contrast[it]) }
        sorted.sortDescending()
        val pixelsPerBlock = blocks.pixels.area() / blocks.primary.blocks.area()
        val sampleCount = min(sorted.size, Parameters.RELATIVE_CONTRAST_SAMPLE / pixelsPerBlock)
        val consideredBlocks = max(jround(sampleCount * Parameters.RELATIVE_CONTRAST_PERCENTILE), 1)
        val n = min(consideredBlocks, sorted.size)
        // DoubleStream.average() sums with Kahan compensation. Java 18+ subtracts the compensation at
        // the end, as here; Java 17 adds it. The test images give the same templates either way.
        var sum = 0.0
        var compensation = 0.0
        for (i in 0 until n) {
            val tmp = sorted[i] - compensation
            val velvel = sum + tmp
            compensation = (velvel - sum) - tmp
            sum = velvel
        }
        val limit = (sum - compensation) / n * Parameters.MIN_RELATIVE_CONTRAST
        val result = BooleanMatrix(blocks.primary.blocks)
        blocks.primary.blocks.forEach { block -> if (contrast[block] < limit) result[block] = true }
        return result
    }

    private fun filter(input: BooleanMatrix) = VoteFilter.vote(
        input, null, Parameters.BLOCK_ERRORS_VOTE_RADIUS, Parameters.BLOCK_ERRORS_VOTE_MAJORITY,
        Parameters.BLOCK_ERRORS_VOTE_BORDER_DISTANCE
    )

    fun compute(blocks: BlockMap, histogram: HistogramCube): BooleanMatrix {
        val contrast = clippedContrast(blocks, histogram)
        val mask = absoluteContrastMask(contrast)
        mask.merge(relativeContrastMask(contrast, blocks))
        mask.merge(filter(mask))
        mask.invert()
        mask.merge(filter(mask))
        mask.merge(filter(mask))
        mask.merge(
            VoteFilter.vote(
                mask, null, Parameters.MASK_VOTE_RADIUS, Parameters.MASK_VOTE_MAJORITY,
                Parameters.MASK_VOTE_BORDER_DISTANCE
            )
        )
        return mask
    }

    fun pixelwise(mask: BooleanMatrix, blocks: BlockMap): BooleanMatrix {
        val pixelized = BooleanMatrix(blocks.pixels)
        blocks.primary.blocks.forEach { block ->
            if (mask[block]) blocks.primary.block(block).forEach { pixelized[it] = true }
        }
        return pixelized
    }

    private fun shrink(mask: BooleanMatrix, amount: Int): BooleanMatrix {
        val size = mask.size()
        val shrunk = BooleanMatrix(size)
        for (y in amount until size.y - amount)
            for (x in amount until size.x - amount)
                shrunk[x, y] = mask[x, y - amount] && mask[x, y + amount] && mask[x - amount, y] && mask[x + amount, y]
        return shrunk
    }

    fun inner(outer: BooleanMatrix): BooleanMatrix {
        val size = outer.size()
        var inner = BooleanMatrix(size)
        for (y in 1 until size.y - 1)
            for (x in 1 until size.x - 1)
                inner[x, y] = outer[x, y]
        if (Parameters.INNER_MASK_BORDER_DISTANCE >= 1) inner = shrink(inner, 1)
        var total = 1
        var step = 1
        while (total + step <= Parameters.INNER_MASK_BORDER_DISTANCE) {
            inner = shrink(inner, step)
            total += step
            step *= 2
        }
        if (total < Parameters.INNER_MASK_BORDER_DISTANCE)
            inner = shrink(inner, Parameters.INNER_MASK_BORDER_DISTANCE - total)
        return inner
    }
}

internal object VoteFilter {
    fun vote(input: BooleanMatrix, mask: BooleanMatrix?, radius: Int, majority: Double, borderDistance: Int): BooleanMatrix {
        val size = input.size()
        val rect = IntRect(borderDistance, borderDistance, size.x - 2 * borderDistance, size.y - 2 * borderDistance)
        val thresholds = IntArray(sq(2 * radius + 1) + 1) { ceil(majority * it).toInt() }
        val counts = IntMatrix(size)
        val output = BooleanMatrix(size)
        for (y in rect.top() until rect.bottom()) {
            val superTop = y - radius - 1
            val superBottom = y + radius
            val yMin = max(0, y - radius)
            val yMax = min(size.y - 1, y + radius)
            val yRange = yMax - yMin + 1
            for (x in rect.left() until rect.right()) {
                if (mask != null && !mask[x, y]) continue
                val left = if (x > 0) counts[x - 1, y] else 0
                val top = if (y > 0) counts[x, y - 1] else 0
                val diagonal = if (x > 0 && y > 0) counts[x - 1, y - 1] else 0
                val xMin = max(0, x - radius)
                val xMax = min(size.x - 1, x + radius)
                var ones: Int
                if (left > 0 && top > 0 && diagonal > 0) {
                    ones = top + left - diagonal - 1
                    val superLeft = x - radius - 1
                    val superRight = x + radius
                    if (superLeft >= 0 && superTop >= 0 && input[superLeft, superTop]) ++ones
                    if (superLeft >= 0 && superBottom < size.y && input[superLeft, superBottom]) --ones
                    if (superRight < size.x && superTop >= 0 && input[superRight, superTop]) --ones
                    if (superRight < size.x && superBottom < size.y && input[superRight, superBottom]) ++ones
                } else {
                    ones = 0
                    for (ny in yMin..yMax) for (nx in xMin..xMax) if (input[nx, ny]) ++ones
                }
                counts[x, y] = ones + 1
                if (ones >= thresholds[yRange * (xMax - xMin + 1)]) output[x, y] = true
            }
        }
        return output
    }
}

internal object ImageEqualization {
    fun equalize(blocks: BlockMap, image: DoubleMatrix, histogram: HistogramCube, blockMask: BooleanMatrix): DoubleMatrix {
        val rangeMin = -1.0
        val rangeMax = 1.0
        val rangeSize = rangeMax - rangeMin
        val widthMax = rangeSize / 256 * Parameters.MAX_EQUALIZATION_SCALING
        val widthMin = rangeSize / 256 * Parameters.MIN_EQUALIZATION_SCALING
        val bins = histogram.bins
        val limitedMin = DoubleArray(bins)
        val limitedMax = DoubleArray(bins)
        val dequantized = DoubleArray(bins)
        for (i in 0 until bins) {
            limitedMin[i] = max(i * widthMin + rangeMin, rangeMax - (bins - 1 - i) * widthMax)
            limitedMax[i] = min(i * widthMax + rangeMin, rangeMax - (bins - 1 - i) * widthMin)
            dequantized[i] = i / (bins - 1).toDouble()
        }
        val corners = blocks.secondary.blocks
        val mappings = arrayOfNulls<DoubleArray>(corners.area())
        corners.forEach { corner ->
            val mapping = DoubleArray(bins)
            mappings[corner.y * corners.x + corner.x] = mapping
            if (blockMask.get(corner, false) || blockMask.get(corner.x - 1, corner.y, false) ||
                blockMask.get(corner.x, corner.y - 1, false) || blockMask.get(corner.x - 1, corner.y - 1, false)
            ) {
                val step = rangeSize / histogram.sum(corner)
                var top = rangeMin
                for (i in 0 until bins) {
                    val band = histogram[corner, i] * step
                    var equalized = top + dequantized[i] * band
                    top += band
                    if (equalized < limitedMin[i]) equalized = limitedMin[i]
                    if (equalized > limitedMax[i]) equalized = limitedMax[i]
                    mapping[i] = equalized
                }
            }
        }
        fun mapping(x: Int, y: Int) = mappings[y * corners.x + x]!!
        val result = DoubleMatrix(blocks.pixels)
        blocks.primary.blocks.forEach { block ->
            val area = blocks.primary.block(block)
            if (blockMask[block]) {
                val topleft = mapping(block.x, block.y)
                val topright = mapping(block.x + 1, block.y)
                val bottomleft = mapping(block.x, block.y + 1)
                val bottomright = mapping(block.x + 1, block.y + 1)
                for (y in area.top() until area.bottom())
                    for (x in area.left() until area.right()) {
                        val depth = histogram.constrain((image[x, y] * bins).toInt())
                        val rx = (x - area.x + 0.5) / area.width
                        val ry = (y - area.y + 0.5) / area.height
                        result[x, y] = interpolate(bottomleft[depth], bottomright[depth], topleft[depth], topright[depth], rx, ry)
                    }
            } else {
                for (y in area.top() until area.bottom())
                    for (x in area.left() until area.right())
                        result[x, y] = -1.0
            }
        }
        return result
    }
}

internal object PixelwiseOrientations {
    private class ConsideredOrientation(val offset: IntPoint, val orientation: DoublePoint)

    private class OrientationRandom {
        // Java computes PRIME * PRIME * PRIME in 32-bit int and then widens it; keep the overflow.
        private var state: Long = (PRIME * PRIME * PRIME).toLong()

        fun next(): Double {
            state *= PRIME
            return ((state and MASK.toLong()) + 0.5) * SCALING
        }

        companion object {
            const val PRIME = 1610612741
            const val BITS = 30
            const val MASK = (1 shl BITS) - 1
            const val SCALING = 1.0 / (1 shl BITS)
        }
    }

    private val plan: Array<Array<ConsideredOrientation>> by lazy {
        val random = OrientationRandom()
        Array(Parameters.ORIENTATION_SPLIT) {
            val orientations = ArrayList<ConsideredOrientation>()
            for (j in 0 until Parameters.ORIENTATIONS_CHECKED) {
                var offset: IntPoint
                do {
                    val angle = random.next() * PI
                    val distance = interpolateExponential(
                        Parameters.MIN_ORIENTATION_RADIUS, Parameters.MAX_ORIENTATION_RADIUS, random.next()
                    )
                    offset = (DoubleAngle.toVector(angle) * distance).round()
                } while (offset == IntPoint.ZERO || offset.y < 0 || orientations.any { it.offset == offset })
                val orientation = DoubleAngle.toVector(
                    DoubleAngle.add(DoubleAngle.toOrientation(DoubleAngle.atan(offset.toDouble())), PI)
                )
                orientations.add(ConsideredOrientation(offset, orientation))
            }
            orientations.toTypedArray()
        }
    }

    private fun maskRange(mask: BooleanMatrix, y: Int): IntRange {
        var first = -1
        var last = -1
        for (x in 0 until mask.width)
            if (mask[x, y]) {
                last = x
                if (first < 0) first = x
            }
        return if (first >= 0) IntRange(first, last + 1) else IntRange.ZERO
    }

    fun compute(input: DoubleMatrix, mask: BooleanMatrix, blocks: BlockMap): DoublePointMatrix {
        val neighbors = plan
        val orientation = DoublePointMatrix(input.size())
        for (blockY in 0 until blocks.primary.blocks.y) {
            val maskRange = maskRange(mask, blockY)
            if (maskRange.length() <= 0) continue
            val validXRange = IntRange(
                blocks.primary.block(maskRange.start, blockY).left(),
                blocks.primary.block(maskRange.end - 1, blockY).right()
            )
            for (y in blocks.primary.block(0, blockY).top() until blocks.primary.block(0, blockY).bottom()) {
                for (neighbor in neighbors[y % neighbors.size]) {
                    val radius = max(abs(neighbor.offset.x), abs(neighbor.offset.y))
                    if (y - radius >= 0 && y + radius < input.height) {
                        val xStart = max(radius, validXRange.start)
                        val xEnd = min(input.width - radius, validXRange.end)
                        for (x in xStart until xEnd) {
                            val before = input[x - neighbor.offset.x, y - neighbor.offset.y]
                            val at = input[x, y]
                            val after = input[x + neighbor.offset.x, y + neighbor.offset.y]
                            val strength = at - max(before, after)
                            if (strength > 0)
                                orientation.add(x, y, neighbor.orientation.x * strength, neighbor.orientation.y * strength)
                        }
                    }
                }
            }
        }
        return orientation
    }
}

internal object BlockOrientations {
    private fun aggregate(orientation: DoublePointMatrix, blocks: BlockMap, mask: BooleanMatrix): DoublePointMatrix {
        val sums = DoublePointMatrix(blocks.primary.blocks)
        blocks.primary.blocks.forEach { block ->
            if (mask[block]) {
                val area = blocks.primary.block(block)
                for (y in area.top() until area.bottom())
                    for (x in area.left() until area.right())
                        sums.add(block, orientation[x, y])
            }
        }
        return sums
    }

    private fun smooth(orientation: DoublePointMatrix, mask: BooleanMatrix): DoublePointMatrix {
        val size = mask.size()
        val smoothed = DoublePointMatrix(size)
        size.forEach { block ->
            if (mask[block]) {
                val neighbors = IntRect.around(block, Parameters.ORIENTATION_SMOOTHING_RADIUS).intersect(IntRect(size))
                for (ny in neighbors.top() until neighbors.bottom())
                    for (nx in neighbors.left() until neighbors.right())
                        if (mask[nx, ny]) smoothed.add(block, orientation[nx, ny])
            }
        }
        return smoothed
    }

    fun compute(image: DoubleMatrix, mask: BooleanMatrix, blocks: BlockMap): DoubleMatrix {
        val accumulated = PixelwiseOrientations.compute(image, mask, blocks)
        val smooth = smooth(aggregate(accumulated, blocks, mask), mask)
        val size = mask.size()
        val angles = DoubleMatrix(size)
        size.forEach { block -> if (mask[block]) angles[block] = DoubleAngle.atan(smooth[block]) }
        return angles
    }
}

internal object OrientedSmoothing {
    private fun lines(resolution: Int, radius: Int, step: Double): Array<Array<IntPoint>> = Array(resolution) { orientationIndex ->
        val line = ArrayList<IntPoint>()
        line.add(IntPoint.ZERO)
        val direction = DoubleAngle.toVector(DoubleAngle.fromOrientation(DoubleAngle.bucketCenter(orientationIndex, resolution)))
        var r = radius.toDouble()
        while (r >= 0.5) {
            val sample = (direction * r).round()
            if (!line.contains(sample)) {
                line.add(sample)
                line.add(sample.negate())
            }
            r /= step
        }
        line.toTypedArray()
    }

    private fun smooth(
        input: DoubleMatrix, orientation: DoubleMatrix, mask: BooleanMatrix, blocks: BlockMap,
        angle: Double, lines: Array<Array<IntPoint>>
    ): DoubleMatrix {
        val output = DoubleMatrix(input.size())
        val all = IntRect(blocks.pixels)
        blocks.primary.blocks.forEach { block ->
            if (mask[block]) {
                val line = lines[DoubleAngle.quantize(DoubleAngle.add(orientation[block], angle), lines.size)]
                for (linePoint in line) {
                    val source = blocks.primary.block(block).move(linePoint).intersect(all)
                    val target = source.move(linePoint.negate())
                    for (y in target.top() until target.bottom())
                        for (x in target.left() until target.right())
                            output.add(x, y, input[x + linePoint.x, y + linePoint.y])
                }
                val blockArea = blocks.primary.block(block)
                for (y in blockArea.top() until blockArea.bottom())
                    for (x in blockArea.left() until blockArea.right())
                        output.multiply(x, y, 1.0 / line.size)
            }
        }
        return output
    }

    fun parallel(input: DoubleMatrix, orientation: DoubleMatrix, mask: BooleanMatrix, blocks: BlockMap): DoubleMatrix {
        val lines = lines(
            Parameters.PARALLEL_SMOOTHING_RESOLUTION, Parameters.PARALLEL_SMOOTHING_RADIUS, Parameters.PARALLEL_SMOOTHING_STEP
        )
        return smooth(input, orientation, mask, blocks, 0.0, lines)
    }

    fun orthogonal(input: DoubleMatrix, orientation: DoubleMatrix, mask: BooleanMatrix, blocks: BlockMap): DoubleMatrix {
        val lines = lines(
            Parameters.ORTHOGONAL_SMOOTHING_RESOLUTION, Parameters.ORTHOGONAL_SMOOTHING_RADIUS, Parameters.ORTHOGONAL_SMOOTHING_STEP
        )
        return smooth(input, orientation, mask, blocks, PI, lines)
    }
}

internal object BinarizedImage {
    fun binarize(input: DoubleMatrix, baseline: DoubleMatrix, mask: BooleanMatrix, blocks: BlockMap): BooleanMatrix {
        val binarized = BooleanMatrix(input.size())
        blocks.primary.blocks.forEach { block ->
            if (mask[block]) {
                val rect = blocks.primary.block(block)
                for (y in rect.top() until rect.bottom())
                    for (x in rect.left() until rect.right())
                        if (input[x, y] - baseline[x, y] > 0) binarized[x, y] = true
            }
        }
        return binarized
    }

    private fun removeCrosses(input: BooleanMatrix) {
        val size = input.size()
        var any = true
        while (any) {
            any = false
            for (y in 0 until size.y - 1)
                for (x in 0 until size.x - 1)
                    if (input[x, y] && input[x + 1, y + 1] && !input[x, y + 1] && !input[x + 1, y] ||
                        input[x, y + 1] && input[x + 1, y] && !input[x, y] && !input[x + 1, y + 1]
                    ) {
                        input[x, y] = false
                        input[x, y + 1] = false
                        input[x + 1, y] = false
                        input[x + 1, y + 1] = false
                        any = true
                    }
        }
    }

    fun cleanup(binary: BooleanMatrix, mask: BooleanMatrix) {
        val size = binary.size()
        val inverted = BooleanMatrix(binary)
        inverted.invert()
        val islands = VoteFilter.vote(
            inverted, mask, Parameters.BINARIZED_VOTE_RADIUS, Parameters.BINARIZED_VOTE_MAJORITY,
            Parameters.BINARIZED_VOTE_BORDER_DISTANCE
        )
        val holes = VoteFilter.vote(
            binary, mask, Parameters.BINARIZED_VOTE_RADIUS, Parameters.BINARIZED_VOTE_MAJORITY,
            Parameters.BINARIZED_VOTE_BORDER_DISTANCE
        )
        for (y in 0 until size.y)
            for (x in 0 until size.x)
                binary[x, y] = binary[x, y] && !islands[x, y] || holes[x, y]
        removeCrosses(binary)
    }

    fun invert(binary: BooleanMatrix, mask: BooleanMatrix): BooleanMatrix {
        val size = binary.size()
        val inverted = BooleanMatrix(size)
        for (y in 0 until size.y)
            for (x in 0 until size.x)
                inverted[x, y] = !binary[x, y] && mask[x, y]
        return inverted
    }
}
