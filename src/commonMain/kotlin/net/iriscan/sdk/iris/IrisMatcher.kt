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
}