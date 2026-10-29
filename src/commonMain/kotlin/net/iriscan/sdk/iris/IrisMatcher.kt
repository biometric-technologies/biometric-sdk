package net.iriscan.sdk.iris

import net.iriscan.sdk.core.io.DataBytes
import net.iriscan.sdk.core.record.BiometricRecord

/**
 * @author Slava Gornostal
 */
interface IrisMatcher {
    /**
     * Matches one biometric record template with other
     * @return true if they matches
     * */
    fun matches(sample1: BiometricRecord, sample2: BiometricRecord): Boolean
    fun matchesAny(sample1: BiometricRecord, samples: List<BiometricRecord>): Boolean

    /**
     * Matches one template with other
     * @return true if they matches
     * */
    fun matches(sample1: DataBytes, sample2: DataBytes): Boolean
    fun matchesAny(sample1: DataBytes, samples: List<DataBytes>): Boolean

    /**
     * Similarity of two templates, 0.0-1.0 (1 - normalized Hamming distance).
     * Compare with [IrisMatchProperties.threshold].
     * */
    fun score(sample1: DataBytes, sample2: DataBytes): Double

    /**
     * Finds the most similar template in [samples] (1:N search).
     * @return the best candidate, or null if [samples] is empty or nothing reaches the threshold
     * */
    fun identify(sample: DataBytes, samples: List<DataBytes>): IrisMatch?
}

/**
 * Result of a 1:N search.
 * @property index position of the template in the searched list
 * @property score similarity, 0.0-1.0
 */
data class IrisMatch(val index: Int, val score: Double)
