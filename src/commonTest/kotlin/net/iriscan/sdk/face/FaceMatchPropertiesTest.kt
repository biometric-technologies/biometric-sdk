package net.iriscan.sdk.face

import net.iriscan.sdk.face.impl.embeddingDistance
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FaceMatchPropertiesTest {

    @Test
    fun acceptsEveryDistanceTheMatcherCanReturn() {
        for (threshold in listOf(0.0, 0.5, 1.0, sqrt(2.0), 2.0)) {
            assertEquals(threshold, FaceMatchProperties(threshold).threshold)
        }
    }

    @Test
    fun rejectsThresholdsThatAcceptEveryPairOrNone() {
        // 10.0 stands for a threshold chosen on raw distances, which SDK versions before 0.7 compared
        val thresholds = listOf(-0.01, 2.01, 10.0, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN)
        for (threshold in thresholds) {
            val e = assertFailsWith<IllegalArgumentException>("threshold $threshold") {
                FaceMatchProperties(threshold)
            }
            val message = e.message ?: ""
            assertTrue(message.contains("from 0.0 to 2.0") && message.endsWith("was $threshold"), message)
        }
    }

    @Test
    fun oppositeEmbeddingsAreAsFarApartAsTheLargestThreshold() {
        // the matcher normalizes the embeddings, so the scale of the model output does not matter
        val embedding = FloatArray(128) { k -> 7 * sin(k + 1.0).toFloat() }
        val opposite = FloatArray(128) { k -> -embedding[k] / 3 }
        assertEquals(MAX_FACE_DISTANCE, embeddingDistance(embedding, opposite).toDouble(), 1e-6)
        assertEquals(0.0, embeddingDistance(embedding, opposite.map { -it }.toFloatArray()).toDouble(), 1e-6)
    }
}
