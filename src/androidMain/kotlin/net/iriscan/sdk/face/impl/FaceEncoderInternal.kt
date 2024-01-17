package net.iriscan.sdk.face.impl

import android.graphics.Bitmap
import android.graphics.Color
import com.soywiz.kmem.length
import io.github.aakira.napier.Napier
import net.iriscan.sdk.core.image.*
import net.iriscan.sdk.core.io.DataBytes
import net.iriscan.sdk.face.FaceNetModelConfiguration
import net.iriscan.sdk.tf.InterpreterImpl
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
        "facenet.tflite",
        faceNetModelConfig.path,
        faceNetModelConfig.modelChecksum,
        faceNetModelConfig.modelChecksumMethod,
        faceNetModelConfig.overrideCacheOnWrongChecksum
    )
    private val imageTensorProcessor = ImageProcessor.Builder()
        .add(ResizeOp(faceNetModelConfig.inputHeight, faceNetModelConfig.inputWidth, ResizeOp.ResizeMethod.BILINEAR))
        .add(StandardizeOp())
        .build()

    actual fun encode(image: Image, traceId: String?): ByteArray {
        Napier.d(tag = traceId) { "Encoding sdk image [${image.width},${image.height}]" }
        val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        for (x in 0 until image.width) {
            for (y in 0 until image.height) {
                val pixel = image[x, y]
                val newPixel = Color.argb(255, pixel.red(), pixel.green(), pixel.blue())
                bitmap.setPixel(x, y, newPixel)
            }
        }
        Napier.d(tag = traceId) { "Bitmap image created" }
        val imageBytes = imageTensorProcessor.process(TensorImage.fromBitmap(bitmap)).buffer
        bitmap.recycle()
        Napier.d(tag = traceId) { "Image pre-processed, ${imageBytes.length} bytes resolved" }
        return encodeInternal(imageBytes)
    }

    actual fun encode(image: NativeImage, traceId: String?): DataBytes {
        Napier.d(tag = traceId) { "Encoding native image [${image.width},${image.height}]" }
        val imageBytes = imageTensorProcessor.process(TensorImage.fromBitmap(image)).buffer
        Napier.d(tag = traceId) { "Image pre-processed, ${imageBytes.length} bytes resolved" }
        return encodeInternal(imageBytes, traceId)
    }

    private fun encodeInternal(imageBytes: ByteBuffer, traceId: String? = null): ByteArray {
        Napier.d(tag = traceId) { "Encoding image bytes with FaceNet interpreter" }
        val faceNetModelInputs = mapOf(0 to imageBytes)
        val faceNetModelOutputs = mutableMapOf<Int, Any>(0 to Array(1) { FloatArray(faceNetModelConfig.outputLength) })
        interpreter.invoke(faceNetModelInputs, faceNetModelOutputs, traceId)
        val template = (faceNetModelOutputs[0] as Array<FloatArray>)[0]
        Napier.d(tag = traceId) { "Encoding done, result template size: ${template.size}" }
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