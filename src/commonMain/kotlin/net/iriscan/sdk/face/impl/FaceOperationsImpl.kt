package net.iriscan.sdk.face.impl

import net.iriscan.sdk.FaceConfig
import net.iriscan.sdk.core.image.NativeImage
import net.iriscan.sdk.core.io.DataBytes
import net.iriscan.sdk.core.utils.generateTraceID
import net.iriscan.sdk.face.*

/**
 * @author Slava Gornostal
 */
internal class FaceOperationsImpl(val config: FaceConfig) : FaceOperations {

    private val extractor: FaceExtractor? =
        config.extractor?.let {
            val faceExtractorInternal = FaceExtractorInternal()
            object : FaceExtractor {
                override fun extract(nativeImage: NativeImage): NativeImage? =
                    faceExtractorInternal.extract(nativeImage, true)
            }
        }

    private val encoder: FaceEncoder? =
        config.encoder?.let {
            val faceEncoderInternal = FaceEncoderInternal(it.tfModel)
            val faceExtractorInternal = FaceExtractorInternal()
            object : FaceEncoder {
                override fun encode(nativeImage: NativeImage): DataBytes = faceEncoderInternal.encode(nativeImage)
                override fun extractAndEncode(nativeImage: NativeImage): DataBytes? {
                    val traceId = generateTraceID()
                    return faceExtractorInternal.extract(nativeImage, true, traceId)
                        ?.let { faceEncoderInternal.encode(it, traceId) }
                }
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
            val livenessDetectorInternal = FaceLivenessDetectionInternal(it.tfModel)
            val extractorInternal = FaceExtractorInternal()
            object : FaceLivenessDetection {
                override fun validate(nativeImage: NativeImage): Boolean =
                    livenessDetectorInternal.validate(nativeImage)

                override fun extractAndValidate(nativeImage: NativeImage): Boolean? {
                    val traceId = generateTraceID()
                    return extractorInternal.extract(nativeImage, false, traceId)
                        ?.let { livenessDetectorInternal.validate(it, traceId) }
                }

                override fun score(nativeImage: NativeImage): Double =
                    livenessDetectorInternal.score(nativeImage)

                override fun extractAndScore(nativeImage: NativeImage): Double? {
                    val traceId = generateTraceID()
                    return extractorInternal.extract(nativeImage, false, traceId)
                        ?.let { livenessDetectorInternal.score(it, traceId) }
                }
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