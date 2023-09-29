package net.iriscan.sdk.face.impl

import net.iriscan.sdk.core.image.*
import net.iriscan.sdk.core.io.DataBytes
import net.iriscan.sdk.core.utils.resizeImg
import net.iriscan.sdk.face.FaceNetModelConfiguration
import net.iriscan.sdk.tf.InterpreterImpl
import java.awt.Color
import java.awt.image.BufferedImage
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

    actual fun encode(image: Image): ByteArray {
        val resized = resizeImg(image, faceNetModelConfig.inputWidth, faceNetModelConfig.inputHeight)
        val data = normalize(resized.width, resized.height) { x, y ->
            val color = resized[x, y]
            Color(color.red(), color.green(), color.blue())
        }
        return encodeInternal(data)
    }

    actual fun encode(image: NativeImage): DataBytes {
        val resized = resizeBillinear(image, faceNetModelConfig.inputWidth, faceNetModelConfig.inputHeight)
        val data = normalize(resized.width, resized.height) { x, y -> Color(resized.getRGB(x, y)) }
        return encodeInternal(data)
    }

    private fun resizeBillinear(image: NativeImage, width: Int, height: Int): NativeImage {
        val resizedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        var graphics2D = resizedImage.createGraphics()
        graphics2D.drawImage(image, 0, 0, width, height, null)
        graphics2D.dispose()
        val tmp = image.getScaledInstance(width, height, java.awt.Image.SCALE_AREA_AVERAGING)
        graphics2D = resizedImage.createGraphics()
        graphics2D.drawImage(tmp, 0, 0, null)
        graphics2D.dispose()
        return resizedImage
    }

    private fun normalize(width: Int, height: Int, getColor: (x: Int, y: Int) -> Color): FloatArray {
        val rgb = Array(width * height) { floatArrayOf(0f, 0f, 0f) }
        for (y in 0 until height) {
            for (x in 0 until width) {
                val color = getColor(x, y)
                val i = y * width + x
                rgb[i][0] = color.red.toFloat()
                rgb[i][1] = color.green.toFloat()
                rgb[i][2] = color.blue.toFloat()
            }
        }
        val pixels = rgb.flatMap { it.toList() }.toFloatArray()
        val mean = pixels.average().toFloat()
        var std = sqrt(pixels.map { pi -> (pi - mean).pow(2) }.sum() / pixels.size.toFloat())
        std = max(std, 1f / sqrt(pixels.size.toFloat()))
        for (i in pixels.indices) {
            pixels[i] = (pixels[i] - mean) / std
        }
        return pixels
    }

    private fun encodeInternal(data: FloatArray): ByteArray {
        val faceNetModelInputs = mapOf(0 to data)
        val faceNetModelOutputs = mutableMapOf<Int, Any>(0 to FloatArray(faceNetModelConfig.outputLength))
        interpreter.invoke(faceNetModelInputs, faceNetModelOutputs)
        return (faceNetModelOutputs[0] as FloatArray).toByteArray()
    }

    private fun FloatArray.toByteArray(): ByteArray {
        val buffer = ByteBuffer.allocate(this.size * 4)
        buffer.asFloatBuffer().put(this)
        return buffer.array()
    }
}