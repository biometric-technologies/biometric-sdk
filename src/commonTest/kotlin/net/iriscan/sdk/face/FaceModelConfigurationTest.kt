package net.iriscan.sdk.face

import net.iriscan.sdk.core.io.HashMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FaceModelConfigurationTest {

    private val badSizes = listOf(0, -1, -160, Int.MIN_VALUE)

    private fun assertRejected(setting: String, value: Int, create: () -> Any) {
        val e = assertFailsWith<IllegalArgumentException>("$setting = $value") { create() }
        val message = e.message ?: ""
        assertTrue(message.contains("$setting must be positive") && message.endsWith("was $value"), message)
    }

    @Test
    fun acceptsTheSizesOfTheReadmeExamples() {
        val faceNet = FaceNetModelConfiguration("assets:facenet.tflite", 160, 160, 128)
        assertEquals(listOf(160, 160, 128), listOf(faceNet.inputWidth, faceNet.inputHeight, faceNet.outputLength))
        val photo = LivenessModelPhotoConfiguration("assets:liveness.tflite", 224, 224, 0.5)
        assertEquals(listOf(224, 224), listOf(photo.inputWidth, photo.inputHeight))
        // a model with a non-square input and a single output value is valid too
        val smallest = FaceNetModelConfiguration("assets:facenet.tflite", 1, 2, 1, "00", HashMethod.SHA256, true)
        assertEquals(listOf(1, 2, 1), listOf(smallest.inputWidth, smallest.inputHeight, smallest.outputLength))
    }

    @Test
    fun faceNetRejectsSizesOfZeroOrLess() {
        for (bad in badSizes) {
            assertRejected("FaceNet inputWidth", bad) { FaceNetModelConfiguration("m", bad, 160, 128) }
            assertRejected("FaceNet inputHeight", bad) { FaceNetModelConfiguration("m", 160, bad, 128) }
            assertRejected("FaceNet outputLength", bad) { FaceNetModelConfiguration("m", 160, 160, bad) }
            // the constructor with a checksum checks the same, it is the primary one
            assertRejected("FaceNet outputLength", bad) {
                FaceNetModelConfiguration("m", 160, 160, bad, "00", HashMethod.SHA256, true)
            }
        }
    }

    @Test
    fun photoLivenessRejectsSizesOfZeroOrLess() {
        for (bad in badSizes) {
            assertRejected("Photo liveness inputWidth", bad) { LivenessModelPhotoConfiguration("m", bad, 224, 0.5) }
            assertRejected("Photo liveness inputHeight", bad) { LivenessModelPhotoConfiguration("m", 224, bad, 0.5) }
            assertRejected("Photo liveness inputHeight", bad) {
                LivenessModelPhotoConfiguration("m", 224, bad, 0.5, "00", HashMethod.SHA256, true)
            }
        }
    }
}
