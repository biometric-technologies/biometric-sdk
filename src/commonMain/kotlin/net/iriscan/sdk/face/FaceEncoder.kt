package net.iriscan.sdk.face

import net.iriscan.sdk.core.image.Image
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
    fun encode(sample: FaceImageRecord): FaceTemplateRecord

    /**
     * Extracts and encodes provided record to biometric template record
     * */
    fun extractAndEncode(sample: FaceImageRecord): FaceTemplateRecord

    /**
     * Encodes provided image to face template
     * */
    fun encode(sample: Image): ByteArray

    /**
     * Extracts and encodes provided record to face template record
     * */
    fun extractAndEncode(sample: Image): ByteArray
}