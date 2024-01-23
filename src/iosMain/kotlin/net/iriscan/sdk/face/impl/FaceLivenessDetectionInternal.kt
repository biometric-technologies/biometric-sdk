package net.iriscan.sdk.face.impl

import io.github.aakira.napier.Napier
import kotlinx.cinterop.*
import net.iriscan.sdk.core.image.NativeImage
import net.iriscan.sdk.face.LivenessModelConfiguration
import net.iriscan.sdk.tf.InterpreterImpl
import net.iriscan.sdk.utils.toByteArray
import net.iriscan.sdk.utils.toNSData
import platform.CoreGraphics.*
import platform.Foundation.NSData

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
        calculateScore(image, traceId) > modelConfig.threshold

    actual fun score(image: NativeImage, traceId: String?): Double {
        Napier.d(tag = traceId) {
            "Validate face liveness on native image [${CGImageGetWidth(image.ptr)},${
                CGImageGetHeight(
                    image.ptr
                )
            }]"
        }
        return calculateScore(image, traceId)
    }

    private fun calculateScore(image: NativeImage, traceId: String?): Double {
        Napier.d(tag = traceId) { "Pre-processing input image" }
        val newWidth = modelConfig.inputWidth
        val newHeight = modelConfig.inputHeight
        val pixelCount = newWidth * newHeight
        val data = nativeHeap.allocArray<UByteVar>(pixelCount * 4)
        val colorSpace = CGColorSpaceCreateDeviceRGB()
        val bitmapInfo = CGImageAlphaInfo.kCGImageAlphaNoneSkipLast.value
        val context = CGBitmapContextCreate(
            data,
            newWidth.toULong(),
            newHeight.toULong(),
            8u,
            (newWidth * 4).toULong(),
            colorSpace,
            bitmapInfo
        )
        CGContextSetInterpolationQuality(context, kCGInterpolationHigh)
        CGContextDrawImage(context, CGRectMake(0.0, 0.0, newWidth.toDouble(), newHeight.toDouble()), image.ptr)
        val pixels = FloatArray(pixelCount * 3)
        val std = 0.5f
        val mean = 0.5f
        var i = 0
        for (j in 0 until pixelCount) {
            val index = j * 4
            pixels[i] = ((data[index].toFloat() / 255f) - mean) / std
            pixels[i + 1] = ((data[index + 1].toFloat() / 255f) - mean) / std
            pixels[i + 2] = ((data[index + 2].toFloat() / 255f) - mean) / std
            i += 3
        }
        val chwPixels = FloatArray(pixelCount * 3)
        val channels = 3
        for (c in 0 until channels) {
            for (h in 0 until newHeight) {
                for (w in 0 until newWidth) {
                    val hwcIndex = h * newWidth * channels + w * channels + c
                    val chwIndex = c * newHeight * newWidth + h * newWidth + w
                    chwPixels[chwIndex] = pixels[hwcIndex]
                }
            }
        }
        CGContextRelease(context)
        nativeHeap.free(data)
        val bytes = chwPixels
            .flatMap {
                val bits = it.toRawBits()
                listOf((bits shr 24).toByte(), (bits shr 16).toByte(), (bits shr 8).toByte(), (bits).toByte())
                    .reversed()
            }
            .toByteArray()
        Napier.d(tag = traceId) { "Image pre-processed, ${bytes.size} bytes resolved" }
        val inputs = mapOf(0 to bytes.toNSData())
        val outputs = mutableMapOf<Int, Any>(0 to NSData(), 1 to NSData())
        interpreter.invoke(inputs, outputs)
        val result = (outputs[1] as NSData).toByteArray()
            .toList()
            .chunked(4)
            .map {
                val bits = it.reversed().fold(0) { acc, byte ->
                    (acc shl 8) or (byte.toInt() and 0xFF)
                }
                Float.fromBits(bits)
            }
        val score = result.first().toDouble()
        Napier.d(tag = traceId) { "Resolved liveness score: $score" }
        return score
    }
}