package net.iriscan.sdk.face.impl

import io.github.aakira.napier.Napier
import kotlinx.cinterop.*
import net.iriscan.sdk.core.image.*
import net.iriscan.sdk.core.io.DataBytes
import net.iriscan.sdk.core.utils.generateTraceID
import net.iriscan.sdk.core.utils.resizeImg
import net.iriscan.sdk.face.FaceNetModelConfiguration
import net.iriscan.sdk.tf.InterpreterImpl
import net.iriscan.sdk.utils.toByteArray
import net.iriscan.sdk.utils.toNSData
import platform.CoreGraphics.*
import platform.Foundation.NSData
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * @author Slava Gornostal
 */
internal actual class FaceEncoderInternal actual constructor(private val faceNetModelConfig: FaceNetModelConfiguration) {
    private val interpreter = InterpreterImpl(
        "facenet.tflite",
        faceNetModelConfig.path,
        faceNetModelConfig.modelChecksum,
        faceNetModelConfig.modelChecksumMethod,
        faceNetModelConfig.overrideCacheOnWrongChecksum
    )

    actual fun encode(image: Image, traceId: String?): DataBytes {
        Napier.d(tag = traceId) { "Encoding sdk image [${image.width},${image.height}]" }
        val resized = resizeImg(image, faceNetModelConfig.inputWidth, faceNetModelConfig.inputHeight)
        val pixels =
            resized.colors
                .flatMap {
                    when (image.colorType) {
                        ImageColorType.RGB -> {
                            val gray = (0.299 * it.red() + 0.587 * it.green() + 0.114 * it.blue()).toFloat()
                            listOf(gray, gray, gray)
                        }

                        ImageColorType.GRAY -> listOf(it.toFloat(), it.toFloat(), it.toFloat())
                        ImageColorType.BINARY -> throw IllegalArgumentException("Unsupported color type ${image.colorType.name}")
                    }
                }
                .toFloatArray()
        Napier.d(tag = traceId) { "Image pixes extracted from thee image" }
        return encodeInternal(pixels, traceId)
    }

    actual fun encode(image: NativeImage, traceId: String?): DataBytes {
        val debugTraceId = generateTraceID()
        val newWidth = faceNetModelConfig.inputWidth
        val newHeight = faceNetModelConfig.inputHeight
        Napier.d(tag = debugTraceId) {
            "Encoding native image [${CGImageGetWidth(image.ptr)},${CGImageGetHeight(image.ptr)}]"
        }
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
            pixels[i] = data[index].toFloat()
            pixels[i + 1] = data[index + 1].toFloat()
            pixels[i + 2] = data[index + 2].toFloat()
            i += 3
        }
        CGContextRelease(context)
        nativeHeap.free(data)
        Napier.d(tag = debugTraceId) { "CGImage created" }
        return encodeInternal(pixels, traceId)
    }

    private fun encodeInternal(pixels: FloatArray, traceId: String?): DataBytes {
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
        Napier.d(tag = traceId) { "Encoding CGImage, resolved ${bytes.size} bytes" }
        val inputs = mapOf(0 to bytes.toNSData())
        val outputs = mutableMapOf<Int, Any>(0 to NSData())
        interpreter.invoke(inputs, outputs)
        val template = (outputs[0] as NSData).toByteArray()
            .toList()
            .chunked(4)
            .flatMap { it.reversed() }
            .toByteArray()
        Napier.d(tag = traceId) { "Encoding done, result template size: ${template.size}" }
        return template.toNSData()
    }

}