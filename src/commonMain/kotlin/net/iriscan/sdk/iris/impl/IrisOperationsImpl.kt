package net.iriscan.sdk.iris.impl

import net.iriscan.sdk.IrisConfig
import net.iriscan.sdk.core.algorithm.BiometricAlgorithmProperties
import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.iris.*
import net.iriscan.sdk.iris.record.IrisImageRecord
import net.iriscan.sdk.iris.record.IrisTemplateRecord

/**
 * @author Slava Gornostal
 */
internal class IrisOperationsImpl(private val config: IrisConfig) : IrisOperations {

    override fun extractor(): IrisExtractor = object : IrisExtractor {
        override fun extract(sample: IrisImageRecord): IrisImageRecord {
            TODO("Not implemented yet")
        }

        override fun extract(sample: IrisImageRecord, props: BiometricAlgorithmProperties): IrisImageRecord {
            TODO("Not implemented yet")
        }

        override fun extract(sample: Image): Image =
            extractInternal(sample, config.extractor)

        override fun extract(sample: Image, props: BiometricAlgorithmProperties): Image {
            require(props is IrisExtractProperties)
            return extractInternal(sample, props)
        }
    }

    override fun encoder(): IrisEncoder = object : IrisEncoder {
        override fun encode(sample: IrisImageRecord): IrisTemplateRecord {
            TODO("Not implemented yet")
        }

        override fun encode(sample: IrisImageRecord, props: BiometricAlgorithmProperties): IrisTemplateRecord {
            TODO("Not implemented yet")
        }

        override fun extractAndEncode(sample: Image): ByteArray {
            val texture = extractInternal(sample, config.extractor)
            return encodeInternal(texture, config.encoder)
        }

        override fun extractAndEncode(
            sample: Image,
            extractProps: BiometricAlgorithmProperties,
            encodeProps: BiometricAlgorithmProperties
        ): ByteArray {
            require(extractProps is IrisExtractProperties)
            require(encodeProps is IrisEncodeProperties)
            val texture = extractInternal(sample, extractProps)
            return encodeInternal(texture, encodeProps)
        }

        override fun encode(sample: Image): ByteArray = encodeInternal(sample, config.encoder)

        override fun encode(sample: Image, props: BiometricAlgorithmProperties): ByteArray {
            require(props is IrisEncodeProperties)
            return encodeInternal(sample, props)
        }

        override fun extractAndEncode(sample: IrisImageRecord): ByteArray {
            TODO("Not implemented yet")
        }

        override fun extractAndEncode(
            sample: IrisImageRecord,
            extractProps: BiometricAlgorithmProperties,
            encodeProps: BiometricAlgorithmProperties
        ): ByteArray {
            TODO("Not implemented yet")
        }
    }

    override fun matcher(): IrisMatcher = object : IrisMatcher {
        override fun matches(sample1: IrisTemplateRecord, sample2: IrisTemplateRecord): Boolean {
            TODO("Not implemented yet")
        }

        override fun matches(
            sample1: IrisTemplateRecord,
            sample2: IrisTemplateRecord,
            props: BiometricAlgorithmProperties
        ): Boolean {
            TODO("Not implemented yet")
        }

        override fun matches(sample1: ByteArray, sample2: ByteArray): Boolean =
            matchInternal(sample1, sample2) >= config.matcher.threshold

        override fun matches(sample1: ByteArray, sample2: ByteArray, props: BiometricAlgorithmProperties): Boolean {
            require(props is IrisMatchProperties)
            return matchInternal(sample1, sample2) >= props.threshold
        }
    }
}