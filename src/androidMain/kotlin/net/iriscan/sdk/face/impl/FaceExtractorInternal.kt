package net.iriscan.sdk.face.impl

import android.graphics.Bitmap
import android.graphics.Matrix
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
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

    actual fun extract(image: Image): Image? {
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

    actual fun extract(image: NativeImage): NativeImage? =
        extractInternal(image)

    private fun extractInternal(bitmap: Bitmap): Bitmap? {
        val input = InputImage.fromBitmap(bitmap, 0)
        val result = Tasks.await(detector.process(input))
        if (result.isEmpty()) {
            return null
        }
        val face = result[0]
        val rect = face.boundingBox
        if (
            !(rect.left in 1 until input.width && rect.right in 1 until input.width &&
                    rect.top in 1 until input.height && rect.bottom in 1 until input.height
                    )
        ) {
            return null
        }
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)
        if (leftEye == null || rightEye == null) {
            return null
        }
        val angle = atan2(rightEye.position.y - leftEye.position.y, rightEye.position.x - leftEye.position.x)
            .toDouble()
        val faceBitmap = Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height())
        val matrix = Matrix()
        matrix.postRotate(-Math.toDegrees(angle).toFloat())
        return Bitmap.createBitmap(faceBitmap, 0, 0, faceBitmap.width, faceBitmap.height, matrix, true)
    }
}