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
 * Head position from the head angles in degrees, as the platform face detector reports them:
 * [yawDeg] turns the head left and right, [pitchDeg] up and down. A turn counts once its angle
 * reaches [threshold]; the yaw is checked first.
 *
 * Returns 0 straight, 1 left, 2 right, 3 top, 4 bottom.
 */
internal fun getOrientationBasedOnAngles(yawDeg: Double, pitchDeg: Double, threshold: Double): Int =
    when {
        yawDeg <= -threshold -> 2
        yawDeg >= threshold -> 1
        pitchDeg <= -threshold -> 4
        pitchDeg >= threshold -> 3
        else -> 0
    }