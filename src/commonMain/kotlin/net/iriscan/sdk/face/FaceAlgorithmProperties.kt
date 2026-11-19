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

/**
 * Largest distance the face matcher can return. The matcher L2-normalizes both
 * embeddings before it measures their Euclidean distance, so whatever the model,
 * the distance runs from 0 (same direction) through `sqrt(2)` (orthogonal) to 2
 * (opposite directions).
 */
internal const val MAX_FACE_DISTANCE = 2.0

/**
 * Face matching settings. Two faces match when the distance between their
 * embeddings is at most [threshold].
 */
class FaceMatchProperties(
    /**
     * Largest distance at which two faces match, from 0 to 2 (the range of the
     * distance); calibrate it for your model on your own data. Other values are
     * rejected: above 2 every pair of faces would match, below 0 (or NaN) none
     * would, not even a template compared with itself. SDK versions before 0.7
     * compared the embeddings without normalizing them, so for a model that does
     * not L2-normalize its output, a threshold chosen with them must be chosen again.
     */
    val threshold: Double,
) {
    init {
        require(threshold in 0.0..MAX_FACE_DISTANCE) {
            "Face match threshold must be from 0.0 to $MAX_FACE_DISTANCE " +
                    "(a distance between L2-normalized embeddings), was $threshold"
        }
    }
}

class FaceLivenessDetectionProperties(
    val photo: LivenessModelPhotoConfiguration?,
    val position: LivenessModelPositionConfiguration?,
)

class LivenessModelPositionConfiguration(
    val threshold: Double
)

class LivenessModelPhotoConfiguration(
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