package net.iriscan.sdk.face

import net.iriscan.sdk.core.image.NativeImage

/**
 * @author Slava Gornostal
 */
interface FaceLivenessDetection {

    /**
     * Validates if the image has live face
     * */
    fun validate(nativeImage: NativeImage): Boolean

    /**
     * Returns liveness validation score of the image
     * */
    fun score(nativeImage: NativeImage): Double

}