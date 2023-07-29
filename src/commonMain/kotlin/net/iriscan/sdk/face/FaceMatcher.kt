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

    /**
     * Match one template across multiple templates using min aggregation
     * @return true if matches
     * */
    fun matchesAny(sample1: FaceTemplateRecord, samples: List<FaceTemplateRecord>): Boolean

    /**
     * Matches one template with other
     * @return true if they matches
     * */
    fun matches(sample1: DataBytes, sample2: DataBytes): Boolean

    /**
     * Match one template across multiple templates using min aggregation
     * @return true if matches
     * */
    fun matchesAny(sample1: DataBytes, samples: List<DataBytes>): Boolean

    /**
     * Calculates match score one biometric record template with other
     * @return score value
     * */
    fun matchScore(sample1: FaceTemplateRecord, sample2: FaceTemplateRecord): Double

    /**
     * Calculates min match score across multiple templates
     * @return score value
     * */
    fun matchScoreMin(sample1: FaceTemplateRecord, samples: List<FaceTemplateRecord>): Double

    /**
     * Calculates match score one template with other
     * @return score value
     * */
    fun matchScore(sample1: DataBytes, sample2: DataBytes): Double

    /**
     * Calculates min match score across multiple templates
     * @return score value
     * */
    fun matchScoreMin(sample1: DataBytes, samples: List<DataBytes>): Double
}