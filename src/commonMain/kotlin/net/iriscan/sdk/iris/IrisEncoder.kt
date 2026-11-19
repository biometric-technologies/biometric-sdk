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
     * Encodes provided extracted image to iris template.
     * The template is returned even when too little of the texture is valid for the matcher
     * to compare it; [extractAndEncode] rejects such captures.
     * */
    fun encode(sample: Image): DataBytes

    /**
     * Extracts and encodes provided image to iris template
     * @return the template, or null when no iris is found, less than 20% of the unwrapped iris
     * is visible, or the template has fewer valid bits than
     * [IrisMatchProperties.minComparedBitsFraction] of its size: such a template scores 0
     * against every template, so the eye has to be captured again
     * */
    fun extractAndEncode(sample: Image): DataBytes?
}