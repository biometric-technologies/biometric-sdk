package net.iriscan.sdk.face

import net.iriscan.sdk.core.algorithm.BiometricAlgorithmProperties
import net.iriscan.sdk.face.record.FaceTemplateRecord

/**
 * @author Slava Gornostal
 */
interface FaceMatcher {
    /**
     * Matches one biometric record template with other
     * @return true if they matches
     * */
    fun matches(sample1: FaceTemplateRecord, sample2: FaceTemplateRecord): Boolean
    fun matches(sample1: FaceTemplateRecord, sample2: FaceTemplateRecord, props: BiometricAlgorithmProperties): Boolean

    /**
     * Matches one template with other
     * @return true if they matches
     * */
    fun matches(sample1: ByteArray, sample2: ByteArray): Boolean
    fun matches(sample1: ByteArray, sample2: ByteArray, props: BiometricAlgorithmProperties): Boolean
}