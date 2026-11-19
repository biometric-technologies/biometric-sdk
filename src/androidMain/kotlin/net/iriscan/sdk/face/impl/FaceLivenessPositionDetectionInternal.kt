package net.iriscan.sdk.face.impl

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import io.github.aakira.napier.Napier
import net.iriscan.sdk.core.image.NativeImage
import net.iriscan.sdk.face.LivenessModelPositionConfiguration

/**
 * @author Slava Gornostal
 */
internal actual class FaceLivenessPositionDetectionInternal actual
constructor(private val modelConfig: LivenessModelPositionConfiguration) {
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .build()
    )

    actual fun detectPosition(image: NativeImage, traceId: String?): Int {
        Napier.d(tag = traceId) { "Detecting face position from native image [${image.width},${image.height}]" }
        return process(image, traceId)
    }

    private fun process(bitmap: NativeImage, traceId: String?): Int {
        val input = InputImage.fromBitmap(bitmap, 0)
        Napier.d(tag = traceId) { "Passing bitmap to android MLKit face detector" }
        val result = Tasks.await(detector.process(input))
        if (result.isEmpty()) {
            Napier.w(tag = traceId) { "No biometrics were found on provided image" }
            return 0
        }
        val face = result[0]
        Napier.d(tag = traceId) { "Face angles (x,y,z): ${face.headEulerAngleX}, ${face.headEulerAngleY}, ${face.headEulerAngleZ}" }
        // ML Kit: X is the pitch (up and down), Y the yaw (left and right), Z the roll (tilt to a
        // shoulder). Top and bottom come from the pitch, as on iOS; this used to pass the roll.
        return getOrientationBasedOnAngles(
            yawDeg = face.headEulerAngleY.toDouble(),
            pitchDeg = face.headEulerAngleX.toDouble(),
            threshold = modelConfig.threshold
        )
    }


}