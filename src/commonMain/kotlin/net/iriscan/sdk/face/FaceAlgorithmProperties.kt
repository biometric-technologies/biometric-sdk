package net.iriscan.sdk.face

/**
 * @author Slava Gornostal
 */
class FaceExtractProperties

class FaceEncodeProperties(val faceNetModel: FaceNetModelConfiguration)

class FaceNetModelConfiguration(
    val tfliteModelPath: String,
    val inputWidth: Int,
    val inputHeight: Int,
    val outputLength: Int
)

class FaceMatchProperties(val threshold: Double)