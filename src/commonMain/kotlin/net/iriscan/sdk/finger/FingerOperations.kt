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
 * Works on images from contact scanners or scanned ink prints, with dark ridges on a light
 * background. Phone camera photos of a finger are not supported.
 */
interface FingerEncoder {
    /**
     * Template from an image at the resolution set in [FingerEncodeProperties.dpi].
     * @return the template, or null if no minutiae were found
     */
    fun extractAndEncode(sample: Image): DataBytes?

    /**
     * Template from an image of the given resolution, in dots per inch (20-20000).
     * @return the template, or null if no minutiae were found
     */
    fun extractAndEncode(sample: Image, dpi: Double): DataBytes?

    /**
     * The template as an ISO/IEC 19794-2:2005 finger minutiae record (one finger view, 500 dpi),
     * the format other fingerprint systems and biometric-converter read. Angles are rounded to
     * 1.4 degrees, so the record matches slightly lower than the template it came from.
     */
    fun toIso19794_2(template: DataBytes): DataBytes

    /**
     * Templates from an ISO/IEC 19794-2 record of version 2005 or 2011, one per finger view,
     * ready for [FingerMatcher].
     */
    fun fromIso19794_2(record: DataBytes): List<DataBytes>
}

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
