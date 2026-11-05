package net.iriscan.sdk.tf

import net.iriscan.sdk.face.FaceNetModelConfiguration
import net.iriscan.sdk.face.LivenessModelPhotoConfiguration
import net.iriscan.sdk.face.impl.FaceEncoderInternal
import net.iriscan.sdk.face.impl.FaceLivenessPhotoDetectionInternal
import net.iriscan.sdk.io.ResourceIOFactory
import net.iriscan.sdk.testutil.hex
import org.bytedeco.javacpp.Loader
import org.bytedeco.tensorflowlite.global.tensorflowlite
import java.awt.image.BufferedImage
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * On the JVM the interpreter copies the SDK's arrays into and out of the model's tensors with
 * JavaCPP, which does not know how large a tensor is. An input size or output length in the model
 * configuration that does not match the model must therefore be rejected before the copy, as the
 * TensorFlow Lite APIs of Android and iOS do. These tests run two real models, [MEAN_MODEL] and
 * [CAST_MODEL], with the TensorFlow Lite runtime the SDK uses on the JVM.
 */
class ModelTensorSizeTest {

    @Test
    fun `sizes that match the model run it`() = withModel { path ->
        val interpreter = InterpreterImpl("mean.tflite", path, null, null, null)
        assertContentEquals(floatArrayOf(4.5f, 5.5f, 6.5f), meanOf(interpreter, FloatArray(12) { it.toFloat() }))
    }

    @Test
    fun `an input of another size is rejected before it is copied`() = withModel { path ->
        val interpreter = InterpreterImpl("mean.tflite", path, null, null, null)
        // a 4 x 4 image (more values than the tensor holds) and 2 pixels (fewer)
        for (size in intArrayOf(48, 6)) {
            val error = assertFailsWith<IllegalArgumentException> { meanOf(interpreter, FloatArray(size)) }
            assertEquals(
                "Model input 0 holds 12 float32 values (shape [1, 2, 2, 3]), but the SDK passes $size: " +
                        "the sizes in the model configuration do not match the model",
                error.message,
            )
        }
        // nothing was copied, so the model still gives the means of an input that fits
        assertContentEquals(floatArrayOf(4.5f, 5.5f, 6.5f), meanOf(interpreter, FloatArray(12) { it.toFloat() }))
    }

    @Test
    fun `an output of another length is rejected before anything is read`() = withModel { path ->
        val interpreter = InterpreterImpl("mean.tflite", path, null, null, null)
        // longer than the output (values from past the tensor) and shorter (an embedding cut short)
        for (length in intArrayOf(128, 2)) {
            val output = FloatArray(length)
            val error = assertFailsWith<IllegalArgumentException> {
                interpreter.invoke(mapOf(0 to FloatArray(12) { it.toFloat() }), mutableMapOf(0 to output))
            }
            assertEquals(
                "Model output 0 holds 3 float32 values (shape [1, 3]), but the SDK reads $length: " +
                        "the sizes in the model configuration do not match the model",
                error.message,
            )
            assertTrue(output.all { it == 0f }, "nothing is read into an output that does not fit")
        }
    }

    @Test
    fun `a model that takes other values than float32 is rejected by name`() = withModel(CAST_MODEL) { path ->
        val interpreter = InterpreterImpl("cast.tflite", path, null, null, null)
        val error = assertFailsWith<IllegalArgumentException> {
            interpreter.invoke(mapOf(0 to FloatArray(12)), mutableMapOf(0 to FloatArray(12)))
        }
        assertEquals("Model input 0 is UINT8, but the SDK exchanges only float32 values with a model", error.message)
    }

    @Test
    fun `face encoding and photo liveness reject a configuration that does not fit the model`() = withModel { path ->
        val face = uniformImage(8, 8, red = 200, green = 100, blue = 50)

        // 2 x 2 RGB input and 3 output values fit: the template is the 3 values, big-endian float32,
        // here the mean of each channel after the encoder's per-image standardization
        val template = FaceEncoderInternal(FaceNetModelConfiguration(path, 2, 2, 3)).encode(face, null)
        val mean = (200 + 100 + 50) / 3.0
        val std = sqrt(((200 - mean) * (200 - mean) + (100 - mean) * (100 - mean) + (50 - mean) * (50 - mean)) / 3)
        val expected = doubleArrayOf((200 - mean) / std, (100 - mean) / std, (50 - mean) / std)
        val values = ByteBuffer.wrap(template).asFloatBuffer()
        assertEquals(3, values.remaining())
        expected.forEachIndexed { i, value -> assertEquals(value, values[i].toDouble(), 1e-5) }

        val wrongInput = assertFailsWith<IllegalArgumentException> {
            FaceEncoderInternal(FaceNetModelConfiguration(path, 4, 4, 3)).encode(face, null)
        }
        assertTrue("shape [1, 2, 2, 3]" in wrongInput.message.orEmpty(), wrongInput.message)
        val wrongOutput = assertFailsWith<IllegalArgumentException> {
            FaceEncoderInternal(FaceNetModelConfiguration(path, 2, 2, 128)).encode(face, null)
        }
        assertTrue("shape [1, 3]" in wrongOutput.message.orEmpty(), wrongOutput.message)
        // photo liveness reads a 14 x 14 map of live probabilities, which this model does not give
        val liveness = assertFailsWith<IllegalArgumentException> {
            FaceLivenessPhotoDetectionInternal(LivenessModelPhotoConfiguration(path, 2, 2, 0.5)).score(face, null)
        }
        assertTrue("but the SDK reads 196" in liveness.message.orEmpty(), liveness.message)
    }

