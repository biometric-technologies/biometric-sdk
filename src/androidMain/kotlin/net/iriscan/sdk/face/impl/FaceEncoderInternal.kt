package net.iriscan.sdk.face.impl

import android.graphics.Bitmap
import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.image.NativeImage
import net.iriscan.sdk.core.io.DataBytes
import net.iriscan.sdk.core.tf.InterpreterImpl
import net.iriscan.sdk.face.FaceNetModelConfiguration
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.common.TensorOperator
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import org.tensorflow.lite.support.tensorbuffer.TensorBufferFloat
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * @author Slava Gornostal
 */
internal actual class FaceEncoderInternal actual constructor(
    private val faceNetModelConfig: FaceNetModelConfiguration
) {
    private val interpreter = InterpreterImpl(
        "tflite.model",
        faceNetModelConfig.path,
        faceNetModelConfig.modelChecksum,
        faceNetModelConfig.modelChecksumMethod,
        faceNetModelConfig.overrideCacheOnWrongChecksum
    )
    private val imageTensorProcessor = ImageProcessor.Builder()
        .add(ResizeOp(faceNetModelConfig.inputHeight, faceNetModelConfig.inputWidth, ResizeOp.ResizeMethod.BILINEAR))
        .add(StandardizeOp())
        .build()

    actual fun encode(image: Image): ByteArray {
        var bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.RGB_565)
        bitmap.setPixels(image.colors, 0, image.width, 0, 0, image.width, image.height)
        bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val imageBytes = imageTensorProcessor.process(TensorImage.fromBitmap(bitmap)).buffer
        bitmap.recycle()
        return encodeInternal(imageBytes)
    }

    actual fun encode(image: NativeImage): DataBytes {
        val imageBytes = imageTensorProcessor.process(TensorImage.fromBitmap(image)).buffer
        return encodeInternal(imageBytes)
    }

    private fun encodeInternal(imageBytes: ByteBuffer): ByteArray {
        val faceNetModelInputs = mapOf(0 to imageBytes)
        val faceNetModelOutputs = mutableMapOf<Int, Any>(0 to Array(1) { FloatArray(faceNetModelConfig.outputLength) })
        interpreter.invoke(faceNetModelInputs, faceNetModelOutputs)
        val template = (faceNetModelOutputs[0] as Array<FloatArray>)[0]
        val bb = ByteBuffer.allocate(template.size * 4)
        bb.asFloatBuffer().put(template)
        return bb.array()
    }

    class StandardizeOp : TensorOperator {

        override fun apply(p0: TensorBuffer?): TensorBuffer {
            val pixels = p0!!.floatArray
            val mean = pixels.average().toFloat()
            var std = sqrt(pixels.map { pi -> (pi - mean).pow(2) }.sum() / pixels.size.toFloat())
            std = max(std, 1f / sqrt(pixels.size.toFloat()))
            for (i in pixels.indices) {
                pixels[i] = (pixels[i] - mean) / std
            }
            val output = TensorBufferFloat.createFixedSize(p0.shape, DataType.FLOAT32)
            output.loadArray(pixels)
            return output
        }

    }
}