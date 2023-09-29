package net.iriscan.sdk.face.impl

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import net.iriscan.sdk.core.image.NativeImage
import net.iriscan.sdk.face.LivenessModelConfiguration
import net.iriscan.sdk.tf.InterpreterImpl
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.common.TensorOperator
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
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
        .add(StandardizeOp())
        .build()

    actual fun validate(image: NativeImage): Boolean =
        calculateScore(image) < modelConfig.threshold

    actual fun score(image: NativeImage): Double =
        calculateScore(image)

    private fun calculateScore(image: NativeImage): Double {
        val resized = resizeKeepAspectRatio(image, modelConfig.inputWidth, modelConfig.inputHeight)
        val imageBytes = imageTensorProcessor.process(TensorImage.fromBitmap(resized)).buffer
        val faceNetModelInputs = mapOf(0 to imageBytes)
        val faceNetModelOutputs = mutableMapOf<Int, Any>(0 to Array(1) { FloatArray(1) })
        interpreter.invoke(faceNetModelInputs, faceNetModelOutputs)
        return (faceNetModelOutputs[0] as Array<FloatArray>)[0][0].toDouble()
    }

    private fun resizeKeepAspectRatio(image: NativeImage, width: Int, height: Int): NativeImage {
        val aspectRatio = image.width.toFloat() / image.height.toFloat()
        val (newWidth, newHeight) = when (image.width > image.height) {
            true -> width to (width / aspectRatio).toInt()
            false -> (height / aspectRatio).toInt() to height
        }
        val resizedBitmap = Bitmap.createScaledBitmap(image, newWidth, newHeight, false)
        val outputBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply { color = Color.BLACK })
        val left = (width - newWidth) / 2f
        val top = (height - newHeight) / 2f
        canvas.drawBitmap(resizedBitmap, left, top, null)
        return outputBitmap
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