package net.iriscan.sdk.face

import net.iriscan.sdk.core.io.DataBytes
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
    fun matchesAny(sample1: FaceTemplateRecord, samples: List<FaceTemplateRecord>): Boolean

    /**
     * Matches one template with other
     * @return true if they matches
     * */
    fun matches(sample1: DataBytes, sample2: DataBytes): Boolean
    fun matchesAny(sample1: DataBytes, samples: List<DataBytes>): Boolean
}