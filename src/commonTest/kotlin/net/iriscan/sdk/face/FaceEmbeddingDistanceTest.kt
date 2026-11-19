package net.iriscan.sdk.face

import net.iriscan.sdk.face.impl.NOT_COMPARABLE_DISTANCE
import net.iriscan.sdk.face.impl.embeddingDistance
import net.iriscan.sdk.face.impl.toFloatArray
import net.iriscan.sdk.testutil.hex
import net.iriscan.sdk.testutil.pseudoRandomBytes
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Distances of templates that are not the embedding of a face: empty, all zero, with a value that
 * is not a finite number, or made of damaged bytes. The matcher used to divide such an embedding by
 * a length of infinity, of 0 (raised to 1e-12) or NaN, so it became the zero vector, 1 from every
 * face and so a match at the threshold of 1.0 the README suggests, or it gave NaN.
 *
 * Templates are decoded the way the matcher decodes them, as big-endian float32 values
 * ([toFloatArray]). The tests stay on byte and float arrays rather than DataBytes, so on iOS they do
 * not depend on the conversion to NSData.
 */
class FaceEmbeddingDistanceTest {

    private fun distance(template1: ByteArray, template2: ByteArray): Double =
        embeddingDistance(toFloatArray(template1), toFloatArray(template2)).toDouble()

    private fun distance(hex1: String, hex2: String): Double = distance(hex(hex1), hex(hex2))

    private val notComparable = NOT_COMPARABLE_DISTANCE.toDouble()

    /** [3, 4]: the unit vector (0.6, 0.8). */
    private val probe = "40400000 40800000"

    /** [4, 3]: (0.8, 0.6), sqrt(0.08) = 0.283 from the probe. */
    private val near = "40800000 40400000"

    @Test
    fun anEmptyOrAllZeroTemplateMatchesNoFace() {
        val zeros = "00000000 00000000"
        assertEquals(notComparable, distance(zeros, probe)) // was 1.0
        assertEquals(notComparable, distance(probe, zeros))
        assertEquals(notComparable, distance(zeros, zeros)) // was 0.0, a match at every threshold
        assertEquals(notComparable, distance("", "")) // was 0.0
        assertTrue(notComparable > 2.0, "above every distance between two embeddings")
    }

    @Test
    fun aValueThatIsNotAFiniteNumberMakesTheTemplateMatchNoFace() {
        // NaN, infinity and minus infinity as the first value; each used to give the distance NaN
        for (first in listOf("7FC00000", "7F800000", "FF800000")) {
            assertEquals(notComparable, distance("$first 40800000", probe), first)
            assertEquals(notComparable, distance(probe, "$first 40800000"), first)
        }
    }

    @Test
    fun theDistanceDoesNotDependOnTheScaleOfTheEmbedding() {
        // [3, 4] times 1e20 and times 1e-25: in Float their squares are infinity and 0
        for (scaled in listOf("61821AB1 61AD78EC", "16B9B0E6 16F79688")) {
            assertEquals(0.0, distance(scaled, probe), 1e-6, scaled) // was 1.0
            assertEquals(sqrt(0.08), distance(scaled, near), 1e-6, scaled) // was 1.0
        }
    }

    @Test
    fun aTemplateReadInTheOtherByteOrderOrDamagedMatchesNoFace() {
        // two embeddings of unit length with 128 values, as a FaceNet model gives them
        val faces = listOf(
            unitLength(FloatArray(128) { k -> sin(k + 1.0).toFloat() }),
            unitLength(FloatArray(128) { k -> cos(3.0 * k + 0.5).toFloat() }),
        )
        // the same embeddings written little-endian and read as big-endian, and random bytes;
        // before, each of them was exactly 1.0 from both faces (a match at 1.0) or NaN from them
        val damaged = faces.map { littleEndian(it) } + (1..20).map { seed -> pseudoRandomBytes(512, seed) }
        for ((n, bytes) in damaged.withIndex()) {
            for (face in faces) {
                val d = distance(bytes, bigEndian(face))
                assertTrue(d > 1.0, "damaged template $n is $d from a face")
            }
        }
        assertEquals(0.0, distance(bigEndian(faces[0]), bigEndian(faces[0])), 1e-6)
    }

    private fun unitLength(values: FloatArray): FloatArray {
        val length = sqrt(values.sumOf { it.toDouble() * it })
        return FloatArray(values.size) { (values[it] / length).toFloat() }
    }

    /** float32 values as the encoders write them, big-endian. */
    private fun bigEndian(values: FloatArray): ByteArray =
        ByteArray(4 * values.size) { i -> (values[i / 4].toRawBits() ushr (24 - 8 * (i % 4))).toByte() }

    /** The same values little-endian, as for example numpy's `tobytes()` writes them on x86 and ARM. */
    private fun littleEndian(values: FloatArray): ByteArray =
        ByteArray(4 * values.size) { i -> (values[i / 4].toRawBits() ushr (8 * (i % 4))).toByte() }
}
