package net.iriscan.sdk.face

import net.iriscan.sdk.core.image.NativeImage

/**
 * @author Slava Gornostal
 */
interface FaceLivenessPhotoDetection {

    /**
     * Validates if the image has live face
     * */
    fun validate(nativeImage: NativeImage): Boolean

    fun extractAndValidate(nativeImage: NativeImage): Boolean?

    /**
     * Returns liveness validation score of the image
     * */
    fun score(nativeImage: NativeImage): Double

    fun extractAndScore(nativeImage: NativeImage): Double

}

interface FaceLivenessPositionDetection {

    /**
     * Detects and return position based on euler angles
     * 0 - straight, 1 - left, 2 - right, 3 - top, 4 - bottom
     * */
    fun detectPosition(nativeImage: NativeImage): Int

}