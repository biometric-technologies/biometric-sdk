package net.iriscan.sdk.face.impl

import net.iriscan.sdk.FaceConfig
import net.iriscan.sdk.core.image.Image
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
    private val encoder = FaceEncoderInternal(config.faceNetModel, config.encoder.faceNetModel)

    override fun extractor(): FaceExtractor = object : FaceExtractor {
        override fun extract(sample: FaceImageRecord): FaceImageRecord {
            TODO("Not implemented yet")
        }

        override fun extract(sample: Image): Image = extractor.extract(sample)
    }

    override fun encoder(): FaceEncoder = object : FaceEncoder {
        override fun encode(sample: FaceImageRecord): FaceTemplateRecord {
            TODO("Not implemented yet")
        }

        override fun encode(sample: Image): ByteArray = encoder.encode(sample)

        override fun extractAndEncode(sample: FaceImageRecord): FaceTemplateRecord {
            TODO("Not implemented yet")
        }

        override fun extractAndEncode(sample: Image): ByteArray = encoder.encode(extractor.extract(sample))

    }

    override fun matcher(): FaceMatcher = object : FaceMatcher {
        override fun matches(sample1: FaceTemplateRecord, sample2: FaceTemplateRecord): Boolean {
            TODO("Not implemented yet")
        }

        override fun matches(sample1: ByteArray, vararg samples: ByteArray): Boolean {
            val scoreAvg = samples.toList()
                .map { matchFaceNetTemplatesInternal(sample1, it) }
                .average()
            return scoreAvg <= config.matcher.threshold
        }

    }
}