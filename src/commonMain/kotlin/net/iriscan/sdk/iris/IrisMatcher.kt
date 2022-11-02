package net.iriscan.sdk.iris

import net.iriscan.sdk.core.algorithm.BiometricAlgorithmProperties
import net.iriscan.sdk.iris.record.IrisTemplateRecord

/**
 * @author Slava Gornostal
 */
interface IrisMatcher {
    /**
     * Matches one biometric record template with other
     * @return true if they matches
     * */
    fun matches(sample1: IrisTemplateRecord, sample2: IrisTemplateRecord): Boolean
    fun matches(sample1: IrisTemplateRecord, sample2: IrisTemplateRecord, props: BiometricAlgorithmProperties): Boolean

    /**
     * Matches one template with other
     * @return true if they matches
     * */
    fun matches(sample1: ByteArray, sample2: ByteArray): Boolean
    fun matches(sample1: ByteArray, sample2: ByteArray, props: BiometricAlgorithmProperties): Boolean
}