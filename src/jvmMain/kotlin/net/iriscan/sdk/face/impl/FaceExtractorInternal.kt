package net.iriscan.sdk.face.impl

import io.github.aakira.napier.Napier
import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.image.ImageColorType
import net.iriscan.sdk.core.image.NativeImage
import net.iriscan.sdk.face.impl.haar.Box
import net.iriscan.sdk.face.impl.haar.GrayPlane
import net.iriscan.sdk.face.impl.haar.HaarCascade
import net.iriscan.sdk.face.impl.haar.HaarDetector
import net.iriscan.sdk.face.impl.haar.equalizeHistogram
import java.awt.image.BufferedImage
import kotlin.math.max
import kotlin.math.min

/**
 * @author Slava Gornostal
 *
 * Face detection on the JVM with Haar cascades (OpenCV's haarcascade_frontalface_alt and
 * haarcascade_eye, run by the SDK's own [HaarDetector], no native libraries). The largest face is
 * cropped with a margin and, when both eyes are found, rotated so that the eye line is horizontal.
 * The result is a square crop ready for the encoder.
 */
internal actual class FaceExtractorInternal {

    private val faceDetector = HaarDetector(loadCascade(FACE_CASCADE))
    private val eyeDetector = HaarDetector(loadCascade(EYE_CASCADE))

    private fun loadCascade(name: String): HaarCascade {
        val xml = javaClass.getResourceAsStream("/$name")?.use { it.readBytes().decodeToString() }
            ?: throw IllegalStateException("Resource $name is missing")
        return HaarCascade.parse(xml)
    }

    actual fun extract(image: Image, rotateOnWrongOrientation: Boolean, traceId: String?): Image? =
        extractInternal(image, rotateOnWrongOrientation, traceId)

    actual fun extract(image: NativeImage, rotateOnWrongOrientation: Boolean, traceId: String?): NativeImage? =
        extractInternal(toSdkImage(image), rotateOnWrongOrientation, traceId)?.let { toNativeImage(it) }

    private fun extractInternal(image: Image, align: Boolean, traceId: String?): Image? {
        val gray = equalizeHistogram(grayPlane(image))
        val minSide = max(MIN_FACE_SIDE, min(image.width, image.height) / MIN_FACE_SHARE_DIVISOR)
        val face = faceDetector.detect(gray, SCALE_FACTOR, MIN_NEIGHBORS, minSize = minSide).maxByOrNull { it.area }
        if (face == null) {
            Napier.w(tag = traceId) { "No face found on image [${image.width},${image.height}]" }
            return null
        }
        val angle = if (align) eyeAngle(gray, face) ?: 0.0 else 0.0
        Napier.d(tag = traceId) { "Face [${face.x},${face.y},${face.width},${face.height}], roll $angle" }
        return cropFaceBox(image, face.x, face.y, face.width, face.height, angle, OUTPUT_SIZE)
    }

    /** Roll angle from the two eyes found in the upper part of the face box, or null. */
    private fun eyeAngle(gray: GrayPlane, face: Box): Double? {
        val roiHeight = min((face.height * EYE_REGION_HEIGHT_SHARE).toInt(), gray.height - face.y)
        val roiWidth = min(face.width, gray.width - face.x)
        if (roiHeight < MIN_EYE_REGION || roiWidth < MIN_EYE_REGION) return null
        val roi = gray.crop(face.x, face.y, roiWidth, roiHeight)
        val minEye = max(MIN_EYE_SIDE, face.width / 10)
        val maxEye = max(minEye + 1, face.width / 3)
        val found = eyeDetector.detect(roi, SCALE_FACTOR, MIN_NEIGHBORS, minSize = minEye, maxSize = maxEye)
        val half = roiWidth / 2.0
        val left = found.filter { it.x + it.width / 2.0 < half }.maxByOrNull { it.width } ?: return null
        val right = found.filter { it.x + it.width / 2.0 >= half }.maxByOrNull { it.width } ?: return null
        return eyeLineAngle(
            left.x + left.width / 2.0, left.y + left.height / 2.0,
            right.x + right.width / 2.0, right.y + right.height / 2.0,
        )
    }

    private fun grayPlane(image: Image): GrayPlane = GrayPlane(image.width, image.height, IntArray(image.width * image.height) { i ->
        val c = image.colors[i]
        when (image.colorType) {
            // ITU-R BT.601 luma, the weights OpenCV uses for RGB to gray
            ImageColorType.RGB -> (299 * ((c ushr 16) and 0xFF) + 587 * ((c ushr 8) and 0xFF) + 114 * (c and 0xFF)) / 1000
            ImageColorType.GRAY -> c.coerceIn(0, 255)
            ImageColorType.BINARY -> if (c != 0) 255 else 0
        }
    })

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
        const val FACE_CASCADE = "haarcascade_frontalface_alt.xml"
        const val EYE_CASCADE = "haarcascade_eye.xml"

        /** Crop size; the encoder resizes it to the model input. */
        const val OUTPUT_SIZE = 192

        /** Search scales 10% apart and keep detections confirmed by more than 3 neighbors (OpenCV defaults). */
        const val SCALE_FACTOR = 1.1
        const val MIN_NEIGHBORS = 3

        /** Smallest face searched: 24 px, or 1/8 of the shorter image side if that is larger. */
        const val MIN_FACE_SIDE = 24
        const val MIN_FACE_SHARE_DIVISOR = 8

        /** Eyes are searched in the upper 60% of the face box, sized 1/10 to 1/3 of its width (at least 8 px). */
        const val EYE_REGION_HEIGHT_SHARE = 0.6
        const val MIN_EYE_SIDE = 8
        const val MIN_EYE_REGION = 10
    }
}
