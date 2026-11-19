package net.iriscan.sdk.face.impl

import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The face encoder and the photo liveness check standardize the resized face crop with their own
 * Support Library operators before the model sees it. The Support Library now comes from LiteRT
 * (`litert-support-api`) instead of TensorFlow Lite Support 0.4.3, so these tests pin what the two
 * operators hand to the models. Tensor buffers are plain Java, so the tests run on the host JVM
 * without the native runtime; the resize itself needs an Android bitmap and is checked on a device.
 */
class FaceStandardizeOpTest {

    @Test
    fun faceNetInputHasZeroMeanAndUnitDeviation() {
        val values = intArrayOf(0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 255)
        val output = FaceEncoderInternal.StandardizeOp().apply(rgbCrop(values, height = 2, width = 2))
        assertEquals(DataType.FLOAT32, output.dataType)
        assertContentEquals(intArrayOf(2, 2, 3), output.shape)
        // The operator uses the population deviation over all channels of the crop
        val mean = values.average()
        val deviation = sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
        val expected = FloatArray(values.size) { ((values[it] - mean) / deviation).toFloat() }
        assertAllClose(expected, output.floatArray)
    }

    @Test
    fun faceNetInputOfAFlatCropIsZeroInsteadOfNotANumber() {
        // A crop of one colour has no deviation; the operator divides by 1 / sqrt(n) instead of 0
        val output = FaceEncoderInternal.StandardizeOp().apply(rgbCrop(IntArray(12) { 128 }, height = 2, width = 2))
        assertTrue(output.floatArray.all { it == 0f }, "values: ${output.floatArray.toList()}")
    }

    @Test
    fun livenessInputIsScaledToMinusOneToOneInChannelFirstOrder() {
        val height = 2
        val width = 3
        val values = IntArray(height * width * CHANNELS) { it * 14 }
        val output = FaceLivenessPhotoDetectionInternal.StandardizeOp(height, width)
            .apply(rgbCrop(values, height, width))
        assertEquals(DataType.FLOAT32, output.dataType)
        // The liveness model takes channels first: all red values, then all green, then all blue
        val expected = FloatArray(values.size)
        for (c in 0 until CHANNELS) for (y in 0 until height) for (x in 0 until width) {
            val pixel = values[(y * width + x) * CHANNELS + c]
            expected[c * height * width + y * width + x] = (pixel / 255f - 0.5f) / 0.5f
        }
        assertAllClose(expected, output.floatArray)
    }

    /** An RGB crop as the resize step hands it to the operators: unsigned bytes, height x width x 3. */
    private fun rgbCrop(values: IntArray, height: Int, width: Int): TensorBuffer {
        val buffer = TensorBuffer.createFixedSize(intArrayOf(height, width, CHANNELS), DataType.UINT8)
        buffer.loadArray(values)
        return buffer
    }

    private fun assertAllClose(expected: FloatArray, actual: FloatArray) {
        assertEquals(expected.size, actual.size, "number of values")
        for (i in expected.indices) {
            assertEquals(expected[i], actual[i], FLOAT_TOLERANCE, "value $i of ${actual.toList()}")
        }
    }

    private companion object {
        const val CHANNELS = 3
        const val FLOAT_TOLERANCE = 1e-5f
    }
}
