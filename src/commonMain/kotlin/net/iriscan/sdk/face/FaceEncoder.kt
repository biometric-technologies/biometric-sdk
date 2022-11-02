package net.iriscan.sdk.face

import net.iriscan.sdk.core.algorithm.BiometricAlgorithmProperties
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
    fun encode(sample: FaceImageRecord, props: BiometricAlgorithmProperties): FaceTemplateRecord

    /**
     * Detects and encodes provided record to biometric template record
     * */
    fun detectAndEncode(sample: FaceImageRecord): FaceTemplateRecord
    fun detectAndEncode(
        sample: FaceImageRecord,
        extractProps: BiometricAlgorithmProperties,
        encodeProps: BiometricAlgorithmProperties
    ): FaceTemplateRecord

    /**
     * Encodes provided image to face template
     * */
    fun encode(sample: Image): ByteArray
    fun encode(sample: Image, props: BiometricAlgorithmProperties): ByteArray

    /**
     * Detects and encodes provided record to face template record
     * */
    fun detectAndEncode(sample: Image): ByteArray
    fun detectAndEncode(
        sample: Image,
        extractProps: BiometricAlgorithmProperties,
        encodeProps: BiometricAlgorithmProperties
    ): ByteArray
}