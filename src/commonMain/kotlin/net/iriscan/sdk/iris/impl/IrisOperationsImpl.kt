package net.iriscan.sdk.iris.impl

import net.iriscan.sdk.IrisConfig
import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.io.DataBytes
import net.iriscan.sdk.core.io.asByteArray
import net.iriscan.sdk.core.io.asDataBytes
import net.iriscan.sdk.core.record.BiometricRecord
import net.iriscan.sdk.iris.IrisEncoder
import net.iriscan.sdk.iris.IrisExtractor
import net.iriscan.sdk.iris.IrisMatcher
import net.iriscan.sdk.iris.IrisOperations

/**
 * @author Slava Gornostal
 */
internal class IrisOperationsImpl(private val config: IrisConfig) : IrisOperations {

    override fun extractor(): IrisExtractor = object : IrisExtractor {
        override fun extract(sample: BiometricRecord): BiometricRecord {
            TODO("Not implemented yet")
        }

        override fun extract(sample: Image): Image =
            extractInternal(sample, config.extractor)
    }

    override fun encoder(): IrisEncoder = object : IrisEncoder {
        override fun encode(sample: BiometricRecord): BiometricRecord {
            TODO("Not implemented yet")
        }

        override fun extractAndEncode(sample: Image): DataBytes {
            val texture = extractInternal(sample, config.extractor)
            return encodeInternal(texture, config.encoder).asDataBytes()
        }

        override fun encode(sample: Image): DataBytes = encodeInternal(sample, config.encoder)
            .asDataBytes()

        override fun extractAndEncode(sample: BiometricRecord): DataBytes {
            TODO("Not implemented yet")
        }
    }

    override fun matcher(): IrisMatcher = object : IrisMatcher {
        override fun matches(sample1: BiometricRecord, sample2: BiometricRecord): Boolean {
            TODO("Not implemented yet")
        }

        override fun matches(sample1: DataBytes, sample2: DataBytes): Boolean =
            matchInternal(sample1.asByteArray(), sample2.asByteArray()) >= config.matcher.threshold

        override fun matchesAny(sample1: BiometricRecord, samples: List<BiometricRecord>): Boolean {
            TODO("Not implemented yet")
        }

        override fun matchesAny(sample1: DataBytes, samples: List<DataBytes>): Boolean =
            samples.any {
                matchInternal(sample1.asByteArray(), it.asByteArray()) >= config.matcher.threshold
            }
    }
}