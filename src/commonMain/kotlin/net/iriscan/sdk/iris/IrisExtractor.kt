package net.iriscan.sdk.iris

import net.iriscan.sdk.core.algorithm.BiometricAlgorithmProperties
import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.iris.record.IrisImageRecord

/**
 * @author Slava Gornostal
 *
 * Interface for extracting iris texture from image or record
 */
interface IrisExtractor {
    /**
     * Extracts biometric data from biometric image record
     * */
    fun extract(sample: IrisImageRecord): IrisImageRecord
    fun extract(sample: IrisImageRecord, props: BiometricAlgorithmProperties): IrisImageRecord

    /**
     * Extracts biometric data from raw image
     * */
    fun extract(sample: Image): Image
    fun extract(sample: Image, props: BiometricAlgorithmProperties): Image
}