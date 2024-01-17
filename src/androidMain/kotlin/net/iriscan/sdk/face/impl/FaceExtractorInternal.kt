package net.iriscan.sdk.face.impl

import android.graphics.Bitmap
import android.graphics.Matrix
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import io.github.aakira.napier.Napier
import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.image.ImageColorType
import net.iriscan.sdk.core.image.NativeImage
import kotlin.math.atan2

/**
 * @author Slava Gornostal
 */
internal actual class FaceExtractorInternal {
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .build()
    )

    actual fun extract(image: Image, rotateOnWrongOrientation: Boolean, traceId: String?): Image? {
        Napier.d(tag = traceId) { "Extracting face from SDK image [${image.width},${image.height}]" }
        val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.RGB_565)
        bitmap.setPixels(image.colors, 0, image.width, 0, 0, image.width, image.height)
        val face = extractInternal(bitmap) ?: return null
        val pixels = IntArray(face.width * face.height)
        face.getPixels(pixels, 0, face.width, 0, 0, face.width, face.height)
        return Image(
            width = face.width,
            height = face.height,
            colorType = ImageColorType.RGB,
            colors = pixels
        )
    }

    actual fun extract(image: NativeImage, rotateOnWrongOrientation: Boolean, traceId: String?): NativeImage? {
        Napier.d(tag = traceId) { "Extracting biometrics from native image [${image.width},${image.height}]" }
        return extractInternal(image)
    }

    private fun extractInternal(bitmap: Bitmap, rotateOnWrongOrientation: Boolean, traceId: String? = null): Bitmap? {
        val input = InputImage.fromBitmap(bitmap, 0)
        Napier.d(tag = traceId) { "Passing bitmap to android MLKit face detector" }
        val result = Tasks.await(detector.process(input))
        if (result.isEmpty()) {
            Napier.w(tag = traceId) { "No biometrics were found on provided image" }
            return null
        }
        val face = result[0]
        val rect = face.boundingBox
        Napier.d(tag = traceId) { "Extracting face from result bbox $rect" }
        if (
            !(rect.left in 1 until input.width && rect.right in 1 until input.width &&
                    rect.top in 1 until input.height && rect.bottom in 1 until input.height
                    )
        ) {
            Napier.w(tag = traceId) { "Extracted face bbox is out of bounds for provided image" }
            return null
        }
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)
        if (leftEye == null || rightEye == null) {
            Napier.w(tag = traceId) { "Necessary eye(s) [${leftEye?.position}, ${rightEye?.position}] landmarks were not found" }
            return null
        }
        val faceBitmap = Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height())
        val resultImg = if (rotateOnWrongOrientation) {
            val angle = atan2(rightEye.position.y - leftEye.position.y, rightEye.position.x - leftEye.position.x)
                .toDouble()
            Napier.d(tag = traceId) { "Rotating face to portrait position with angle: $angle" }
            val matrix = Matrix()
            matrix.postRotate(-Math.toDegrees(angle).toFloat())
            Bitmap.createBitmap(faceBitmap, 0, 0, faceBitmap.width, faceBitmap.height, matrix, true)
        } else {
            faceBitmap
        }
        Napier.d(tag = traceId) { "Extracting face done with resulting image [${bitmap.width},${bitmap.height}]" }
        return resultImg
    }
}