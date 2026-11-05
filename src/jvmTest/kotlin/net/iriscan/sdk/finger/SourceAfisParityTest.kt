package net.iriscan.sdk.finger

import com.machinezoo.sourceafis.FingerprintImage
import com.machinezoo.sourceafis.FingerprintImageOptions
import com.machinezoo.sourceafis.FingerprintMatcher
import com.machinezoo.sourceafis.FingerprintTemplate
import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.finger.impl.encodeFinger
import net.iriscan.sdk.finger.impl.fingerScore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The port must give the same scores as SourceAFIS for Java, to the last bit: both read the same
 * gray pixels, extract on their own, and every pair is compared both ways.
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

    private fun sourceAfis(sample: Sample): FingerprintTemplate {
        val pixels = ByteArray(sample.image.size) { sample.image[it].toByte() }
        val options = FingerprintImageOptions().dpi(sample.dpi)
        return FingerprintTemplate(FingerprintImage(sample.image.width, sample.image.height, pixels, options))
    }

    @Test
    fun scoresAreIdenticalToSourceAfis() {
        val theirs = samples.map(::sourceAfis)
        val ours = samples.map { assertNotNull(encodeFinger(it.image, it.dpi), it.name) }
        for (i in samples.indices) {
            val matcher = FingerprintMatcher(theirs[i])
            for (j in samples.indices) {
                val expected = matcher.match(theirs[j])
                val actual = fingerScore(ours[i], ours[j])
                assertEquals(expected, actual, "${samples[i].name} vs ${samples[j].name}")
            }
        }
    }
}
