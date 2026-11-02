package net.iriscan.sdk.iris.impl

import net.iriscan.sdk.IrisConfig
import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.io.DataBytes
import net.iriscan.sdk.core.io.asByteArray
import net.iriscan.sdk.core.io.asDataBytes
import net.iriscan.sdk.core.record.BiometricRecord
import net.iriscan.sdk.iris.IrisEncoder
import net.iriscan.sdk.iris.IrisExtractor
import net.iriscan.sdk.iris.IrisMatch
import net.iriscan.sdk.iris.IrisMatcher
import net.iriscan.sdk.iris.IrisOperations
import net.iriscan.sdk.iris.IrisQuality
import net.iriscan.sdk.iris.IrisQualityAssessor

/**
 * @author Slava Gornostal
 */
internal class IrisOperationsImpl(private val config: IrisConfig) : IrisOperations {

    override fun extractor(): IrisExtractor = object : IrisExtractor {
        override fun extract(sample: BiometricRecord): BiometricRecord {
            TODO("Not implemented yet")
        }

        override fun extract(sample: Image): Image? =
            extractInternal(sample, config.extractor)
    }

    override fun encoder(): IrisEncoder = object : IrisEncoder {
        override fun encode(sample: BiometricRecord): BiometricRecord {
            TODO("Not implemented yet")
        }

        override fun extractAndEncode(sample: Image): DataBytes? {
            val normalized = extractNormalized(sample, config.extractor) ?: return null
            return encodeNormalized(normalized, config.encoder).toBytes().asDataBytes()
        }

        override fun encode(sample: Image): DataBytes {
            val normalized = NormalizedIris.fromImage(
                sample, config.extractor.normalizedHeight, config.extractor.normalizedWidth
            )
            return encodeNormalized(normalized, config.encoder).toBytes().asDataBytes()
        }

        override fun extractAndEncode(sample: BiometricRecord): DataBytes {
            TODO("Not implemented yet")
        }
    }

    override fun matcher(): IrisMatcher = object : IrisMatcher {
        override fun matches(sample1: BiometricRecord, sample2: BiometricRecord): Boolean {
            TODO("Not implemented yet")
        }

        override fun matches(sample1: DataBytes, sample2: DataBytes): Boolean =
            matches(sample1.asByteArray(), sample2.asByteArray(), config.matcher)

        override fun matchesAny(sample1: BiometricRecord, samples: List<BiometricRecord>): Boolean {
            TODO("Not implemented yet")
        }

        override fun matchesAny(sample1: DataBytes, samples: List<DataBytes>): Boolean =
            identify(sample1, samples) != null

        override fun score(sample1: DataBytes, sample2: DataBytes): Double =
            matchScore(sample1.asByteArray(), sample2.asByteArray(), config.matcher)

        override fun identify(sample: DataBytes, samples: List<DataBytes>): IrisMatch? {
            val probe = sample.asByteArray()
            val (index, score) = identify(probe, samples.map { it.asByteArray() }, config.matcher) ?: return null
            val threshold = if (IrisTemplate.isV2(probe)) config.matcher.threshold else config.matcher.legacyThreshold
            return if (score >= threshold) IrisMatch(index, score) else null
        }
    }

    override fun quality(): IrisQualityAssessor = object : IrisQualityAssessor {
        override fun assess(sample: Image): IrisQuality? = assessQuality(sample, config.extractor, config.quality)
    }
}
