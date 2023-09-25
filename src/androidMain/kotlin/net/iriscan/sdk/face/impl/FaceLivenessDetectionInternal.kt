package net.iriscan.sdk.face.impl

import net.iriscan.sdk.core.image.NativeImage
import net.iriscan.sdk.face.LivenessModelConfiguration
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
internal actual class FaceLivenessDetectionInternal actual constructor(
    private val modelConfig: LivenessModelConfiguration
) {
    private val interpreter = InterpreterImpl(
        "liveness.tflite",
        modelConfig.path,
        modelConfig.modelChecksum,
        modelConfig.modelChecksumMethod,
        modelConfig.overrideCacheOnWrongChecksum
    )
    private val imageTensorProcessor = ImageProcessor.Builder()
        .add(ResizeOp(modelConfig.inputHeight, modelConfig.inputWidth, ResizeOp.ResizeMethod.BILINEAR))
        .add(StandardizeOp())
        .build()

    actual fun validate(image: NativeImage): Boolean =
        calculateScore(image) < modelConfig.threshold

    actual fun score(image: NativeImage): Double =
        calculateScore(image)

    private fun calculateScore(image: NativeImage): Double {
        val imageBytes = imageTensorProcessor.process(TensorImage.fromBitmap(image)).buffer
        val faceNetModelInputs = mapOf(0 to imageBytes)
        val faceNetModelOutputs = mutableMapOf<Int, Any>(0 to Array(1) { FloatArray(1) })
        interpreter.invoke(faceNetModelInputs, faceNetModelOutputs)
        return (faceNetModelOutputs[0] as Array<FloatArray>)[0][0].toDouble()
    }

    class StandardizeOp : TensorOperator {

        override fun apply(p0: TensorBuffer?): TensorBuffer {
            val pixels = p0!!.floatArray
            for (i in pixels.indices) {
                pixels[i] = pixels[i] / 255f
            }
            val output = TensorBufferFloat.createFixedSize(p0.shape, DataType.FLOAT32)
            output.loadArray(pixels)
            return output
        }

    }

}