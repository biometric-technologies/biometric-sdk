package net.iriscan.sdk.face

import net.iriscan.sdk.core.io.HashMethod

/**
 * @author Slava Gornostal
 */
class FaceExtractProperties

class FaceEncodeProperties(val tfModel: FaceNetModelConfiguration)

class FaceNetModelConfiguration(
    val path: String,
    val inputWidth: Int,
    val inputHeight: Int,
    val outputLength: Int,
    val modelChecksum: String?,
    val modelChecksumMethod: HashMethod?,
    val overrideCacheOnWrongChecksum: Boolean?
) {
    constructor(
        path: String,
        inputWidth: Int,
        inputHeight: Int,
        outputLength: Int
    ) : this(path, inputWidth, inputHeight, outputLength, null, null, null)
}

class FaceMatchProperties(val threshold: Double)

class FaceLivenessDetectionProperties(val tfModel: LivenessModelConfiguration)

class LivenessModelConfiguration(
    val path: String,
    val inputWidth: Int,
    val inputHeight: Int,
    val threshold: Double,
    val modelChecksum: String?,
    val modelChecksumMethod: HashMethod?,
    val overrideCacheOnWrongChecksum: Boolean?
) {
    constructor(
        path: String,
        inputWidth: Int,
        inputHeight: Int,
        threshold: Double
    ) : this(path, inputWidth, inputHeight, threshold, null, null, null)
}