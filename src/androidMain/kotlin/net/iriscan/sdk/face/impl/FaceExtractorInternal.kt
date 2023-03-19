package net.iriscan.sdk.face.impl

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import net.iriscan.sdk.core.image.Image

/**
 * @author Slava Gornostal
 */
internal actual class FaceExtractorInternal {
    private val detector = FaceDetection.getClient()
    actual fun extract(image: Image): Image {
        val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.RGB_565)
        bitmap.setPixels(image.colors, 0, image.width, 0, 0, image.width, image.height)
        val input = InputImage.fromBitmap(bitmap, 0)
        val result = Tasks.await(detector.process(input))
        if (result.isEmpty()) {
            return image
        }
        val face = result[0].boundingBox
        if (face.left in 1 until image.width && face.right in 1 until image.width &&
            face.top in 1 until image.height && face.bottom in 1 until image.height
        ) {
            return image[face.left..face.right, face.top..face.bottom]
        }
        return image
    }
}