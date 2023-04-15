package net.iriscan.sdk.face.impl

import net.iriscan.sdk.core.image.*
import net.iriscan.sdk.core.io.DataBytes
import net.iriscan.sdk.core.tf.InterpreterImpl
import net.iriscan.sdk.core.utils.resizeImg
import net.iriscan.sdk.face.FaceNetModelConfiguration
import net.iriscan.sdk.utils.toByteArray
import net.iriscan.sdk.utils.toNSData
import platform.Foundation.NSData
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * @author Slava Gornostal
 */
internal actual class FaceEncoderInternal actual constructor(private val faceNetModelConfig: FaceNetModelConfiguration) {
    private val interpreter = InterpreterImpl(faceNetModelConfig.tfliteModelPath, faceNetModelConfig.modelChecksum)
    actual fun encode(image: Image): DataBytes {
        val resized = resizeImg(image, faceNetModelConfig.inputWidth, faceNetModelConfig.inputHeight)
        val pixels =
            resized.colors
                .flatMap {
                    val color = when (image.colorType) {
                        ImageColorType.RGB -> (it.red().toFloat() + it.green().toFloat() + it.blue().toFloat()) / 3
                        ImageColorType.GRAY -> it.toFloat()
                        ImageColorType.BINARY -> throw IllegalArgumentException("Unsupported color type ${image.colorType.name}")
                    }
                    listOf(color, color, color)
                }
                .toFloatArray()
        val mean = pixels.average().toFloat()
        var std = sqrt(pixels.map { pi -> (pi - mean).pow(2) }.sum() / pixels.size.toFloat())
        std = max(std, 1f / sqrt(pixels.size.toFloat()))
        for (i in pixels.indices) {
            pixels[i] = (pixels[i] - mean) / std
        }
        val bytes = pixels
            .flatMap {
                val bits = it.toRawBits()
                listOf((bits shr 24).toByte(), (bits shr 16).toByte(), (bits shr 8).toByte(), (bits).toByte())
                    .reversed()
            }
            .toByteArray()
        val inputs = mapOf(0 to bytes.toNSData())
        val outputs = mutableMapOf<Int, Any>(0 to NSData())
        interpreter.invoke(inputs, outputs)
        return (outputs[0] as NSData).toByteArray()
            .toList()
            .chunked(4)
            .flatMap { it.reversed() }
            .toByteArray()
            .toNSData()
    }

    actual fun encode(image: NativeImage): DataBytes {
        TODO("Not yet implemented")
    }
}