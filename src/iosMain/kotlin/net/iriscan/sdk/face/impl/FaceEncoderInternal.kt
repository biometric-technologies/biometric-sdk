package net.iriscan.sdk.face.impl

import kotlinx.cinterop.*
import net.iriscan.sdk.core.image.*
import net.iriscan.sdk.core.io.DataBytes
import net.iriscan.sdk.core.utils.resizeImg
import net.iriscan.sdk.face.FaceNetModelConfiguration
import net.iriscan.sdk.tf.InterpreterImpl
import net.iriscan.sdk.utils.toByteArray
import net.iriscan.sdk.utils.toNSData
import platform.CoreGraphics.*
import platform.Foundation.NSData

/**
 * @author Slava Gornostal
 */
internal actual class FaceEncoderInternal actual constructor(private val faceNetModelConfig: FaceNetModelConfiguration) {
    private val interpreter = InterpreterImpl(
        "tflite.model",
        faceNetModelConfig.path,
        faceNetModelConfig.modelChecksum,
        faceNetModelConfig.modelChecksumMethod,
        faceNetModelConfig.overrideCacheOnWrongChecksum
    )

    actual fun encode(image: Image): DataBytes {
        val resized = resizeImg(image, faceNetModelConfig.inputWidth, faceNetModelConfig.inputHeight)
        val pixels =
            resized.colors
                .flatMap {
                    when (image.colorType) {
                        ImageColorType.RGB -> listOf(it.red().toFloat(), it.green().toFloat(), it.blue().toFloat())
                        ImageColorType.GRAY -> listOf(it.toFloat(), it.toFloat(), it.toFloat())
                        ImageColorType.BINARY -> throw IllegalArgumentException("Unsupported color type ${image.colorType.name}")
                    }
                }
                .toFloatArray()
        return encodeInternal(pixels)
    }

    actual fun encode(image: NativeImage): DataBytes {
        val newWidth = faceNetModelConfig.inputWidth
        val newHeight = faceNetModelConfig.inputHeight
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
        var i = 0
        for (j in 0 until pixelCount) {
            val index = j * 4
            val r = data[index].toFloat()
            val g = data[index + 1].toFloat()
            val b = data[index + 2].toFloat()
            pixels[i] = r
            pixels[i + 1] = g
            pixels[i + 2] = b
            i += 3
        }
        CGContextRelease(context)
        nativeHeap.free(data)
        return encodeInternal(pixels)
    }

    private fun encodeInternal(pixels: FloatArray): DataBytes {
        val mean = 127.5f
        val std = 128.0f
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

}