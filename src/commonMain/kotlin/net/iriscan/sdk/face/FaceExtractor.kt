package net.iriscan.sdk.face

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

    /**
     * Detects and extracts face from raw image
     * */
    fun extract(sample: Image): Image
}