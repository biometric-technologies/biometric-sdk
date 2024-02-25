package net.iriscan.sdk.face.impl

import net.iriscan.sdk.core.image.NativeImage
import net.iriscan.sdk.core.utils.generateTraceID
import net.iriscan.sdk.face.LivenessModelPhotoConfiguration
import net.iriscan.sdk.face.LivenessModelPositionConfiguration

/**
 * @author Slava Gornostal
 */
internal expect class FaceLivenessPhotoDetectionInternal(modelConfig: LivenessModelPhotoConfiguration) {
    fun validate(image: NativeImage, traceId: String? = generateTraceID()): Boolean
    fun score(image: NativeImage, traceId: String? = generateTraceID()): Double
}

internal expect class FaceLivenessPositionDetectionInternal(modelConfig: LivenessModelPositionConfiguration) {
    fun detectPosition(image: NativeImage, traceId: String? = generateTraceID()): Int
}

/**
 * Detects and return position based on euler angles
 * 0 - straight, 1 - left, 2 - right, 3 - top, 4 - bottom
 * */
internal fun getOrientationBasedOnAngles(eulerY: Double, eulerZ: Double, threshold: Double): Int =
    when {
        eulerY < 0 && eulerY <= -threshold -> 2
        eulerY > 0 && eulerY >= threshold -> 1
        eulerZ < 0 && eulerZ <= -threshold -> 4
        eulerZ > 0 && eulerZ >= threshold -> 3
        else -> 0
    }