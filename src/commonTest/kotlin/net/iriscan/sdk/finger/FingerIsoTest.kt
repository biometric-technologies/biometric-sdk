package net.iriscan.sdk.finger

import net.iriscan.sdk.finger.impl.FingerIso
import net.iriscan.sdk.finger.impl.afis.FeatureExtractor
import net.iriscan.sdk.finger.impl.afis.SearchTemplate
import net.iriscan.sdk.finger.impl.fingerMatrix
import net.iriscan.sdk.finger.impl.encodeFinger
import net.iriscan.sdk.finger.impl.fingerScore
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FingerIsoTest {
    private val finger = SyntheticFinger(1)
    private val enrolled = assertNotNull(encodeFinger(finger.capture(), 500.0))
    private val probe = assertNotNull(encodeFinger(finger.capture(dx = 9, dy = -6, angle = 0.12, noise = 12.0), 500.0))
    private val iso = enrolled

    /** The template before it is written: SourceAFIS directions in full float precision. */
    private val native = SearchTemplate(FeatureExtractor.extract(fingerMatrix(finger.capture()), 500.0))

    private fun u16(bytes: ByteArray, at: Int) = (bytes[at].toInt() and 0xFF shl 8) or (bytes[at + 1].toInt() and 0xFF)

    @Test
    fun exportHasTheIso2005Layout() {
        val template = native
        val n = template.minutiae.size
        assertContentEquals("FMR\u0000 20\u0000".encodeToByteArray(), iso.copyOf(8))
        assertEquals(30 + 6 * n, iso.size)
        assertEquals(iso.size, (u16(iso, 8) shl 16) or u16(iso, 10))
        assertEquals(template.width.toInt(), u16(iso, 14))
        assertEquals(template.height.toInt(), u16(iso, 16))
        assertEquals(197, u16(iso, 18))
        assertEquals(197, u16(iso, 20))
        assertEquals(1, iso[22].toInt())
        assertEquals(n, iso[27].toInt() and 0xFF)
        for (i in 0 until n) {
            val m = template.minutiae[i]
            val at = 28 + 6 * i
            val type = if (m.type.name == "BIFURCATION") 2 else 1
            assertEquals((type shl 14) or m.x.toInt(), u16(iso, at))
            assertEquals(m.y.toInt(), u16(iso, at + 2))
        }
        assertEquals(0, u16(iso, iso.size - 2))
    }

    @Test
    fun importedRecordKeepsTheMinutiaeAndStillMatches() {
        val original = native
        val imported = FingerIso.import(iso).single()
        assertEquals(original.minutiae.size, imported.minutiae.size)
        val step = 2 * PI / 256
        for (m in original.minutiae) {
            val match = imported.minutiae.any { i ->
                abs(i.x - m.x) <= 1 && abs(i.y - m.y) <= 1 && i.type == m.type &&
                    min(abs(i.direction - m.direction), (2 * PI - abs(i.direction - m.direction)).toFloat()) <= step
            }
            assertTrue(match, "minutia ${m.x},${m.y} lost")
        }
        val score = fingerScore(probe, iso)
        assertTrue(score >= 40, "imported record scored $score")
    }

    @Test
    fun iso2011RecordReadsLikeThe2005One() {
        val record2011 = to2011(iso)
        assertContentEquals(iso, FingerIso.export2005(FingerIso.import(record2011).single()))
        assertEquals(fingerScore(probe, iso), fingerScore(probe, record2011))
    }

    @Test
    fun everyFingerViewBecomesATemplate() {
        val other = assertNotNull(encodeFinger(SyntheticFinger(2).capture(), 500.0))
        val views = FingerIso.import(twoViews(iso, other))
        assertEquals(2, views.size)
        assertContentEquals(other, FingerIso.export2005(views[1]))
        assertEquals(fingerScore(probe, iso), fingerScore(probe, twoViews(iso, other)), "first view is matched")
    }

    @Test
    fun otherDataIsRejected() {
        assertFailsWith<IllegalArgumentException> { FingerIso.import("not a record".encodeToByteArray()) }
        assertFailsWith<IllegalArgumentException> { FingerIso.import(iso.copyOf(iso.size - 7)) }
        assertTrue(!FingerIso.isIsoRecord(ByteArray(40)) && FingerIso.isIsoRecord(iso))
    }

    /** Two views of the 2005 records [a] and [b], which must share the general header values. */
    private fun twoViews(a: ByteArray, b: ByteArray): ByteArray {
        val out = a + b.copyOfRange(24, b.size)
        out[8] = (out.size ushr 24).toByte(); out[9] = (out.size ushr 16).toByte()
        out[10] = (out.size ushr 8).toByte(); out[11] = out.size.toByte()
        out[22] = 2
        out[a.size + 1] = 0x10 // view 1 of the same finger
        return out
    }

    /** The same minutiae as an ISO/IEC 19794-2:2011 record with 5-byte minutiae and a quality block. */
    private fun to2011(record2005: ByteArray): ByteArray {
        val width = u16(record2005, 14)
        val height = u16(record2005, 16)
        val count = record2005[27].toInt() and 0xFF
        val body = ArrayList<Byte>()
        fun u8(v: Int) { body.add(v.toByte()) }
        fun u16(v: Int) { u8(v ushr 8); u8(v) }
        repeat(9) { u8(0xFF) } // capture date and time unknown
        u8(0); u16(0); u16(0) // device technology, vendor, type
        u8(1); u8(80); u16(0x0101); u16(1) // one quality block
        u8(0); u8(0) // finger position, representation number
        u16(197); u16(197)
        u8(0) // impression type
        u16(width); u16(height)
        u8(0x50) // 5-byte minutiae, ridge ending type 0
        u8(count)
        for (i in 0 until count) {
            val at = 28 + 6 * i
            for (k in 0 until 5) u8(record2005[at + k].toInt() and 0xFF)
        }
        u16(0)
        val representation = 4 + body.size
        val total = 15 + representation
        val out = ArrayList<Byte>()
        "FMR\u0000030\u0000".encodeToByteArray().forEach { out.add(it) }
        for (s in intArrayOf(24, 16, 8, 0)) out.add((total ushr s).toByte())
        out.add(0); out.add(1) // one representation
        out.add(0) // no certification
        for (s in intArrayOf(24, 16, 8, 0)) out.add((representation ushr s).toByte())
        out.addAll(body)
        return out.toByteArray()
    }
}
