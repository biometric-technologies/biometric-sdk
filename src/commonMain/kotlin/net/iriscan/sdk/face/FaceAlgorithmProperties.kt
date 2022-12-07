package net.iriscan.sdk.face

import net.iriscan.sdk.core.algorithm.BiometricAlgorithmProperties

/**
 * @author Slava Gornostal
 */
class FaceExtractProperties : BiometricAlgorithmProperties {
    override fun asMap(): Map<String, Any> = mapOf()
}

class FaceEncodeProperties(
    val tfliteModelPath: String = "facenet.tflite"
) : BiometricAlgorithmProperties {
    override fun asMap(): Map<String, Any> = mapOf()
}

class FaceMatchProperties(
    val threshold: Double = 10.0
) : BiometricAlgorithmProperties {
    override fun asMap(): Map<String, Any> = mapOf()
}