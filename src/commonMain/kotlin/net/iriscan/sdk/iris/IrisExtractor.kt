package net.iriscan.sdk.iris

import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.record.BiometricRecord

/**
 * @author Slava Gornostal
 *
 * Interface for extracting iris texture from image or record
 */
interface IrisExtractor {
    /**
     * Extracts biometric data from biometric image record
     * */
    fun extract(sample: BiometricRecord): BiometricRecord

    /**
     * Extracts biometric data from raw image
     * */
    fun extract(sample: Image): Image
}