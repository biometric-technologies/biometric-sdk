package net.iriscan.sdk.face.impl

import net.iriscan.sdk.FaceConfig
import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.image.NativeImage
import net.iriscan.sdk.core.io.DataBytes
import net.iriscan.sdk.face.FaceEncoder
import net.iriscan.sdk.face.FaceExtractor
import net.iriscan.sdk.face.FaceMatcher
import net.iriscan.sdk.face.FaceOperations
import net.iriscan.sdk.face.record.FaceImageRecord
import net.iriscan.sdk.face.record.FaceTemplateRecord

/**
 * @author Slava Gornostal
 */
internal class FaceOperationsImpl(val config: FaceConfig) : FaceOperations {
    private val extractor = FaceExtractorInternal()
    private val encoder = FaceEncoderInternal(config.encoder.faceNetModel)

    override fun extractor(): FaceExtractor = object : FaceExtractor {
        override fun extract(record: FaceImageRecord): FaceImageRecord {
            TODO("Not implemented yet")
        }

        override fun extract(sdkImage: Image): Image? = extractor.extract(sdkImage)

        override fun extract(nativeImage: NativeImage): NativeImage? = extractor.extract(nativeImage)
    }

    override fun encoder(): FaceEncoder = object : FaceEncoder {
        override fun encode(record: FaceImageRecord): FaceTemplateRecord {
            TODO("Not implemented yet")
        }

        override fun encode(sdkImage: Image): DataBytes = encoder.encode(sdkImage)
        override fun encode(nativeImage: NativeImage): DataBytes = encoder.encode(nativeImage)

        override fun extractAndEncode(record: FaceImageRecord): FaceTemplateRecord {
            TODO("Not implemented yet")
        }

        override fun extractAndEncode(sdkImage: Image): DataBytes? =
            extractor.extract(sdkImage)?.let { encoder.encode(it) }

        override fun extractAndEncode(nativeImage: NativeImage): DataBytes? =
            extractor.extract(nativeImage)?.let { encoder.encode(it) }

    }

    override fun matcher(): FaceMatcher = object : FaceMatcher {
        override fun matches(sample1: FaceTemplateRecord, sample2: FaceTemplateRecord): Boolean {
            TODO("Not implemented yet")
        }

        override fun matches(sample1: DataBytes, sample2: DataBytes): Boolean =
            matchFaceNetTemplatesInternal(sample1, sample2) <= config.matcher.threshold

        override fun matchesAny(sample1: FaceTemplateRecord, samples: List<FaceTemplateRecord>): Boolean {
            TODO("Not implemented yet")
        }

        override fun matchesAny(sample1: DataBytes, samples: List<DataBytes>): Boolean {
            val minScore = samples
                .minOf { matchFaceNetTemplatesInternal(sample1, it) }
            return minScore <= config.matcher.threshold
        }

        override fun matchScore(sample1: FaceTemplateRecord, sample2: FaceTemplateRecord): Double {
            TODO("Not yet implemented")
        }

        override fun matchScore(sample1: DataBytes, sample2: DataBytes): Double =
            matchFaceNetTemplatesInternal(sample1, sample2).toDouble()

        override fun matchScoreMin(sample1: FaceTemplateRecord, samples: List<FaceTemplateRecord>): Double {
            TODO("Not yet implemented")
        }

        override fun matchScoreMin(sample1: DataBytes, samples: List<DataBytes>): Double =
            samples.minOf { matchFaceNetTemplatesInternal(sample1, it).toDouble() }

    }
}