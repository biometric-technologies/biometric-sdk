package net.iriscan.sdk.face.impl.haar

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** 8-bit grayscale image: [pixels] holds width * height values 0..255, row by row. */
internal class GrayPlane(val width: Int, val height: Int, val pixels: IntArray) {
    init {
        require(width > 0 && height > 0 && pixels.size == width * height) { "Plane ${width}x$height with ${pixels.size} pixels" }
    }

    operator fun get(x: Int, y: Int): Int = pixels[y * width + x]

    /** The part of the plane inside the given rectangle, which must lie within the plane. */
    fun crop(x: Int, y: Int, w: Int, h: Int): GrayPlane {
        require(x >= 0 && y >= 0 && w > 0 && h > 0 && x + w <= width && y + h <= height) { "Crop $x,$y ${w}x$h outside ${width}x$height" }
        val out = IntArray(w * h)
        for (row in 0 until h) pixels.copyInto(out, row * w, (y + row) * width + x, (y + row) * width + x + w)
        return GrayPlane(w, h, out)
    }
}

/** Detected object: top-left corner and size in pixels of the searched image. */
internal data class Box(val x: Int, val y: Int, val width: Int, val height: Int) {
    val area: Int get() = width * height
}

/**
 * Finds objects with a [HaarCascade] at all positions and sizes, the way OpenCV's
 * `CascadeClassifier::detectMultiScale` does, so that the cascades trained for OpenCV give the
 * same detections without OpenCV. The steps and their OpenCV counterparts
 * (modules/objdetect/src/cascadedetect.cpp):
 *
 * 1. **Scales.** The cascade window has a fixed size (20 x 20). To find bigger faces the image is
 *    shrunk instead: factor 1, [scaleFactor], [scaleFactor]^2 ... For each factor the image is
 *    resized to size / factor and searched with the fixed window, which corresponds to a window of
 *    20 * factor pixels in the original image. Factors whose window is outside [minSize, maxSize]
 *    are skipped; the loop stops when the shrunken image no longer exceeds the window.
 * 2. **Positions.** The window moves in steps of 2 pixels (1 pixel once the factor is above 2,
 *    where a pixel of the shrunken image is already large), and one extra step after a window
 *    that fails the very first stage, since its neighbor is then most likely empty too.
 * 3. **Normalization.** Each window is judged on its contrast, not its brightness: feature values
 *    are divided by `area * standard deviation` of the window (without its 1-pixel border), and
 *    windows with a standard deviation below [MIN_WINDOW_STD_DEV] are rejected outright, as flat
 *    patches (walls, sky) never contain a face.
 * 4. **Cascade.** The window runs through the stages (see [HaarCascade]) and is a hit if it passes
 *    all of them.
 * 5. **Grouping.** A real face is hit at many nearby positions and scales, a false alarm usually
 *    only at a few. Hits are grouped by similarity ([groupBoxes]); groups with no more than
 *    `minNeighbors` hits are dropped and each remaining group gives its average box.
 *
 * All arithmetic is plain Kotlin, so the detector runs on every platform the SDK targets.
 */
internal class HaarDetector(private val cascade: HaarCascade) {

    /**
     * Objects in [image], largest first is not guaranteed; callers usually take the largest box.
     *
     * @param scaleFactor size ratio between consecutive scales; 1.1 tries sizes 10% apart.
     * @param minNeighbors hits a group needs beyond this number to be reported (3 is OpenCV's default).
     * @param minSize smallest object side in pixels.
     * @param maxSize largest object side in pixels.
     */
    fun detect(
        image: GrayPlane,
        scaleFactor: Double = DEFAULT_SCALE_FACTOR,
        minNeighbors: Int = DEFAULT_MIN_NEIGHBORS,
        minSize: Int = 0,
        maxSize: Int = Int.MAX_VALUE,
    ): List<Box> {
        require(scaleFactor > 1.0) { "scaleFactor must be above 1, got $scaleFactor" }
        val hits = ArrayList<Box>()
        var factor = 1.0
        while (true) {
            val windowW = (cascade.windowWidth * factor).roundToInt()
            val windowH = (cascade.windowHeight * factor).roundToInt()
            val scaledW = (image.width / factor).roundToInt()
            val scaledH = (image.height / factor).roundToInt()
            // positions per row and column; OpenCV stops as soon as there is no room for the window
            val rangeX = scaledW - cascade.windowWidth
            val rangeY = scaledH - cascade.windowHeight
            if (rangeX <= 0 || rangeY <= 0) break
            if (windowW > maxSize || windowH > maxSize) break
            if (windowW >= minSize && windowH >= minSize) {
                val scaled = if (scaledW == image.width && scaledH == image.height) image else resizeBilinear(image, scaledW, scaledH)
                searchScale(IntegralImage(scaled), factor, rangeX, rangeY, windowW, windowH, hits)
            }
            factor *= scaleFactor
        }
        return groupBoxes(hits, minNeighbors, GROUP_EPS)
    }

