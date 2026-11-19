package net.iriscan.sdk.face.impl

import io.github.aakira.napier.Napier
import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.image.ImageColorType
import net.iriscan.sdk.core.image.NativeImage
import net.iriscan.sdk.tf.InterpreterImpl
import java.awt.image.BufferedImage
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * @author Slava Gornostal
 *
 * Face detection on the JVM with MediaPipe's BlazeFace short-range model, run by the same
 * TensorFlow Lite as the face encoder. The model (230 KB) is bundled in the jar, so nothing is
 * downloaded. The largest face is cropped with a margin and rotated so that the line between the
 * eyes, two of the model's keypoints, is horizontal. The result is a square crop ready for the encoder.
 */
internal actual class FaceExtractorInternal {

    actual fun extract(image: Image, rotateOnWrongOrientation: Boolean, traceId: String?): Image? =
        extractInternal(image, rotateOnWrongOrientation, traceId)

    actual fun extract(image: NativeImage, rotateOnWrongOrientation: Boolean, traceId: String?): NativeImage? =
        extractInternal(toSdkImage(image), rotateOnWrongOrientation, traceId)?.let { toNativeImage(it) }

    private fun extractInternal(image: Image, align: Boolean, traceId: String?): Image? {
        val face = detectFaces(image)
            // faces smaller than this are too small to encode reliably (the same limit as before)
            .filter { max(it.width, it.height) >= max(MIN_FACE_PX, min(image.width, image.height) / MIN_FACE_SHARE_DIVISOR) }
            .maxByOrNull { it.width * it.height }
        if (face == null) {
            Napier.w(tag = traceId) { "No face found on image [${image.width},${image.height}]" }
            return null
        }
        // keypoint 0 is the person's right eye, on the left of an unmirrored photo
        val angle = if (align) {
            eyeLineAngle(face.keypointsX[0], face.keypointsY[0], face.keypointsX[1], face.keypointsY[1]) ?: 0.0
        } else 0.0
        val x = face.left.roundToInt()
        val y = face.top.roundToInt()
        val w = face.width.roundToInt()
        val h = face.height.roundToInt()
        Napier.d(tag = traceId) { "Face [$x,$y,$w,$h], score ${face.score}, roll $angle" }
        return cropFaceBox(image, x, y, w, h, angle, OUTPUT_SIZE)
    }

    /** All faces BlazeFace finds, in image pixels. */
    internal fun detectFaces(image: Image): List<FaceDetection> {
        val box = BlazeFace.letterbox(image.width, image.height)
        val regressors = FloatArray(BlazeFace.NUM_ANCHORS * BlazeFace.VALUES_PER_ANCHOR)
        val scores = FloatArray(BlazeFace.NUM_ANCHORS)
        // output 0 is "regressors" [1, 896, 16], output 1 "classificators" [1, 896, 1]
        detector.invoke(mapOf(0 to BlazeFace.input(image, box)), mutableMapOf(0 to regressors, 1 to scores))
        return BlazeFace.decode(regressors, scores, box)
    }

    private fun toSdkImage(image: BufferedImage): Image {
        val colors = IntArray(image.width * image.height)
        image.getRGB(0, 0, image.width, image.height, colors, 0, image.width)
        for (i in colors.indices) colors[i] = colors[i] and 0xFFFFFF
        return Image(image.width, image.height, ImageColorType.RGB, colors)
    }

    private fun toNativeImage(image: Image): BufferedImage {
        val out = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        out.setRGB(0, 0, image.width, image.height, image.colors, 0, image.width)
        return out
    }

    private companion object {
        /** Crop size; the encoder resizes it to the model input. */
        const val OUTPUT_SIZE = 192

        /** Smallest face side in pixels, and as a share of the image's shorter side (1/8). */
        const val MIN_FACE_PX = 24
        const val MIN_FACE_SHARE_DIVISOR = 8

        const val MODEL_RESOURCE = "/models/face_detection_short_range.tflite"

        /**
         * One detector for all extractors: the model is small, the interpreter is built once and
         * InterpreterImpl serializes calls, since a TensorFlow Lite interpreter is not thread-safe.
         */
        val detector: InterpreterImpl by lazy {
            val model = FaceExtractorInternal::class.java.getResourceAsStream(MODEL_RESOURCE)
                ?.use { it.readBytes() }
                ?: throw IllegalStateException("Resource $MODEL_RESOURCE is missing")
            InterpreterImpl(model)
        }
    }
}
