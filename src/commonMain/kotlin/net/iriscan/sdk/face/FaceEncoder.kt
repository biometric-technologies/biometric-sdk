package net.iriscan.sdk.face

import net.iriscan.sdk.core.image.NativeImage
import net.iriscan.sdk.core.io.DataBytes

/**
 * @author Slava Gornostal
 *
 *  Interface for generating template from face image
 */
interface FaceEncoder {
    /**
     * Encodes provided image to face template
     * */
    fun encode(nativeImage: NativeImage): DataBytes

    /**
     * Extracts and encodes provided record to face template record
     * */
    fun extractAndEncode(nativeImage: NativeImage): DataBytes?
}