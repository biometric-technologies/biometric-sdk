package net.iriscan.sdk.face

import net.iriscan.sdk.core.io.DataBytes

/**
 * @author Slava Gornostal
 *
 * Compares face templates made by [FaceEncoder]. Unlike the iris matcher, the scores are
 * distances: the Euclidean distance between the L2-normalized embeddings, from 0 (same
 * direction) to 2 (opposite), so lower means more similar. Two templates match when their
 * distance is at most [FaceMatchProperties.threshold].
 *
 * Templates of different lengths (for example from two different models), or data that is not
 * a whole number of float32 values, cannot be compared: the calls throw [IllegalArgumentException].
 */
interface FaceMatcher {
    /**
     * Matches one template with other
     * @return true if their distance is at most the threshold
     * */
    fun matches(sample1: DataBytes, sample2: DataBytes): Boolean

    /**
     * Matches one template against several, for example all templates enrolled for one person,
     * by the distance to the closest of them ([matchScoreMin])
     * @return true if the closest template matches; false if [samples] is empty
     * */
    fun matchesAny(sample1: DataBytes, samples: List<DataBytes>): Boolean

    /**
     * Distance between two templates
     * @return distance, 0.0-2.0; lower is more similar
     * */
    fun matchScore(sample1: DataBytes, sample2: DataBytes): Double

    /**
     * Distance from one template to the closest of several
     * @return the smallest distance; [Double.POSITIVE_INFINITY], which passes no threshold,
     * if [samples] is empty
     * */
    fun matchScoreMin(sample1: DataBytes, samples: List<DataBytes>): Double
}