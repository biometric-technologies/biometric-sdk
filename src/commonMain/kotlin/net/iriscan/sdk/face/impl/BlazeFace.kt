package net.iriscan.sdk.face.impl

import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.image.ImageColorType
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A face found by the BlazeFace detector, in pixels of the source image.
 *
 * Keypoints, as the model returns them: 0 right eye, 1 left eye (the person's right and left,
 * so on an unmirrored photo the right eye is on the image's left), 2 nose tip, 3 mouth center,
 * 4 right ear tragion, 5 left ear tragion.
 */
internal class FaceDetection(
    val score: Float,
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
    val keypointsX: DoubleArray,
    val keypointsY: DoubleArray,
) {
    val width: Double get() = right - left
    val height: Double get() = bottom - top
}

/**
 * Pre- and post-processing for MediaPipe's BlazeFace short-range face detector
 * (`face_detection_short_range.tflite`, Apache License 2.0): a 128 x 128 RGB input, and for each of
 * 896 fixed anchors a score and 16 numbers (box center, size and six keypoints).
 *
 * The parameters are those of MediaPipe's own graph for this model
 * (`mediapipe/modules/face_detection/face_detection_short_range.pbtxt`), so that the boxes match what
 * MediaPipe returns. The model itself runs in the platform's TensorFlow Lite.
 */
internal object BlazeFace {
    const val INPUT_SIZE = 128
    const val NUM_ANCHORS = 896
    const val VALUES_PER_ANCHOR = 16
    const val NUM_KEYPOINTS = 6

    /** MediaPipe's min_score_thresh for this model. */
    const val MIN_SCORE = 0.5f

    /** Overlap above which two detections are the same face (MediaPipe min_suppression_threshold). */
    const val NMS_MIN_IOU = 0.3

    /** Raw scores are clipped before the sigmoid (MediaPipe score_clipping_thresh). */
    private const val SCORE_CLIP = 100f

    /** Box and keypoint offsets are in input pixels; anchors are in [0, 1]. */
    private const val COORD_SCALE = INPUT_SIZE.toFloat()

    /**
     * Anchor centers, x and y interleaved, in the order of the model output. MediaPipe's
     * SsdAnchorsCalculator with strides 8, 16, 16, 16, anchor offset 0.5 and fixed anchor size:
     * layers with the same stride share one grid, two anchors per layer and cell, so the 16 x 16
     * grid has 2 anchors per cell and the 8 x 8 grid 6. 16 * 16 * 2 + 8 * 8 * 6 = 896.
     */
    val anchors: FloatArray = buildAnchors()

    private fun buildAnchors(): FloatArray {
        val strides = intArrayOf(8, 16, 16, 16)
        val out = ArrayList<Float>(NUM_ANCHORS * 2)
        var layer = 0
        while (layer < strides.size) {
            val stride = strides[layer]
            var anchorsPerCell = 0
            while (layer < strides.size && strides[layer] == stride) {
                anchorsPerCell += 2
                layer++
            }
            val grid = ceil(INPUT_SIZE.toDouble() / stride).toInt()
            for (y in 0 until grid) for (x in 0 until grid) repeat(anchorsPerCell) {
                out.add(((x + 0.5) / grid).toFloat())
                out.add(((y + 0.5) / grid).toFloat())
            }
        }
        check(out.size == NUM_ANCHORS * 2)
        return out.toFloatArray()
    }

    /**
     * How the image was fitted into the square input: scaled by [scale] to keep its aspect ratio
     * and centered, with the rest of the input left at zero (MediaPipe's keep_aspect_ratio).
     */
    class Letterbox(val scale: Double, val offsetX: Int, val offsetY: Int) {
        fun toImageX(inputX: Double) = (inputX - offsetX) / scale
        fun toImageY(inputY: Double) = (inputY - offsetY) / scale
    }

    fun letterbox(width: Int, height: Int): Letterbox {
        val scale = INPUT_SIZE.toDouble() / max(width, height)
        val w = (width * scale).roundToInt().coerceIn(1, INPUT_SIZE)
        val h = (height * scale).roundToInt().coerceIn(1, INPUT_SIZE)
        return Letterbox(scale, (INPUT_SIZE - w) / 2, (INPUT_SIZE - h) / 2)
    }

    /**
     * The model input for [image]: 128 x 128 x 3 floats, RGB in [-1, 1], row by row, with bilinear
     * scaling (pixel centers at half-pixel positions, as in TensorFlow and OpenCV).
     */
    fun input(image: Image, box: Letterbox = letterbox(image.width, image.height)): FloatArray {
        val out = FloatArray(INPUT_SIZE * INPUT_SIZE * 3)
        val w = (image.width * box.scale).roundToInt().coerceIn(1, INPUT_SIZE)
        val h = (image.height * box.scale).roundToInt().coerceIn(1, INPUT_SIZE)
        val sx = image.width.toDouble() / w
        val sy = image.height.toDouble() / h
        for (y in 0 until h) {
            val fy = ((y + 0.5) * sy - 0.5).coerceIn(0.0, image.height - 1.0)
            val y0 = floor(fy).toInt()
            val y1 = min(y0 + 1, image.height - 1)
            val wy = fy - y0
            for (x in 0 until w) {
                val fx = ((x + 0.5) * sx - 0.5).coerceIn(0.0, image.width - 1.0)
                val x0 = floor(fx).toInt()
                val x1 = min(x0 + 1, image.width - 1)
                val wx = fx - x0
                val c00 = rgb(image, x0, y0)
                val c10 = rgb(image, x1, y0)
                val c01 = rgb(image, x0, y1)
                val c11 = rgb(image, x1, y1)
                val o = ((y + box.offsetY) * INPUT_SIZE + x + box.offsetX) * 3
                for ((k, shift) in intArrayOf(16, 8, 0).withIndex()) {
                    val v = ((c00 shr shift) and 0xFF) * (1 - wx) * (1 - wy) +
                        ((c10 shr shift) and 0xFF) * wx * (1 - wy) +
                        ((c01 shr shift) and 0xFF) * (1 - wx) * wy +
                        ((c11 shr shift) and 0xFF) * wx * wy
                    out[o + k] = (v / 127.5 - 1.0).toFloat()
                }
            }
        }
        return out
    }

    private fun rgb(image: Image, x: Int, y: Int): Int {
        val c = image[x, y]
        return when (image.colorType) {
            ImageColorType.RGB -> c and 0xFFFFFF
            ImageColorType.GRAY -> c.coerceIn(0, 255).let { (it shl 16) or (it shl 8) or it }
            ImageColorType.BINARY -> if (c != 0) 0xFFFFFF else 0
        }
    }

    /**
     * Faces from the raw model output, strongest first, in source image pixels.
     * [regressors] has [VALUES_PER_ANCHOR] values per anchor, [scores] one raw score per anchor.
     */
    fun decode(regressors: FloatArray, scores: FloatArray, box: Letterbox): List<FaceDetection> {
        require(regressors.size == NUM_ANCHORS * VALUES_PER_ANCHOR && scores.size == NUM_ANCHORS) {
            "BlazeFace output has ${regressors.size} and ${scores.size} values"
        }
        val candidates = ArrayList<Raw>()
        for (i in 0 until NUM_ANCHORS) {
            val score = sigmoid(scores[i].coerceIn(-SCORE_CLIP, SCORE_CLIP))
            if (score < MIN_SCORE) continue
            val r = i * VALUES_PER_ANCHOR
            val ax = anchors[2 * i]
            val ay = anchors[2 * i + 1]
            // MediaPipe reverse_output_order: x, y, w, h; the anchors have a fixed size of 1
            val cx = regressors[r] / COORD_SCALE + ax
            val cy = regressors[r + 1] / COORD_SCALE + ay
            val w = regressors[r + 2] / COORD_SCALE
            val h = regressors[r + 3] / COORD_SCALE
            val kx = DoubleArray(NUM_KEYPOINTS) { (regressors[r + 4 + 2 * it] / COORD_SCALE + ax).toDouble() }
            val ky = DoubleArray(NUM_KEYPOINTS) { (regressors[r + 5 + 2 * it] / COORD_SCALE + ay).toDouble() }
            candidates.add(Raw(score, (cx - w / 2).toDouble(), (cy - h / 2).toDouble(), (cx + w / 2).toDouble(), (cy + h / 2).toDouble(), kx, ky))
        }
        return weightedNms(candidates).map { it.toImage(box) }
    }

    private fun sigmoid(x: Float) = (1.0 / (1.0 + exp(-x.toDouble()))).toFloat()

    /** A detection in input coordinates, [0, 1]. */
    private class Raw(
        val score: Float,
        val left: Double,
        val top: Double,
        val right: Double,
        val bottom: Double,
        val kx: DoubleArray,
        val ky: DoubleArray,
    ) {
        fun area() = max(0.0, right - left) * max(0.0, bottom - top)

        fun toImage(box: Letterbox): FaceDetection {
            fun x(v: Double) = box.toImageX(v * INPUT_SIZE)
            fun y(v: Double) = box.toImageY(v * INPUT_SIZE)
            return FaceDetection(
                score, x(left), y(top), x(right), y(bottom),
                DoubleArray(NUM_KEYPOINTS) { x(kx[it]) }, DoubleArray(NUM_KEYPOINTS) { y(ky[it]) },
            )
        }
    }

    private fun iou(a: Raw, b: Raw): Double {
        val iw = min(a.right, b.right) - max(a.left, b.left)
        val ih = min(a.bottom, b.bottom) - max(a.top, b.top)
        if (iw <= 0 || ih <= 0) return 0.0
        val inter = iw * ih
        val union = a.area() + b.area() - inter
        return if (union > 0) inter / union else 0.0
    }

    /**
     * MediaPipe's weighted non-maximum suppression: the strongest remaining detection takes every
     * detection that overlaps it by more than [NMS_MIN_IOU], and the group is replaced by the
     * score-weighted average of its boxes and keypoints, with the strongest score. Averaging the
     * neighbouring anchors gives steadier boxes and eyes than keeping only the best one.
     */
    private fun weightedNms(candidates: List<Raw>): List<Raw> {
        var remaining = candidates.sortedByDescending { it.score }
        val result = ArrayList<Raw>()
        while (remaining.isNotEmpty()) {
            val top = remaining[0]
            // top is always in its own group, even if a degenerate box has no area
            val (group, rest) = remaining.partition { it === top || iou(top, it) > NMS_MIN_IOU }
            remaining = rest
            val total = group.sumOf { it.score.toDouble() }
            fun avg(f: (Raw) -> Double) = group.sumOf { it.score * f(it) } / total
            result.add(
                Raw(
                    top.score, avg { it.left }, avg { it.top }, avg { it.right }, avg { it.bottom },
                    DoubleArray(NUM_KEYPOINTS) { k -> avg { it.kx[k] } },
                    DoubleArray(NUM_KEYPOINTS) { k -> avg { it.ky[k] } },
                )
            )
        }
        return result
    }
}
