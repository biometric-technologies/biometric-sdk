package net.iriscan.sdk.face


/**
 * @author Slava Gornostal
 */
interface FaceOperations {
    fun extractor(): FaceExtractor
    fun encoder(): FaceEncoder
    fun matcher(): FaceMatcher
    fun liveness(): FaceLivenessDetection
}