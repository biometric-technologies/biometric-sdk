package net.iriscan.sdk.utils

import kotlinx.cinterop.*
import net.iriscan.sdk.core.image.*
import net.iriscan.sdk.core.image.toRgb
import platform.CoreGraphics.*

/**
 * @author Slava Gornostal
 *
 * A CGImage with the pixels of [image]: GRAY and BINARY images become gray and black-and-white
 * pictures ([toRgb]). Used by `convertToCGImage` and by the `Image` overload of the face extractor.
 */
internal fun imageToCGImage(image: Image): CGImageRef {
    val width = image.width.toULong()
    val height = image.height.toULong()
    val pixelCount = width * height
    val data = nativeHeap.allocArray<UIntVar>(pixelCount.toInt())
    for (i in 0 until pixelCount.toInt()) {
        val rgb = toRgb(image.colors[i], image.colorType)
        val rgba = (rgb.red() shl 24) or (rgb.green() shl 16) or (rgb.blue() shl 8) or 0
        data[i] = rgba.toUInt()
    }
    val colorSpace = CGColorSpaceCreateDeviceRGB()
    val bitmapInfo = CGImageAlphaInfo.kCGImageAlphaNoneSkipFirst.value or kCGBitmapByteOrder32Big
    val context = CGBitmapContextCreate(data, width, height, 8u, width * 4u, colorSpace, bitmapInfo)
    val cgImage = CGBitmapContextCreateImage(context)
    CGContextRelease(context)
    nativeHeap.free(data)
    return cgImage!!
}

internal fun cgImageToImage(image: CGImageRef): Image {
    val width = CGImageGetWidth(image)
    val height = CGImageGetHeight(image)
    val pixelCount = width * height
    val data = nativeHeap.allocArray<UIntVar>(pixelCount.toInt())
    val colorSpace = CGColorSpaceCreateDeviceRGB()
    val bitmapInfo = CGImageAlphaInfo.kCGImageAlphaNoneSkipFirst.value or kCGBitmapByteOrder32Big
    val context = CGBitmapContextCreate(data, width, height, 8u, width * 4u, colorSpace, bitmapInfo)
    CGContextDrawImage(context, CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()), image)
    val pixels = IntArray(pixelCount.toInt())
    for (i in pixels.indices) {
        val rgba = data[i]
        val r = (rgba and 0xFF000000u).toInt() shr 24
        val g = (rgba and 0x00FF0000u).toInt() shr 16
        val b = (rgba and 0x0000FF00u).toInt() shr 8
        pixels[i] = createColor(r, g, b)
    }
    CGContextRelease(context)
    nativeHeap.free(data)
    return Image(width.toInt(), height.toInt(), ImageColorType.RGB, pixels)
}