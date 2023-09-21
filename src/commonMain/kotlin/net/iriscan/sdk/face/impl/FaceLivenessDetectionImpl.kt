package net.iriscan.sdk.face.impl

import net.iriscan.sdk.core.image.NativeImage
import net.iriscan.sdk.face.LivenessModelConfiguration

/**
 * @author Slava Gornostal
 */
internal expect class FaceLivenessDetectionInternal(modelConfig: LivenessModelConfiguration) {
    fun validate(image: NativeImage): Boolean
    fun score(image: NativeImage): Double
}