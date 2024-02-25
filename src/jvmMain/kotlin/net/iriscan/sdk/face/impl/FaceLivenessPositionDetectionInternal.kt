package net.iriscan.sdk.face.impl

import net.iriscan.sdk.core.image.NativeImage
import net.iriscan.sdk.face.LivenessModelPositionConfiguration

internal actual class FaceLivenessPositionDetectionInternal actual
constructor(modelConfig: LivenessModelPositionConfiguration) {
    actual fun detectPosition(image: NativeImage, traceId: String?): Int {
        return 0
    }
}