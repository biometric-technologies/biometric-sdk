package net.iriscan.sdk.face

import net.iriscan.sdk.core.io.DataBytes

/**
 * @author Slava Gornostal
 */
interface FaceMatcher {
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