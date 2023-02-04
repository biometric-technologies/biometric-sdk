package net.iriscan.sdk.iris

import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.io.DataBytes
import net.iriscan.sdk.core.record.BiometricRecord

/**
 * @author Slava Gornostal
 *
 * Interface for generating template from image with biometric
 */
interface IrisEncoder {
    /**
     * Encodes provided extracted record to biometric template record
     * */
    fun encode(sample: BiometricRecord): BiometricRecord

    /**
     * Extracts and encodes provided image to iris template
     * */
    fun extractAndEncode(sample: BiometricRecord): DataBytes

    /**
     * Encodes provided extracted image to iris template
     * */
    fun encode(sample: Image): DataBytes

    /**
     * Extracts and encodes provided image to iris template
     * */
    fun extractAndEncode(sample: Image): DataBytes
}