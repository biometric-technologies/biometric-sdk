package net.iriscan.sdk.face.impl

import io.github.aakira.napier.Napier
import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.image.ImageColorType
import net.iriscan.sdk.core.image.NativeImage
import org.bytedeco.opencv.global.opencv_core
import org.bytedeco.opencv.global.opencv_imgproc
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.Rect
import org.bytedeco.opencv.opencv_core.RectVector
import org.bytedeco.opencv.opencv_core.Size
import org.bytedeco.opencv.opencv_objdetect.CascadeClassifier
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.io.path.absolutePathString
import kotlin.math.max
import kotlin.math.min

/**
 * @author Slava Gornostal
 *
 * Face detection on the JVM with OpenCV Haar cascades. The largest face is
 * cropped with a margin and, when both eyes are found, rotated so that the eye
 * line is horizontal. The result is a square crop ready for the encoder.
 */
internal actual class FaceExtractorInternal {

    private val faceClassifier = loadCascade("haarcascade_frontalface_alt.xml")
    private val eyeClassifier = loadCascade("haarcascade_eye.xml")

    private fun loadCascade(name: String): CascadeClassifier {
        val file = File("${System.getProperty("java.io.tmpdir")}/.biometric-sdk/$name").toPath()
        if (!Files.exists(file)) {
            val xml = javaClass.getResourceAsStream("/$name")
                ?: throw IllegalStateException("Resource $name is missing")
            Files.createDirectories(file.parent)
            xml.use { Files.copy(it, file, StandardCopyOption.REPLACE_EXISTING) }
        }
        return CascadeClassifier(file.absolutePathString())
    }

    actual fun extract(image: Image, rotateOnWrongOrientation: Boolean, traceId: String?): Image? =
        extractInternal(image, rotateOnWrongOrientation, traceId)

    actual fun extract(image: NativeImage, rotateOnWrongOrientation: Boolean, traceId: String?): NativeImage? =
        extractInternal(toSdkImage(image), rotateOnWrongOrientation, traceId)?.let { toNativeImage(it) }

    private fun extractInternal(image: Image, align: Boolean, traceId: String?): Image? {
        val gray = grayMat(image)
        opencv_imgproc.equalizeHist(gray, gray)
        val minSide = max(24, min(image.width, image.height) / 8)
        val faces = RectVector()
        faceClassifier.detectMultiScale(gray, faces, 1.1, 3, 0, Size(minSide, minSide), Size())
        val face = faces.get().maxByOrNull { it.width() * it.height() }
        if (face == null) {
            Napier.w(tag = traceId) { "No face found on image [${image.width},${image.height}]" }
            return null
        }
        val angle = if (align) eyeAngle(gray, face) ?: 0.0 else 0.0
        Napier.d(tag = traceId) { "Face [${face.x()},${face.y()},${face.width()},${face.height()}], roll $angle" }
        return cropFaceBox(image, face.x(), face.y(), face.width(), face.height(), angle, OUTPUT_SIZE)
    }

    /** Roll angle from the two eyes found in the upper part of the face box, or null. */
    private fun eyeAngle(gray: Mat, face: Rect): Double? {
        val roiHeight = min((face.height() * 0.6).toInt(), gray.rows() - face.y())
        val roiWidth = min(face.width(), gray.cols() - face.x())
        if (roiHeight < 10 || roiWidth < 10) return null
        val roi = Mat(gray, Rect(face.x(), face.y(), roiWidth, roiHeight))
        val eyes = RectVector()
        val minEye = max(8, face.width() / 10)
        val maxEye = max(minEye + 1, face.width() / 3)
        eyeClassifier.detectMultiScale(roi, eyes, 1.1, 3, 0, Size(minEye, minEye), Size(maxEye, maxEye))
        val found = eyes.get()
        val half = roiWidth / 2.0
        val left = found.filter { it.x() + it.width() / 2.0 < half }.maxByOrNull { it.width() } ?: return null
        val right = found.filter { it.x() + it.width() / 2.0 >= half }.maxByOrNull { it.width() } ?: return null
        return eyeLineAngle(
            left.x() + left.width() / 2.0, left.y() + left.height() / 2.0,
            right.x() + right.width() / 2.0, right.y() + right.height() / 2.0,
        )
    }

    private fun grayMat(image: Image): Mat {
        val bytes = ByteArray(image.width * image.height) { i ->
            val c = image.colors[i]
            val v = when (image.colorType) {
                ImageColorType.RGB -> (299 * ((c ushr 16) and 0xFF) + 587 * ((c ushr 8) and 0xFF) + 114 * (c and 0xFF)) / 1000
                ImageColorType.GRAY -> c.coerceIn(0, 255)
                ImageColorType.BINARY -> if (c != 0) 255 else 0
            }
            v.toByte()
        }
        val mat = Mat(image.height, image.width, opencv_core.CV_8UC1)
        mat.data().put(*bytes)
        return mat
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
    }
}
