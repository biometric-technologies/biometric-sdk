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
    fun extract(sample: Image): Image?

    /**
     * Finds the pupil, the limbus and the eyelids in an eye image without unwrapping the iris,
     * for showing the segmentation or debugging an image (see [IrisBoundaries]). Returns null when
     * no iris is found.
     *
     * [extract] runs the same segmentation, so for an image where this returns null, [extract]
     * returns null too. The reverse does not hold: [extract] also rejects an iris that is found
     * but too little of which is visible (eyelids, lashes, reflections), so an image can have
     * boundaries here and still give no texture or template.
     */
    fun segment(sample: Image): IrisBoundaries?
}