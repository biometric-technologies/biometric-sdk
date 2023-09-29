package net.iriscan.sdk.face.impl

import kotlinx.cinterop.*
import net.iriscan.sdk.core.image.NativeImage
import net.iriscan.sdk.face.LivenessModelConfiguration
import net.iriscan.sdk.tf.InterpreterImpl
import net.iriscan.sdk.utils.toByteArray
import net.iriscan.sdk.utils.toNSData
import platform.CoreGraphics.*
import platform.Foundation.NSData
import platform.UIKit.UIColor

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
        calculateScore(image) < modelConfig.threshold

    actual fun score(image: NativeImage): Double =
        calculateScore(image)

    private fun calculateScore(image: NativeImage): Double {
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
        CGContextSetFillColorWithColor(context, UIColor.blackColor.CGColor)
        val imageWidth = CGImageGetWidth(image.ptr)
        val imageHeight = CGImageGetHeight(image.ptr)
        val aspectRatio = imageWidth.toDouble() / imageHeight.toDouble()
        val (rescaledWidth, rescaledHeight) = when (imageWidth > imageHeight) {
            true -> newWidth.toDouble() to (newWidth / aspectRatio)
            false -> (newHeight / aspectRatio) to newHeight.toDouble()
        }
        CGContextFillRect(context, CGRectMake(0.0, 0.0, rescaledWidth, rescaledHeight))
        val rect = CGRectMake(
            (newWidth - rescaledWidth) / 2,
            (newHeight - rescaledHeight) / 2,
            rescaledWidth,
            rescaledHeight
        )
        CGContextDrawImage(context, rect, image.ptr)
        val pixels = FloatArray(pixelCount * 3)
        var i = 0
        for (j in 0 until pixelCount) {
            val index = j * 4
            pixels[i] = data[index].toFloat() / 255f
            pixels[i + 1] = data[index + 1].toFloat() / 255f
            pixels[i + 2] = data[index + 2].toFloat() / 255f
            i += 3
        }
        CGContextRelease(context)
        nativeHeap.free(data)
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
        val result = (outputs[0] as NSData).toByteArray()
            .toList()
            .chunked(4)
            .map {
                val bits = it.reversed().fold(0) { acc, byte ->
                    (acc shl 8) or (byte.toInt() and 0xFF)
                }
                Float.fromBits(bits)
            }
            .first()
        return result.toDouble()
    }
}