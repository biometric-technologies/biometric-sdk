package net.iriscan.sdk.finger.impl

import net.iriscan.sdk.FingerConfig
import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.image.ImageColorType
import net.iriscan.sdk.core.io.DataBytes
import net.iriscan.sdk.core.io.asByteArray
import net.iriscan.sdk.core.io.asDataBytes
import net.iriscan.sdk.finger.FingerEncoder
import net.iriscan.sdk.finger.FingerMatch
import net.iriscan.sdk.finger.FingerMatcher
import net.iriscan.sdk.finger.FingerOperations
import net.iriscan.sdk.finger.impl.afis.DoubleMatrix
import net.iriscan.sdk.finger.impl.afis.FeatureExtractor
import net.iriscan.sdk.finger.impl.afis.MatcherEngine
import net.iriscan.sdk.finger.impl.afis.Probe
import net.iriscan.sdk.finger.impl.afis.SearchTemplate

internal class FingerOperationsImpl(private val config: FingerConfig) : FingerOperations {
    override fun encoder(): FingerEncoder = object : FingerEncoder {
        override fun extractAndEncode(sample: Image): DataBytes? = extractAndEncode(sample, config.encoder.dpi)

        override fun extractAndEncode(sample: Image, dpi: Double): DataBytes? =
            encodeFinger(sample, dpi)?.asDataBytes()
    }

    override fun matcher(): FingerMatcher = object : FingerMatcher {
        override fun score(sample1: DataBytes, sample2: DataBytes): Double =
            fingerScore(sample1.asByteArray(), sample2.asByteArray())

        override fun matches(sample1: DataBytes, sample2: DataBytes): Boolean =
            score(sample1, sample2) >= config.matcher.threshold

        override fun matchesAny(sample1: DataBytes, samples: List<DataBytes>): Boolean =
            identify(sample1, samples) != null

        override fun identify(sample: DataBytes, samples: List<DataBytes>): FingerMatch? {
            val (index, score) = identifyFinger(sample.asByteArray(), samples.map { it.asByteArray() }) ?: return null
            return if (score >= config.matcher.threshold) FingerMatch(index, score) else null
        }
    }
}

/** Pixel intensities as SourceAFIS reads them: 0 is white, 1 is black. */
internal fun fingerMatrix(image: Image): DoubleMatrix {
    val matrix = DoubleMatrix(image.width, image.height)
    for (y in 0 until image.height) for (x in 0 until image.width) {
        val c = image[x, y]
        matrix[x, y] = when (image.colorType) {
            // FingerprintImage(width, height, pixels) in SourceAFIS
            ImageColorType.GRAY -> 1 - (c and 0xFF) / 255.0
            // FingerprintImage(encoded bytes) in SourceAFIS
            ImageColorType.RGB -> 1 - ((c and 0xFF) + ((c shr 8) and 0xFF) + ((c shr 16) and 0xFF)) * (1.0 / (3.0 * 255.0))
            ImageColorType.BINARY -> if (c != 0) 0.0 else 1.0
        }
    }
    return matrix
}

internal fun encodeFinger(image: Image, dpi: Double): ByteArray? {
    require(dpi in 20.0..20_000.0) { "dpi must be between 20 and 20000" }
    require(image.width > 0 && image.height > 0 && image.size == image.width * image.height) { "Empty or broken image" }
    val template = SearchTemplate(FeatureExtractor.extract(fingerMatrix(image), dpi))
    if (template.minutiae.isEmpty()) return null
    return FingerIso.export2005(template)
}

/** First finger view of an ISO/IEC 19794-2 record, ready for matching. */
internal fun readFinger(record: ByteArray): SearchTemplate =
    FingerIso.import(record).firstOrNull() ?: throw IllegalArgumentException("The record has no finger view")

internal fun fingerScore(record1: ByteArray, record2: ByteArray): Double =
    MatcherEngine().match(Probe(readFinger(record1)), readFinger(record2))

/** Best match in a gallery: index and score, or null when the gallery is empty. */
internal fun identifyFinger(probe: ByteArray, gallery: List<ByteArray>): Pair<Int, Double>? {
    if (gallery.isEmpty()) return null
    val prepared = Probe(readFinger(probe))
    val engine = MatcherEngine()
    var bestIndex = -1
    var bestScore = -1.0
    for (i in gallery.indices) {
        val score = engine.match(prepared, readFinger(gallery[i]))
        if (score > bestScore) {
            bestScore = score
            bestIndex = i
        }
    }
    return bestIndex to bestScore
}
