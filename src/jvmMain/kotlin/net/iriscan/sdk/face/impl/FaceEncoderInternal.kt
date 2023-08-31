package net.iriscan.sdk.face.impl

import net.iriscan.sdk.core.image.*
import net.iriscan.sdk.core.io.DataBytes
import net.iriscan.sdk.core.utils.resizeImg
import net.iriscan.sdk.face.FaceNetModelConfiguration
import net.iriscan.sdk.tf.InterpreterImpl
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.ByteBuffer

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
        val resized = resizeNative(image, faceNetModelConfig.inputWidth, faceNetModelConfig.inputHeight)
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
                rgb[i][0] = (color.red.toFloat() - 127.5f) / 128f
                rgb[i][1] = (color.green.toFloat() - 127.5f) / 128f
                rgb[i][2] = (color.blue.toFloat() - 127.5f) / 128f
            }
        }
        return rgb.flatMap { it.toList() }.toFloatArray()
    }

    private fun resizeNative(originalImage: NativeImage, targetWidth: Int, targetHeight: Int): NativeImage {
        val resizedImage = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB)
        var graphics2D = resizedImage.createGraphics()
        graphics2D.drawImage(originalImage, 0, 0, targetWidth, targetHeight, null)
        graphics2D.dispose()
        val tmp = originalImage.getScaledInstance(targetWidth, targetHeight, java.awt.Image.SCALE_AREA_AVERAGING)
        graphics2D = resizedImage.createGraphics()
        graphics2D.drawImage(tmp, 0, 0, null)
        graphics2D.dispose()
        return resizedImage
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