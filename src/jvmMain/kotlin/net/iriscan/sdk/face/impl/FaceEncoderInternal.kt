package net.iriscan.sdk.face.impl

import com.soywiz.korio.util.toByteArray
import net.iriscan.sdk.core.image.*
import net.iriscan.sdk.core.io.DataBytes
import net.iriscan.sdk.core.tf.InterpreterImpl
import net.iriscan.sdk.core.utils.resizeImg
import net.iriscan.sdk.face.FaceNetModelConfiguration
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.ByteBuffer

/**
 * @author Slava Gornostal
 */
internal actual class FaceEncoderInternal actual constructor(
    private val faceNetModelConfig: FaceNetModelConfiguration
) {

    private val interpreter = InterpreterImpl(faceNetModelConfig.tfliteModelPath, faceNetModelConfig.modelChecksum)
    actual fun encode(image: Image): ByteArray {
        val resized = resizeImg(image, faceNetModelConfig.inputWidth, faceNetModelConfig.inputHeight)
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
        return encodeInternal(data)
    }

    actual fun encode(image: NativeImage): DataBytes {
        val resized = BufferedImage(
            faceNetModelConfig.inputWidth,
            faceNetModelConfig.inputHeight, BufferedImage.TYPE_INT_RGB
        )
        val g2d = resized.createGraphics()
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g2d.drawImage(image, 0, 0, faceNetModelConfig.inputWidth, faceNetModelConfig.inputHeight, null)
        g2d.dispose()
        val rgb = Array(resized.width * resized.height) { floatArrayOf(0f, 0f, 0f) }
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val color = Color(image.getRGB(x, y))
                val i = y * resized.width + x
                rgb[i][0] = (color.red.toFloat() - 127.5f) / 128f
                rgb[i][1] = (color.green.toFloat() - 127.5f) / 128f
                rgb[i][2] = (color.blue.toFloat() - 127.5f) / 128f
            }
        }
        val data = rgb.flatMap { it.toList() }.toFloatArray()
        return encodeInternal(data)
    }

    private fun encodeInternal(data: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(data.size * 4)
        buffer.asFloatBuffer().put(data)
        val faceNetModelInputs = mapOf(0 to buffer.toByteArray())
        val faceNetModelOutputs = mutableMapOf<Int, Any>(0 to ByteArray(faceNetModelConfig.outputLength))
        interpreter.invoke(faceNetModelInputs, faceNetModelOutputs)
        return faceNetModelOutputs[0] as ByteArray
    }
}