package net.iriscan.sdk.face

import net.iriscan.sdk.core.algorithm.BiometricAlgorithmProperties

/**
 * @author Slava Gornostal
 */
class FaceExtractProperties : BiometricAlgorithmProperties {
    override fun asMap(): Map<String, Any> = mapOf()
}

class FaceEncodeProperties(
    val faceNetModel: FaceNetModelConfiguration = FaceNetModelConfiguration(
        tfliteModelPath = "resources://facenet-default.tflite",
        inputWidth = 160,
        inputHeight = 160,
        outputLength = 128
    )
) : BiometricAlgorithmProperties {
    override fun asMap(): Map<String, Any> = mapOf()
}

class FaceNetModelConfiguration(
    val tfliteModelPath: String,
    val inputWidth: Int,
    val inputHeight: Int,
    val outputLength: Int
)

class FaceMatchProperties(
    val threshold: Double = 10.0
) : BiometricAlgorithmProperties {
    override fun asMap(): Map<String, Any> = mapOf()
}