    private fun searchScale(ii: IntegralImage, factor: Double, rangeX: Int, rangeY: Int, windowW: Int, windowH: Int, hits: MutableList<Box>) {
        val step = if (factor > 2.0) 1 else 2
        var y = 0
        while (y < rangeY) {
            var x = 0
            while (x < rangeX) {
                val result = evaluate(ii, x, y)
                if (result > 0) hits += Box((x * factor).roundToInt(), (y * factor).roundToInt(), windowW, windowH)
                // failed the first stage: skip the neighbor as well
                if (result == 0) x += step
                x += step
            }
            y += step
        }
    }

    /**
     * Runs the cascade on the window at ([x], [y]). Returns 1 when all stages pass, otherwise
     * -(index of the failed stage), so 0 means the first stage failed; -1 also stands for a window
     * rejected for low contrast.
     */
    private fun evaluate(ii: IntegralImage, x: Int, y: Int): Int {
        // normalization rectangle: the window without its 1-pixel border, as in OpenCV
        val nx = x + 1
        val ny = y + 1
        val nw = cascade.windowWidth - 2
        val nh = cascade.windowHeight - 2
        val area = (nw * nh).toDouble()
        val sum = ii.sum(nx, ny, nw, nh).toDouble()
        val squares = ii.squareSum(nx, ny, nw, nh)
        // area^2 * variance; its square root is area * standard deviation
        val spread = area * squares - sum * sum
        if (spread <= 0.0) return -1
        val norm = sqrt(spread)
        if (norm <= area * MIN_WINDOW_STD_DEV) return -1
        val scale = 1.0 / norm

        cascade.stages.forEachIndexed { stageIndex, stage ->
            var votes = 0.0
            for (classifier in stage.classifiers) {
                var nodeIndex = 0
                while (true) {
                    val node = classifier.nodes[nodeIndex]
                    val value = featureValue(ii, cascade.features[node.featureIndex], x, y) * scale
                    val child = if (value < node.threshold) node.left else node.right
                    if (child <= 0) {
                        votes += classifier.leaves[-child]
                        break
                    }
                    nodeIndex = child
                }
            }
            if (votes < stage.threshold) return -stageIndex
        }
        return 1
    }

    private fun featureValue(ii: IntegralImage, feature: HaarCascade.Feature, x: Int, y: Int): Double {
        var value = 0.0
        for (r in feature.rects) value += r.weight * ii.sum(x + r.x, y + r.y, r.width, r.height)
        return value
    }

    companion object {
        const val DEFAULT_SCALE_FACTOR = 1.1
        const val DEFAULT_MIN_NEIGHBORS = 3

        /**
         * Windows whose gray levels vary less than this (standard deviation, 0..255 scale) are
         * not searched. OpenCV's value: it rejects windows where area / (area * std) >= 0.1.
         */
        const val MIN_WINDOW_STD_DEV = 10.0

        /** Similarity tolerance for grouping hits, as in detectMultiScale: 20% of the box size. */
        const val GROUP_EPS = 0.2
    }
}

/**
 * Summed-area table: the sum of any rectangle in four reads. Entry (x, y) holds the sum of all
 * pixels above and to the left of (x, y), with an extra zero row and column at the start, so the
 * table is (width + 1) x (height + 1). Squares are summed in doubles, as their totals exceed Int.
 */
internal class IntegralImage(image: GrayPlane) {
    private val stride = image.width + 1
    private val sums = IntArray(stride * (image.height + 1))
    private val squares = DoubleArray(stride * (image.height + 1))

    init {
        for (y in 0 until image.height) {
            var rowSum = 0
            var rowSquares = 0.0
            for (x in 0 until image.width) {
                val v = image[x, y]
                rowSum += v
                rowSquares += (v * v).toDouble()
                val i = (y + 1) * stride + x + 1
                sums[i] = sums[i - stride] + rowSum
                squares[i] = squares[i - stride] + rowSquares
            }
        }
    }

    /** Sum of the pixels in the rectangle with corner ([x], [y]) and size [w] x [h]. */
    fun sum(x: Int, y: Int, w: Int, h: Int): Int {
        val top = y * stride
        val bottom = (y + h) * stride
        return sums[bottom + x + w] - sums[top + x + w] - sums[bottom + x] + sums[top + x]
    }

    /** Sum of the squared pixels in the rectangle. */
    fun squareSum(x: Int, y: Int, w: Int, h: Int): Double {
        val top = y * stride
        val bottom = (y + h) * stride
        return squares[bottom + x + w] - squares[top + x + w] - squares[bottom + x] + squares[top + x]
    }
}

/**
 * Bilinear resize with pixel centers aligned (source coordinate = (x + 0.5) * scale - 0.5), the
 * mapping OpenCV's INTER_LINEAR uses.
 */
