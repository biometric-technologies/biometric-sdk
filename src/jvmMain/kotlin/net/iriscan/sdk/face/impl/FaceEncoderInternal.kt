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
            val gray = (0.299 * color.red() + 0.587 * color.green() + 0.114 * color.blue()).toInt()
            Color(gray, gray, gray)
        }
        return encodeInternal(data)
    }

    actual fun encode(image: NativeImage): DataBytes {
        val resized = internalResizeNativeImage(image, faceNetModelConfig.inputWidth, faceNetModelConfig.inputHeight)
        val grayscale = BufferedImage(resized.width, resized.height, resized.type)
        for (x in 0 until resized.width) {
            for (y in 0 until resized.height) {
                val pixel = Color(resized.getRGB(x, y))
                val red = pixel.red
                val green = pixel.green
                val blue = pixel.blue
                val gray = (0.299 * red + 0.587 * green + 0.114 * blue).toInt()
                val newPixel = Color(gray, gray, gray).rgb
                grayscale.setRGB(x, y, newPixel)
            }
        }
        val data = normalize(grayscale.width, grayscale.height) { x, y -> Color(grayscale.getRGB(x, y)) }
        return encodeInternal(data)
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