package net.iriscan.sdk.face.impl

import net.iriscan.sdk.core.image.NativeImage
import net.iriscan.sdk.core.image.internalResizeNativeImage
import net.iriscan.sdk.face.LivenessModelConfiguration
import net.iriscan.sdk.tf.InterpreterImpl
import java.awt.Color

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

    actual fun validate(image: NativeImage): Boolean =
        calculateScore(image) >= modelConfig.threshold

    actual fun score(image: NativeImage): Double =
        calculateScore(image)

    private fun calculateScore(image: NativeImage): Double {
        val resized = internalResizeNativeImage(image, modelConfig.inputWidth, modelConfig.inputHeight)
        val data = normalize(resized.width, resized.height) { x, y -> Color(resized.getRGB(x, y)) }
        val modelInputs = mapOf(0 to data)
        val modelOutputs = mutableMapOf<Int, Any>(0 to 0f)
        interpreter.invoke(modelInputs, modelOutputs)
        return (modelOutputs[0] as Float).toDouble()
    }

    private fun normalize(width: Int, height: Int, getColor: (x: Int, y: Int) -> Color): FloatArray {
        val rgb = Array(width * height) { floatArrayOf(0f, 0f, 0f) }
        for (y in 0 until height) {
            for (x in 0 until width) {
                val color = getColor(x, y)
                val i = y * width + x
                rgb[i][0] = color.red.toFloat() / 255f
                rgb[i][1] = color.green.toFloat() / 255f
                rgb[i][2] = color.blue.toFloat() / 255f
            }
        }
        return rgb.flatMap { it.toList() }.toFloatArray()
    }

}