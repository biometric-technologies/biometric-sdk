package net.iriscan.sdk.face

import net.iriscan.sdk.core.io.HashMethod

/**
 * @author Slava Gornostal
 */
class FaceExtractProperties

class FaceEncodeProperties(val tfModel: FaceNetModelConfiguration)

/**
 * The FaceNet model and the sizes of its input and output, which must match the model.
 * Sizes of 0 or less are rejected here, when the configuration is created, because no
 * model has them and the platforms would only fail later, after the model has been
 * loaded, each in its own way.
 */
class FaceNetModelConfiguration(
    val path: String,
    /** Width in pixels of the model input; the face image is resized to it. */
    val inputWidth: Int,
    /** Height in pixels of the model input. */
    val inputHeight: Int,
    /** Number of float32 values the model returns: the template is `4 * outputLength` bytes. */
    val outputLength: Int,
    val modelChecksum: String?,
    val modelChecksumMethod: HashMethod?,
    val overrideCacheOnWrongChecksum: Boolean?
) {
    init {
        requirePositiveModelSize("FaceNet", "inputWidth", inputWidth)
        requirePositiveModelSize("FaceNet", "inputHeight", inputHeight)
        requirePositiveModelSize("FaceNet", "outputLength", outputLength)
    }

    constructor(
        path: String,
        inputWidth: Int,
        inputHeight: Int,
        outputLength: Int
    ) : this(path, inputWidth, inputHeight, outputLength, null, null, null)
}

class FaceMatchProperties(val threshold: Double)

class FaceLivenessDetectionProperties(
    val photo: LivenessModelPhotoConfiguration?,
    val position: LivenessModelPositionConfiguration?,
)

class LivenessModelPositionConfiguration(
    val threshold: Double
)

/**
 * The photo liveness model and the size of its input, which must match the model.
 * Sizes of 0 or less are rejected when the configuration is created, as in
 * [FaceNetModelConfiguration].
 */
class LivenessModelPhotoConfiguration(
    val path: String,
    /** Width in pixels of the model input; the face image is resized to it. */
    val inputWidth: Int,
    /** Height in pixels of the model input. */
    val inputHeight: Int,
    val threshold: Double,
    val modelChecksum: String?,
    val modelChecksumMethod: HashMethod?,
    val overrideCacheOnWrongChecksum: Boolean?
) {
    init {
        requirePositiveModelSize("Photo liveness", "inputWidth", inputWidth)
        requirePositiveModelSize("Photo liveness", "inputHeight", inputHeight)
    }

    constructor(
        path: String,
        inputWidth: Int,
        inputHeight: Int,
        threshold: Double
    ) : this(path, inputWidth, inputHeight, threshold, null, null, null)
}

/**
 * Model sizes are used only after the model has been loaded (on the JVM and iOS at the
 * first encode or liveness check), and a size of 0 or less fails there in a different
 * way on each platform (on the JVM, `BufferedImage` throws for a width or height of 0,
 * and an `outputLength` of 0 asks the model for an empty template). Checked once here
 * instead, with the name of the setting in the message.
 */
private fun requirePositiveModelSize(model: String, name: String, value: Int) {
    require(value > 0) { "$model $name must be positive (it must match the model), was $value" }
}