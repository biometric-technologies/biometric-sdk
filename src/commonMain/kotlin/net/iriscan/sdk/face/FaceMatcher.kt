package net.iriscan.sdk.face

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

    /**
     * Matches one template with other
     * @return true if they matches
     * */
    fun matches(sample1: ByteArray, vararg samples: ByteArray): Boolean
}