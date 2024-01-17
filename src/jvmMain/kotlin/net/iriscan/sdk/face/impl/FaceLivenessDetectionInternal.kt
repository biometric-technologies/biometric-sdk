package net.iriscan.sdk.face.impl

import net.iriscan.sdk.core.image.NativeImage
import net.iriscan.sdk.face.LivenessModelConfiguration
import net.iriscan.sdk.tf.InterpreterImpl
import java.awt.Color
import java.awt.image.BufferedImage

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

    actual fun validate(image: NativeImage, traceId: String?): Boolean =
        calculateScore(image) > modelConfig.threshold

    actual fun score(image: NativeImage, traceId: String?): Double =
        calculateScore(image)

    private fun calculateScore(image: NativeImage): Double {
        val resized = resizeKeepAspectRatio(image, modelConfig.inputWidth, modelConfig.inputHeight)
        val data = normalize(resized.width, resized.height) { x, y -> Color(resized.getRGB(x, y)) }
        val modelInputs = mapOf(0 to data)
        val modelOutputs = mutableMapOf<Int, Any>(0 to FloatArray(192))
        interpreter.invoke(modelInputs, modelOutputs)
        return (modelOutputs[0] as FloatArray).average()
    }

    private fun resizeKeepAspectRatio(image: NativeImage, width: Int, height: Int): NativeImage {
        val imageWidth = image.width
        val imageHeight = image.height
        val aspectRatio = imageWidth.toDouble() / imageHeight.toDouble()
        val (newWidth, newHeight) = when {
            imageWidth > imageHeight -> width to (width / aspectRatio).toInt()
            else -> (height * aspectRatio).toInt() to height
        }
        val outputImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = outputImage.createGraphics()
        g.color = Color.BLACK
        g.fillRect(0, 0, width, height)
        val xPos = (width - newWidth) / 2
        val yPos = (height - newHeight) / 2
        g.drawImage(
            image.getScaledInstance(newWidth, newHeight, BufferedImage.SCALE_SMOOTH),
            xPos, yPos, null
        )
        g.dispose()
        return outputImage
    }

    private fun normalize(width: Int, height: Int, getColor: (x: Int, y: Int) -> Color): FloatArray {
        val rgb = Array(width * height) { floatArrayOf(0f, 0f, 0f) }
        val mean = 0.5f
        val std = 0.5f
        for (y in 0 until height) {
            for (x in 0 until width) {
                val color = getColor(x, y)
                val i = y * width + x
                rgb[i][0] = ((color.red.toFloat() / 255f) - mean) / std
                rgb[i][1] = ((color.green.toFloat() / 255f) - mean) / std
                rgb[i][2] = ((color.blue.toFloat() / 255f) - mean) / std
            }
        }
        return rgb.flatMap { it.toList() }.toFloatArray()
    }

}