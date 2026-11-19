package net.iriscan.sdk.iris

import net.iriscan.sdk.iris.impl.IrisProbe
import net.iriscan.sdk.iris.impl.IrisTemplate
import net.iriscan.sdk.iris.impl.MASKED_PIXEL
import net.iriscan.sdk.iris.impl.compare
import net.iriscan.sdk.iris.impl.encodeNormalized
import net.iriscan.sdk.iris.impl.extractInternal
import net.iriscan.sdk.iris.impl.extractNormalized
import net.iriscan.sdk.iris.impl.fft
import net.iriscan.sdk.iris.impl.identify
import net.iriscan.sdk.iris.impl.legacyMatch
import net.iriscan.sdk.iris.impl.matchScore
import net.iriscan.sdk.iris.impl.matches
import net.iriscan.sdk.iris.impl.normalizeIris
import net.iriscan.sdk.iris.impl.segmentIris
import net.iriscan.sdk.iris.impl.toGray
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IrisPipelineTest {
    private val extract = IrisExtractProperties()
    private val encode = IrisEncodeProperties()
    private val match = IrisMatchProperties()

    private fun template(subject: Int, capture: SyntheticIris.Capture): IrisTemplate {
        val normalized = extractNormalized(SyntheticIris(subject).render(capture), extract)
        assertNotNull(normalized, "extraction failed for subject $subject")
        return encodeNormalized(normalized, encode)
    }

    @Test
    fun fftRoundTripAndKnownSpectrum() {
        val n = 64
        val re = DoubleArray(n) { cos(2 * PI * 5 * it / n) }
        val im = DoubleArray(n)
        val orig = re.copyOf()
        fft(re, im, inverse = false)
        assertEquals(n / 2.0, re[5], 1e-9)
        assertEquals(n / 2.0, re[n - 5], 1e-9)
        assertEquals(0.0, re[6], 1e-9)
        fft(re, im, inverse = true)
        for (i in 0 until n) assertEquals(orig[i], re[i], 1e-9)
    }

    @Test
    fun segmentationFindsPupilAndIris() {
        for (k in 0 until 8) {
            val cap = SyntheticIris.randomCapture(500 + k)
            val seg = segmentIris(toGray(SyntheticIris(k).render(cap)), extract)
            assertNotNull(seg)
            assertTrue(hypot(seg.pupil.x - cap.pupilX, seg.pupil.y - cap.pupilY) < 3, "pupil center, capture $k")
            assertTrue(abs(seg.pupil.r - cap.pupilRadius) < 3, "pupil radius, capture $k")
            assertTrue(hypot(seg.iris.x - cap.cx, seg.iris.y - cap.cy) < 5, "iris center, capture $k")
            assertTrue(abs(seg.iris.r - cap.irisRadius) < 5, "iris radius, capture $k")
        }
    }

    @Test
    fun eyelidIsMaskedWhenItCoversTheIris() {
        val covered = SyntheticIris.Capture(upperLid = 0.5, seed = 1)
        val open = SyntheticIris.Capture(upperLid = 1.3, lowerLid = 1.3, seed = 1)
        val iris = SyntheticIris(3)
        val c = extractNormalized(iris.render(covered), extract)!!
        val o = extractNormalized(iris.render(open), extract)!!
        assertTrue(o.validFraction > 0.9, "open eye should be mostly valid: ${o.validFraction}")
        assertTrue(c.validFraction < o.validFraction - 0.1, "covered eye should lose iris area: ${c.validFraction}")
    }

    @Test
    fun extractionDoesNotModifyInputAndMarksOcclusions() {
        val img = SyntheticIris(7).render(SyntheticIris.Capture(upperLid = 0.5, seed = 2))
        val before = img.colors.copyOf()
        val texture = extractInternal(img, extract)
        assertNotNull(texture)
        assertContentEquals(before, img.colors)
        assertEquals(extract.normalizedWidth, texture.width)
        assertEquals(extract.normalizedHeight, texture.height)
        assertTrue(texture.colors.any { it == MASKED_PIXEL })
    }

    @Test
    fun templateSerializationRoundTrip() {
        val t = template(11, SyntheticIris.randomCapture(11))
        val bytes = t.toBytes()
        assertEquals(IrisTemplate.HEADER_SIZE + 2 * t.totalBits / 8, bytes.size)
        val back = IrisTemplate.fromBytes(bytes)
        assertContentEquals(t.code, back.code)
        assertContentEquals(t.mask, back.mask)
    }

    @Test
    fun shiftIsCircular() {
        val rnd = Random(1)
        val words = 16 * 128 * 2 / 64
        val t = IrisTemplate(16, 128, LongArray(words) { rnd.nextLong() }, LongArray(words) { rnd.nextLong() })
        assertContentEquals(t.code, t.shifted(128).code)
        assertContentEquals(t.code, t.shifted(5).shifted(-5).code)
        assertContentEquals(t.shifted(3).shifted(4).mask, t.shifted(7).mask)
    }

    @Test
    fun sameIrisMatchesDifferentIrisDoesNot() {
        val a1 = template(21, SyntheticIris.Capture(rotationDeg = 0.0, pupilRatio = 0.35, seed = 1))
        val a2 = template(21, SyntheticIris.Capture(cx = 150.0, rotationDeg = 8.0, pupilRatio = 0.5, upperLid = 0.65, seed = 2))
        val b = template(22, SyntheticIris.Capture(rotationDeg = 0.0, pupilRatio = 0.35, seed = 1))
        val genuine = compare(IrisProbe(a1, match), a2, match)
        val impostor = compare(IrisProbe(a1, match), b, match)
        assertTrue(genuine.score > 0.8, "genuine score ${genuine.score}")
        assertTrue(impostor.score < 0.62, "impostor score ${impostor.score}")
        // 8 degrees of rotation is about 3 template columns
        assertTrue(abs(genuine.shift) in 2..4, "shift ${genuine.shift}")
        assertTrue(matches(a1.toBytes(), a2.toBytes(), match))
        assertTrue(!matches(a1.toBytes(), b.toBytes(), match))
    }

    @Test
    fun identifyFindsTheEnrolledPerson() {
        val gallery = (0 until 12).map { template(100 + it, SyntheticIris.randomCapture(1000 + it)).toBytes() }
        val probe = template(105, SyntheticIris.randomCapture(2005)).toBytes()
        val result = identify(probe, gallery, match)
        assertNotNull(result)
        assertEquals(5, result.first)
        assertTrue(result.second >= match.threshold)
    }

    @Test
    fun scoreIsSymmetricWithinRotationStep() {
        val a = template(31, SyntheticIris.randomCapture(1)).toBytes()
        val b = template(31, SyntheticIris.randomCapture(2)).toBytes()
        assertEquals(matchScore(a, b, match), matchScore(b, a, match), 1e-9)
    }

    @Test
    fun legacyTemplatesStillCompareAndNeverMatchNewOnes() {
        val legacy1 = ByteArray(127) { (it % 2).toByte() }
        val legacy2 = legacy1.copyOf().also { it[0] = 1 }
        assertEquals(legacyMatch(legacy1, legacy2), matchScore(legacy1, legacy2, match), 1e-12)
        assertTrue(matches(legacy1, legacy2, match))
        val v2 = template(41, SyntheticIris.randomCapture(3)).toBytes()
        assertEquals(0.0, matchScore(legacy1, v2, match))
    }

    @Test
    fun encodingIgnoresBrightnessAndContrast() {
        val img = SyntheticIris(51).render(SyntheticIris.randomCapture(9))
        val gray = toGray(img)
        val seg = segmentIris(gray, extract)!!
        val n = normalizeIris(gray, seg, extract)
        val t1 = encodeNormalized(n, encode)
        for (i in n.values.indices) n.values[i] = n.values[i] * 0.6f + 40f
        val t2 = encodeNormalized(n, encode)
        val r = compare(IrisProbe(t1, match), t2, match)
        assertEquals(1.0, r.score, 0.02)
        assertEquals(0, r.shift)
    }

    @Test
    fun syntheticGenuineAndImpostorScoresSeparate() {
        val subjects = 6
        val templates = (0 until subjects).flatMap { s ->
            (0 until 3).map { c -> s to template(200 + s, SyntheticIris.randomCapture(300 + s * 10 + c)) }
        }
        var minGenuine = 1.0
        var maxImpostor = 0.0
        for (i in templates.indices) for (j in i + 1 until templates.size) {
            val score = compare(IrisProbe(templates[i].second, match), templates[j].second, match).score
            if (templates[i].first == templates[j].first) minGenuine = minOf(minGenuine, score)
            else maxImpostor = maxOf(maxImpostor, score)
        }
        assertTrue(minGenuine > match.threshold, "min genuine $minGenuine")
        assertTrue(maxImpostor < match.threshold, "max impostor $maxImpostor")
    }
}
