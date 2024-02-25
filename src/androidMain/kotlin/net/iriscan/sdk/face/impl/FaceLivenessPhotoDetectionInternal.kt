package net.iriscan.sdk.face.impl

import com.soywiz.kmem.length
import io.github.aakira.napier.Napier
import net.iriscan.sdk.core.image.NativeImage
import net.iriscan.sdk.face.LivenessModelPhotoConfiguration
import net.iriscan.sdk.tf.InterpreterImpl
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.common.TensorOperator
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import org.tensorflow.lite.support.tensorbuffer.TensorBufferFloat

/**
 * @author Slava Gornostal
 */
internal actual class FaceLivenessPhotoDetectionInternal actual constructor(
    private val modelConfig: LivenessModelPhotoConfiguration
) {
    private val interpreter = InterpreterImpl(
        "liveness-photo.tflite",
        modelConfig.path,
        modelConfig.modelChecksum,
        modelConfig.modelChecksumMethod,
        modelConfig.overrideCacheOnWrongChecksum
    )
    private val imageTensorProcessor = ImageProcessor.Builder()
        .add(ResizeOp(modelConfig.inputHeight, modelConfig.inputWidth, ResizeOp.ResizeMethod.BILINEAR))
        .add(StandardizeOp(modelConfig.inputHeight, modelConfig.inputWidth))
        .build()

    actual fun validate(image: NativeImage, traceId: String?): Boolean {
        Napier.d(tag = traceId) { "Validate face liveness on native image [${image.width},${image.height}]" }
        return calculateScore(image) > modelConfig.threshold
    }

    actual fun score(image: NativeImage, traceId: String?): Double {
        Napier.d(tag = traceId) { "Calculate face liveness score on native image [${image.width},${image.height}]" }
        return calculateScore(image)
    }

    private fun calculateScore(image: NativeImage, traceId: String? = null): Double {
        val imageBytes = imageTensorProcessor.process(TensorImage.fromBitmap(image)).buffer
        Napier.d(tag = traceId) { "Image pre-processed, ${imageBytes.length} bytes resolved" }
        val modelInputs = mapOf(0 to imageBytes)
        val output = Array(1) { Array(1) { Array(14) { FloatArray(14) } } }
        val modelOutputs = mutableMapOf<Int, Any>(0 to output)
        interpreter.invoke(modelInputs, modelOutputs, traceId)
        val score = output.flatten().toTypedArray()
            .flatten().toTypedArray()
            .flatMap { it.asList() }
            .average()
        Napier.d(tag = traceId) { "Resolved liveness score: $score" }
        return score
    }

    class StandardizeOp(private val height: Int, private val width: Int) : TensorOperator {

        override fun apply(p0: TensorBuffer?): TensorBuffer {
            val std = 0.5f
            val mean = 0.5f
            val pixels = p0!!.floatArray
            for (i in pixels.indices) {
                pixels[i] = ((pixels[i] / 255f) - mean) / std
            }
            val chwPixels = FloatArray(pixels.size)
            val channels = 3
            for (c in 0 until channels) {
                for (h in 0 until height) {
                    for (w in 0 until width) {
                        val hwcIndex = h * width * channels + w * channels + c
                        val chwIndex = c * height * width + h * width + w
                        chwPixels[chwIndex] = pixels[hwcIndex]
                    }
                }
            }
            val output = TensorBufferFloat.createFixedSize(p0.shape, DataType.FLOAT32)
            output.loadArray(chwPixels)
            return output
        }

    }

}