internal fun resizeBilinear(image: GrayPlane, width: Int, height: Int): GrayPlane {
    val scaleX = image.width.toDouble() / width
    val scaleY = image.height.toDouble() / height
    val out = IntArray(width * height)
    for (y in 0 until height) {
        val sy = ((y + 0.5) * scaleY - 0.5).coerceIn(0.0, (image.height - 1).toDouble())
        val y0 = sy.toInt()
        val y1 = min(y0 + 1, image.height - 1)
        val fy = sy - y0
        for (x in 0 until width) {
            val sx = ((x + 0.5) * scaleX - 0.5).coerceIn(0.0, (image.width - 1).toDouble())
            val x0 = sx.toInt()
            val x1 = min(x0 + 1, image.width - 1)
            val fx = sx - x0
            val top = image[x0, y0] * (1 - fx) + image[x1, y0] * fx
            val bottom = image[x0, y1] * (1 - fx) + image[x1, y1] * fx
            out[y * width + x] = (top * (1 - fy) + bottom * fy).roundToInt()
        }
    }
    return GrayPlane(width, height, out)
}

/**
 * Histogram equalization, as OpenCV's `equalizeHist`: spreads the gray levels over 0..255 so
 * that the detector sees the same contrast in dark and bright photos.
 */
internal fun equalizeHistogram(image: GrayPlane): GrayPlane {
    val histogram = IntArray(256)
    for (v in image.pixels) histogram[v.coerceIn(0, 255)]++
    val total = image.pixels.size
    var first = 0
    while (histogram[first] == 0) first++
    // a single gray level: nothing to spread
    if (histogram[first] == total) return GrayPlane(image.width, image.height, IntArray(total) { first })
    val scale = 255.0 / (total - histogram[first])
    val lookup = IntArray(256)
    var cumulative = 0
    for (level in first + 1 until 256) {
        cumulative += histogram[level]
        lookup[level] = (cumulative * scale).roundToInt().coerceIn(0, 255)
    }
    return GrayPlane(image.width, image.height, IntArray(total) { lookup[image.pixels[it].coerceIn(0, 255)] })
}

/**
 * Merges overlapping detections, as OpenCV's `groupRectangles`:
 *
 * 1. Boxes are put in one group when they are similar: every edge within [eps] times the mean of
 *    their smaller width and smaller height (transitively, so chains of similar boxes join).
 * 2. Groups with at most [minNeighbors] boxes are dropped; the others become their average box.
 * 3. A group's box is dropped when it lies inside the box of another group (with [eps] slack)
 *    that has clearly more support: more than max(3, its own count) boxes, or when its own group
 *    has fewer than 3. This removes small boxes on a face's features inside the face box.
 *
 * With [minNeighbors] <= 0 the boxes are returned unchanged.
 */
internal fun groupBoxes(boxes: List<Box>, minNeighbors: Int, eps: Double): List<Box> {
    if (minNeighbors <= 0 || boxes.isEmpty()) return boxes
    val parent = IntArray(boxes.size) { it }
    fun root(i: Int): Int {
        var r = i
        while (parent[r] != r) r = parent[r]
        var j = i
        while (parent[j] != r) { val next = parent[j]; parent[j] = r; j = next }
        return r
    }
    for (i in boxes.indices) for (j in i + 1 until boxes.size) {
        if (similar(boxes[i], boxes[j], eps)) {
            val a = root(i)
            val b = root(j)
            if (a != b) parent[b] = a
        }
    }
    val groups = boxes.indices.groupBy { root(it) }.values.toList()
    val counts = groups.map { it.size }
    val averages = groups.map { members ->
        val s = 1.0 / members.size
        Box(
            (members.sumOf { boxes[it].x } * s).roundToInt(),
            (members.sumOf { boxes[it].y } * s).roundToInt(),
            (members.sumOf { boxes[it].width } * s).roundToInt(),
            (members.sumOf { boxes[it].height } * s).roundToInt(),
        )
    }
    val result = ArrayList<Box>()
    for (i in groups.indices) {
        val n1 = counts[i]
        if (n1 <= minNeighbors) continue
        val r1 = averages[i]
        val inside = groups.indices.any { j ->
            val n2 = counts[j]
            if (j == i || n2 <= minNeighbors) return@any false
            val r2 = averages[j]
            val dx = (r2.width * eps).roundToInt()
            val dy = (r2.height * eps).roundToInt()
            r1.x >= r2.x - dx && r1.y >= r2.y - dy &&
                r1.x + r1.width <= r2.x + r2.width + dx && r1.y + r1.height <= r2.y + r2.height + dy &&
                (n2 > max(3, n1) || n1 < 3)
        }
        if (!inside) result += r1
    }
    return result
}

private fun similar(a: Box, b: Box, eps: Double): Boolean {
    val delta = eps * (min(a.width, b.width) + min(a.height, b.height)) * 0.5
    return abs(a.x - b.x) <= delta && abs(a.y - b.y) <= delta &&
        abs(a.x + a.width - b.x - b.width) <= delta && abs(a.y + a.height - b.y - b.height) <= delta
}
