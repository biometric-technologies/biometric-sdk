package net.iriscan.sdk.face.impl

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.image.NativeImage

/**
 * @author Slava Gornostal
 */
internal actual class FaceExtractorInternal {
    private val detector = FaceDetection.getClient()
    actual fun extract(image: Image): Image {
        val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.RGB_565)
        bitmap.setPixels(image.colors, 0, image.width, 0, 0, image.width, image.height)
        val input = InputImage.fromBitmap(bitmap, 0)
        val face = extractInternal(input) ?: return image
        return image[face.left..face.right, face.top..face.bottom]
    }

    actual fun extract(image: NativeImage): NativeImage {
        val input = InputImage.fromBitmap(image, 0)
        val face = extractInternal(input) ?: return image
        return Bitmap.createBitmap(image, face.left, face.top, face.width(), face.height())
    }

    private fun extractInternal(input: InputImage): Rect? {
        val result = Tasks.await(detector.process(input))
        if (result.isEmpty()) {
            return null
        }
        val rect = result[0].boundingBox
        if (rect.left in 1 until input.width && rect.right in 1 until input.width &&
            rect.top in 1 until input.height && rect.bottom in 1 until input.height
        ) {
            return rect
        }
        return null
    }
}