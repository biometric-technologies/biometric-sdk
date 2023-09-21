package net.iriscan.sdk.face.impl

import net.iriscan.sdk.core.image.NativeImage
import net.iriscan.sdk.face.LivenessModelConfiguration

/**
 * @author Slava Gornostal
 */
internal actual class FaceLivenessDetectionInternal actual constructor(
    private val modelConfig: LivenessModelConfiguration
) {
    actual fun validate(image: NativeImage): Boolean {
        TODO("Not yet implemented")
    }

    actual fun score(image: NativeImage): Double {
        TODO("Not yet implemented")
    }

}