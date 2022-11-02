package net.iriscan.sdk.iris

import net.iriscan.sdk.core.algorithm.BiometricAlgorithmProperties
import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.iris.record.IrisImageRecord
import net.iriscan.sdk.iris.record.IrisTemplateRecord

/**
 * @author Slava Gornostal
 *
 * Interface for generating template from image with biometric
 */
interface IrisEncoder {
    /**
     * Encodes provided extracted record to biometric template record
     * */
    fun encode(sample: IrisImageRecord): IrisTemplateRecord
    fun encode(sample: IrisImageRecord, props: BiometricAlgorithmProperties): IrisTemplateRecord

    /**
     * Extracts and encodes provided image to iris template
     * */
    fun encode(sample: Image): ByteArray
    fun encode(
        sample: Image,
        extractProps: BiometricAlgorithmProperties,
        encodeProps: BiometricAlgorithmProperties
    ): ByteArray

    /**
     * Encodes provided extracted image to iris template
     * */
    fun encodeTexture(sample: Image): ByteArray
    fun encodeTexture(sample: Image, props: BiometricAlgorithmProperties): ByteArray
}