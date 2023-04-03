package net.iriscan.sdk.face.impl

import com.soywiz.korio.util.toByteArray
import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.image.blue
import net.iriscan.sdk.core.image.green
import net.iriscan.sdk.core.image.red
import net.iriscan.sdk.core.tf.InterpreterImpl
import net.iriscan.sdk.core.utils.resizeImg
import net.iriscan.sdk.face.FaceNetModelConfiguration
import java.nio.ByteBuffer

/**
 * @author Slava Gornostal
 */
internal actual class FaceEncoderInternal actual constructor(
    private val faceNetModelConfig: FaceNetModelConfiguration
) {

    private val interpreter = InterpreterImpl(faceNetModelConfig.tfliteModelPath, faceNetModelConfig.modelChecksum)
    actual fun encode(image: Image): ByteArray {
        val resized = resizeImg(image, 160, 160)
        val rgb = Array(resized.width * resized.height) { floatArrayOf(0f, 0f, 0f) }
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val color = resized[x, y]
                val i = y * resized.width + x
                rgb[i][0] = (color.red().toFloat() - 127.5f) / 128f
                rgb[i][1] = (color.green().toFloat() - 127.5f) / 128f
                rgb[i][2] = (color.blue().toFloat() - 127.5f) / 128f
            }
        }
        val data = rgb.flatMap { it.toList() }.toFloatArray()
        val buffer = ByteBuffer.allocate(data.size * 4)
        buffer.asFloatBuffer().put(data)
        val faceNetModelInputs = mapOf(0 to buffer.toByteArray())
        val faceNetModelOutputs = mutableMapOf<Int, Any>(0 to ByteArray(faceNetModelConfig.outputLength))
        interpreter.invoke(faceNetModelInputs, faceNetModelOutputs)
        return faceNetModelOutputs[0] as ByteArray
    }
}