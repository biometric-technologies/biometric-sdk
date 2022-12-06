package net.iriscan.sdk.face

import net.iriscan.sdk.core.algorithm.BiometricAlgorithmProperties

/**
 * @author Slava Gornostal
 */
class FaceExtractProperties : BiometricAlgorithmProperties {
    override fun asMap(): Map<String, Any> = mapOf()
}

class FaceEncodeProperties(
    val tfliteModelPath: String = "classpath:tensorflow/facenet.tflite"
) : BiometricAlgorithmProperties {
    override fun asMap(): Map<String, Any> = mapOf()
}

class FaceMatchProperties(
    val threshold: Double = 0.7 // 0.0-1.0
) : BiometricAlgorithmProperties {
    override fun asMap(): Map<String, Any> = mapOf()
}