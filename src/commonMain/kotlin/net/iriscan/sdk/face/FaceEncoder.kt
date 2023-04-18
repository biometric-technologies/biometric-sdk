package net.iriscan.sdk.face

import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.image.NativeImage
import net.iriscan.sdk.core.io.DataBytes
import net.iriscan.sdk.face.record.FaceImageRecord
import net.iriscan.sdk.face.record.FaceTemplateRecord

/**
 * @author Slava Gornostal
 *
 *  Interface for generating template from face image
 */
interface FaceEncoder {
    /**
     * Encodes provided extracted record to biometric template record
     * */
    fun encode(record: FaceImageRecord): FaceTemplateRecord

    /**
     * Extracts and encodes provided record to biometric template record
     * */
    fun extractAndEncode(record: FaceImageRecord): FaceTemplateRecord

    /**
     * Encodes provided image to face template
     * */
    fun encode(sdkImage: Image): DataBytes

    /**
     * Encodes provided image to face template
     * */
    fun encode(nativeImage: NativeImage): DataBytes

    /**
     * Extracts and encodes provided record to face template record
     * */
    fun extractAndEncode(sdkImage: Image): DataBytes

    /**
     * Extracts and encodes provided record to face template record
     * */
    fun extractAndEncode(nativeImage: NativeImage): DataBytes
}