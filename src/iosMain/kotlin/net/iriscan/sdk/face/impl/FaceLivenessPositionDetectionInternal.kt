package net.iriscan.sdk.face.impl

import io.github.aakira.napier.Napier
import kotlinx.cinterop.ptr
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import net.iriscan.sdk.core.image.NativeImage
import net.iriscan.sdk.core.utils.Math
import net.iriscan.sdk.face.LivenessModelPositionConfiguration
import net.iriscan.sdk.utils.throwError
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRef
import platform.Vision.VNDetectFaceRectanglesRequest
import platform.Vision.VNFaceObservation
import platform.Vision.VNImageRequestHandler

internal actual class FaceLivenessPositionDetectionInternal actual
constructor(private val modelConfig: LivenessModelPositionConfiguration) {
    actual fun detectPosition(image: NativeImage, traceId: String?): Int =
        detectInternal(image.ptr, traceId)

    private fun detectInternal(cgImage: CGImageRef, traceId: String?): Int {
        Napier.d(tag = traceId) {
            "Detecting face position from native image [${CGImageGetWidth(cgImage)},${
                CGImageGetHeight(
                    cgImage
                )
            }]"
        }
        val face = throwError { errorPtr ->
            val handler = VNImageRequestHandler(cgImage, mapOf<Any?, Any?>())
            val faceRect = CompletableDeferred<VNFaceObservation?>()
            val request = VNDetectFaceRectanglesRequest { data, error ->
                if (error != null) {
                    Napier.e(tag = traceId) { "Could not extract face from image: ${error.localizedDescription}" }
                    faceRect.complete(null)
                    return@VNDetectFaceRectanglesRequest
                }
                faceRect.complete(data?.results?.firstOrNull() as? VNFaceObservation)
            }
            handler.performRequests(listOf(request), errorPtr)
            runBlocking { faceRect.await() }
        }
        if (face == null) {
            Napier.w(tag = traceId) { "No biometrics were found on provided image" }
            return 0
        }
        Napier.d(tag = traceId) { "Face angles (x,y,z): ${face.roll?.floatValue}, ${face.yaw?.floatValue}, ${face.pitch?.floatValue}" }
        val eulerY = Math.toDegrees(face.yaw?.doubleValue ?: 0.0)
        val eulerZ = Math.toDegrees(face.pitch?.doubleValue ?: 0.0)
        return getOrientationBasedOnAngles(eulerY, eulerZ, modelConfig.threshold)
    }
}