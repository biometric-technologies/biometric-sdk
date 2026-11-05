package net.iriscan.sdk.finger

import com.machinezoo.fingerprintio.TemplateFormat
import com.machinezoo.sourceafis.FingerprintCompatibility
import com.machinezoo.sourceafis.FingerprintImage
import com.machinezoo.sourceafis.FingerprintImageOptions
import com.machinezoo.sourceafis.FingerprintMatcher
import com.machinezoo.sourceafis.FingerprintTemplate
import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.finger.impl.afis.FeatureExtractor
import net.iriscan.sdk.finger.impl.afis.MatcherEngine
import net.iriscan.sdk.finger.impl.afis.Probe
import net.iriscan.sdk.finger.impl.afis.SearchTemplate
import net.iriscan.sdk.finger.impl.encodeFinger
import net.iriscan.sdk.finger.impl.fingerMatrix
import net.iriscan.sdk.finger.impl.fingerScore
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The port must behave as SourceAFIS for Java 3.18.1, to the last bit: both read the same gray
 * pixels, extract on their own, and the results are compared.
 */
class SourceAfisParityTest {
    private class Sample(val name: String, val image: Image, val dpi: Double)

    private val samples: List<Sample> = buildList {
        for (seed in 1..4) {
            val finger = SyntheticFinger(seed)
            add(Sample("finger$seed", finger.capture(), 500.0))
            add(Sample("finger$seed-moved", finger.capture(dx = 11, dy = -7, angle = -0.2, noise = 18.0, noiseSeed = seed), 500.0))
            add(Sample("finger$seed-noisy", finger.capture(angle = 0.35, noise = 40.0, noiseSeed = 100 + seed), 500.0))
        }
        add(Sample("finger1-at-420dpi", SyntheticFinger(1).capture(noise = 6.0), 420.0))
        add(Sample("finger2-at-640dpi", SyntheticFinger(2).capture(dx = -5, noise = 6.0), 640.0))
    }

    private val theirs: List<FingerprintTemplate> = samples.map { sample ->
        val pixels = ByteArray(sample.image.size) { sample.image[it].toByte() }
        val options = FingerprintImageOptions().dpi(sample.dpi)
        FingerprintTemplate(FingerprintImage(sample.image.width, sample.image.height, pixels, options))
    }

    /** Engine parity before anything is written: native templates, full float precision. */
    @Test
    fun nativeScoresAreIdenticalToSourceAfis() {
        val ours = samples.map { SearchTemplate(FeatureExtractor.extract(fingerMatrix(it.image), it.dpi)) }
        val engine = MatcherEngine()
        for (i in samples.indices) {
            val matcher = FingerprintMatcher(theirs[i])
            val probe = Probe(ours[i])
            for (j in samples.indices)
                assertEquals(matcher.match(theirs[j]), engine.match(probe, ours[j]), "${samples[i].name} vs ${samples[j].name}")
        }
    }

    /** The SDK template is the record SourceAFIS exports, byte for byte. */
    @Test
    fun templatesAreTheIsoRecordsOfSourceAfis() {
        for (i in samples.indices) {
            val expected = FingerprintCompatibility.exportTemplates(TemplateFormat.ISO_19794_2_2005, theirs[i])
            assertContentEquals(expected, assertNotNull(encodeFinger(samples[i].image, samples[i].dpi)), samples[i].name)
        }
    }

    /** Records of version 2005 and 2011 score as SourceAFIS scores them after importing them. */
    @Test
    fun isoRecordsScoreAsInSourceAfis() {
        for (format in listOf(TemplateFormat.ISO_19794_2_2005, TemplateFormat.ISO_19794_2_2011)) {
            val records = theirs.map { FingerprintCompatibility.exportTemplates(format, it) }
            val imported = records.map { FingerprintCompatibility.importTemplate(it) }
            for (i in samples.indices) {
                val matcher = FingerprintMatcher(imported[i])
                for (j in samples.indices)
                    assertEquals(matcher.match(imported[j]), fingerScore(records[i], records[j]), "$format ${samples[i].name} vs ${samples[j].name}")
            }
        }
    }
}
