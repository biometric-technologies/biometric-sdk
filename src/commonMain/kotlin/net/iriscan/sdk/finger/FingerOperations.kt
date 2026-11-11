package net.iriscan.sdk.finger

import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.io.DataBytes

/**
 * Fingerprint SDK operations: minutiae templates and their comparison.
 *
 * The algorithm is a port of SourceAFIS (Robert Važan, Apache License 2.0) and gives the same
 * minutiae and the same scores as SourceAFIS for Java 3.18.
 *
 * @see FingerEncoder
 * @see FingerMatcher
 */
interface FingerOperations {
    fun encoder(): FingerEncoder
    fun matcher(): FingerMatcher
}

/**
 * Turns a fingerprint image into a template.
 *
 * The template is an ISO/IEC 19794-2:2005 finger minutiae record: one finger view, 500 dpi
 * (197 pixels per cm), the format other fingerprint systems and biometric-converter read.
 *
 * Works on images from contact scanners or scanned ink prints, with dark ridges on a light
 * background. Phone camera photos of a finger are not supported.
 */
interface FingerEncoder {
    /**
     * Template from an image at the resolution set in [FingerEncodeProperties.dpi].
     * @return the ISO/IEC 19794-2:2005 record, or null if no minutiae were found
     */
    fun extractAndEncode(sample: Image): DataBytes?

    /**
     * Template from an image of the given resolution, in dots per inch (20-20000).
     * @return the ISO/IEC 19794-2:2005 record, or null if no minutiae were found
     */
    fun extractAndEncode(sample: Image, dpi: Double): DataBytes?
}

/**
 * Compares templates: ISO/IEC 19794-2 records of version 2005 or 2011, from this SDK or from
 * other systems. A record with several finger views is compared by its first view.
 */
interface FingerMatcher {
    /**
     * Similarity of two templates. 0 means no similarity; there is no upper bound.
     * 40 corresponds to a false match rate of 0.01%. Compare with [FingerMatchProperties.threshold].
     */
    fun score(sample1: DataBytes, sample2: DataBytes): Double

    /** @return true if the score reaches [FingerMatchProperties.threshold] */
    fun matches(sample1: DataBytes, sample2: DataBytes): Boolean

    fun matchesAny(sample1: DataBytes, samples: List<DataBytes>): Boolean

    /**
     * Finds the most similar template in [samples] (1:N search).
     * @return the best candidate, or null if [samples] is empty or nothing reaches the threshold
     */
    fun identify(sample: DataBytes, samples: List<DataBytes>): FingerMatch?
}

/**
 * Result of a 1:N search.
 * @property index position of the template in the searched list
 * @property score similarity, see [FingerMatcher.score]
 */
data class FingerMatch(val index: Int, val score: Double)
