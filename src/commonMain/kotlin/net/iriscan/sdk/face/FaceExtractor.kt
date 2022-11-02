package net.iriscan.sdk.face

import net.iriscan.sdk.core.algorithm.BiometricAlgorithmProperties
import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.face.record.FaceImageRecord

/**
 * @author Slava Gornostal
 *
 * Interface for extracting face from image or record
 */
interface FaceExtractor {
    /**
     * Detects and extracts face from biometric image record
     * */
    fun extract(sample: FaceImageRecord): FaceImageRecord
    fun extract(sample: FaceImageRecord, props: BiometricAlgorithmProperties): FaceImageRecord

    /**
     * Detects and extracts face from raw image
     * */
    fun extract(sample: Image): Image
    fun extract(sample: Image, props: BiometricAlgorithmProperties): Image
}