    private fun meanOf(interpreter: InterpreterImpl, input: FloatArray): FloatArray {
        val output = FloatArray(3)
        interpreter.invoke(mapOf(0 to input), mutableMapOf(0 to output))
        return output
    }

    private fun uniformImage(width: Int, height: Int, red: Int, green: Int, blue: Int): BufferedImage =
        BufferedImage(width, height, BufferedImage.TYPE_INT_RGB).apply {
            for (y in 0 until height) for (x in 0 until width) setRGB(x, y, (red shl 16) or (green shl 8) or blue)
        }

    /**
     * Runs [test] with the `file:` path of [model]. bytedeco's tensorflow-lite 2.10.0-1.5.8 has
     * natives only for [PLATFORMS_WITH_NATIVES]; elsewhere (the macOS arm64 runner of the CI build
     * job) the test returns without running, as IrisDatasetBenchmark does without a dataset.
     */
    private fun withModel(model: ByteArray = MEAN_MODEL, test: (path: String) -> Unit) {
        if (Loader.getPlatform() !in PLATFORMS_WITH_NATIVES) {
            println("ModelTensorSizeTest skipped: no TensorFlow Lite natives for ${Loader.getPlatform()}")
            return
        }
        // In an app, the face extractor's OpenCV classes load JavaCPP's natives before the first
        // model; this test creates no extractor, so it loads them, with TensorFlow Lite's, itself.
        Loader.load(tensorflowlite::class.java)
        ResourceIOFactory.initialize(null)
        val file = File.createTempFile("model", ".tflite")
        try {
            file.writeBytes(model)
            test("file:${file.absolutePath}")
        } finally {
            file.delete()
        }
    }

    private companion object {
        val PLATFORMS_WITH_NATIVES = setOf("linux-x86_64", "linux-arm64", "macosx-x86_64", "windows-x86_64")

        /**
         * The smallest model with an input and an output of different sizes (436 bytes): one MEAN
         * operator; input "image", float32 [1, 2, 2, 3], a 2 x 2 RGB image in the layout of FaceNet
         * models; output "mean", float32 [1, 3], the mean of each channel over the 4 pixels. Written
         * with the TensorFlow Lite flatbuffer schema (Python packages tflite and flatbuffers) and
         * checked with LiteRT 2.3: the input 0, 1, ..., 11 gives 4.5, 5.5, 6.5.
         */
        val MEAN_MODEL = hex(
            """
            0800000054464c339affffff3000000010000000340000003800000003000000
            140000006d65616e206f662065616368206368616e6e656c0000000002000000
            6401000054010000010000003000000001000000100000000c000a0009000000
            000004000c0000002800000000280e001800140010000c00080004000e000000
            140000001c000000200000002400000028000000040000006d61696e00000000
            01000000340000000100000002000000010000000000000003000000ac000000
            740000004000000000000e001400000010000c000b0004000e00000024000000
            0000001b080000000c0000000100000002000000020000000000000001000000
            60ffffffa8ffffff140000000400000002000000010000000300000004000000
            6d65616e000000000c00140010000f00080004000c0000001800000001000000
            000000020400000001000000020000000400000061786573000000000c000c00
            08000000000004000c0000001c00000004000000040000000100000002000000
            020000000300000005000000696d61676500060008000400060000000c000000
            0400040004000000080000000100000002000000
            """
        )

        /**
         * A model that takes bytes, as quantized models do (344 bytes): one CAST operator; input
         * "image", uint8 [1, 2, 2, 3]; output "image_float", float32 [1, 2, 2, 3]. Written and
         * checked like [MEAN_MODEL]: the input 0, 1, ..., 11 gives 0.0, 1.0, ..., 11.0.
         */
        val CAST_MODEL = hex(
            """
            1800000054464c3300000e00140010000c000800000004000e00000010000000
            1400000018000000030000000100000024010000010000003000000001000000
            100000000c000a0009000000000004000c0000003500000000350e0018001400
            10000c00080004000e000000140000001c000000200000002400000028000000
            040000006d61696e00000000010000002c000000010000000100000001000000
            0000000002000000780000003800000000000a000c000000080004000a000000
            080000000c000000010000000100000001000000000000000c000c0008000000
            000004000c0000001c0000000400000004000000010000000200000002000000
            030000000b000000696d6167655f666c6f6174000c0010000c000b0000000400
            0c00000020000000000000030400000004000000010000000200000002000000
            0300000005000000696d6167650000000400040004000000
            """
        )
    }
}
