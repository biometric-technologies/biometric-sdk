package net.iriscan.sdk.face.impl

import net.iriscan.sdk.FaceConfig
import net.iriscan.sdk.core.image.NativeImage
import net.iriscan.sdk.core.io.DataBytes
import net.iriscan.sdk.face.*

/**
 * @author Slava Gornostal
 */
internal class FaceOperationsImpl(val config: FaceConfig) : FaceOperations {

    private val extractor: FaceExtractor? =
        config.extractor?.let {
            val internal = FaceExtractorInternal()
            object : FaceExtractor {
                override fun extract(nativeImage: NativeImage): NativeImage? =
                    internal.extract(nativeImage)
            }
        }

    private val encoder: FaceEncoder? =
        config.encoder?.let {
            val internal = FaceEncoderInternal(it.tfModel)
            object : FaceEncoder {
                override fun encode(nativeImage: NativeImage): DataBytes = internal.encode(nativeImage)
                override fun extractAndEncode(nativeImage: NativeImage): DataBytes? =
                    extractor().extract(nativeImage)?.let { internal.encode(it) }
            }
        }
    private val matcher: FaceMatcher? =
        config.matcher?.let {
            object : FaceMatcher {
                override fun matches(sample1: DataBytes, sample2: DataBytes): Boolean =
                    matchFaceNetTemplatesInternal(sample1, sample2) <= config.matcher.threshold

                override fun matchesAny(sample1: DataBytes, samples: List<DataBytes>): Boolean {
                    val minScore = samples
                        .minOf { matchFaceNetTemplatesInternal(sample1, it) }
                    return minScore <= config.matcher.threshold
                }

                override fun matchScore(sample1: DataBytes, sample2: DataBytes): Double =
                    matchFaceNetTemplatesInternal(sample1, sample2).toDouble()

                override fun matchScoreMin(sample1: DataBytes, samples: List<DataBytes>): Double =
                    samples.minOf { matchFaceNetTemplatesInternal(sample1, it).toDouble() }
            }
        }

    private val liveness: FaceLivenessDetection? =
        config.liveness?.let {
            val internal = FaceLivenessDetectionInternal(it.tfModel)
            object : FaceLivenessDetection {
                override fun validate(nativeImage: NativeImage): Boolean =
                    internal.validate(nativeImage)

                override fun extractAndValidate(nativeImage: NativeImage): Boolean? =
                    extractor().extract(nativeImage)?.let { internal.validate(it) }

                override fun score(nativeImage: NativeImage): Double =
                    internal.score(nativeImage)

                override fun extractAndScore(nativeImage: NativeImage): Double? =
                    extractor().extract(nativeImage)?.let { internal.score(it) }
            }
        }

    override fun extractor(): FaceExtractor =
        extractor ?: throw IllegalStateException("Please initialize SDK with face extractor")

    override fun encoder(): FaceEncoder =
        encoder ?: throw IllegalStateException("Please initialize SDK with face encoder")

    override fun matcher(): FaceMatcher =
        matcher ?: throw IllegalStateException("Please initialize SDK with face matcher")

    override fun liveness(): FaceLivenessDetection =
        liveness ?: throw IllegalStateException("Please initialize SDK with face liveness")
}