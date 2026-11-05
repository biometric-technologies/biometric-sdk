package net.iriscan.sdk.finger

import net.iriscan.sdk.FingerConfig
import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.image.ImageColorType
import net.iriscan.sdk.core.io.asDataBytes
import net.iriscan.sdk.finger.impl.FingerOperationsImpl
import net.iriscan.sdk.finger.impl.FingerTemplate
import net.iriscan.sdk.finger.impl.encodeFinger
import net.iriscan.sdk.finger.impl.fingerScore
import net.iriscan.sdk.finger.impl.identifyFinger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FingerTest {
    private val first = SyntheticFinger(1)
    private val enrolled = assertNotNull(encodeFinger(first.capture(), 500.0))
    private val sameFinger = assertNotNull(encodeFinger(first.capture(dx = 9, dy = -6, angle = 0.12, noise = 12.0), 500.0))
    private val others = (2..5).map { assertNotNull(encodeFinger(SyntheticFinger(it).capture(), 500.0)) }

    @Test
    fun sameFingerMatchesOtherFingersDoNot() {
        val genuine = fingerScore(enrolled, sameFinger)
        assertTrue(genuine >= 40, "same finger scored $genuine")
        for (other in others) {
            val impostor = fingerScore(enrolled, other)
            assertTrue(impostor < 40, "another finger scored $impostor")
        }
    }

    @Test
    fun identifyFindsTheFingerInAGallery() {
        val gallery = others.take(2) + enrolled + others.drop(2)
        val (index, score) = assertNotNull(identifyFinger(sameFinger, gallery))
        assertEquals(2, index)
        assertEquals(fingerScore(sameFinger, enrolled), score)
        assertNull(identifyFinger(sameFinger, emptyList()))
    }

    @Test
    fun operationsApplyTheThreshold() {
        val strict = FingerOperationsImplForTest(threshold = 1e9)
        val default = FingerOperationsImplForTest(threshold = 40.0)
        assertTrue(default.matches(enrolled, sameFinger))
        assertTrue(!strict.matches(enrolled, sameFinger))
        assertNull(strict.identify(enrolled, listOf(sameFinger)))
        assertEquals(0, default.identify(sameFinger, listOf(enrolled))?.index)
    }

    @Test
    fun templateSurvivesSerialization() {
        val restored = FingerTemplate.toBytes(FingerTemplate.fromBytes(enrolled))
        assertContentEquals(enrolled, restored)
        assertEquals(fingerScore(enrolled, sameFinger), fingerScore(restored, sameFinger))
    }

    @Test
    fun brokenTemplatesAreRejected() {
        assertFailsWith<IllegalArgumentException> { fingerScore(byteArrayOf(1, 2, 3), enrolled) }
        assertFailsWith<IllegalArgumentException> { fingerScore(enrolled.copyOf(enrolled.size - 1), enrolled) }
        val wrongType = enrolled.copyOf().also { it[it.size - 1] = 7 }
        assertFailsWith<IllegalArgumentException> { fingerScore(wrongType, enrolled) }
    }

    @Test
    fun blankImageHasNoTemplate() {
        assertNull(encodeFinger(Image(300, 300, ImageColorType.GRAY, IntArray(300 * 300) { 255 }), 500.0))
    }

    @Test
    fun resolutionIsChecked() {
        assertFailsWith<IllegalArgumentException> { encodeFinger(first.capture(), 10.0) }
        assertFailsWith<IllegalArgumentException> { FingerEncodeProperties(dpi = 50_000.0) }
    }

    private class FingerOperationsImplForTest(threshold: Double) {
        private val matcher = FingerOperationsImpl(FingerConfig(matcher = FingerMatchProperties(threshold))).matcher()

        fun matches(a: ByteArray, b: ByteArray) = matcher.matches(a.asDataBytes(), b.asDataBytes())
        fun identify(a: ByteArray, gallery: List<ByteArray>) = matcher.identify(a.asDataBytes(), gallery.map { it.asDataBytes() })
    }
}
