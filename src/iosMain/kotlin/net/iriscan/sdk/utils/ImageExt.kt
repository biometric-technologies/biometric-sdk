package net.iriscan.sdk.utils

import cnames.structs.CGImage
import kotlinx.cinterop.*
import net.iriscan.sdk.core.image.*
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreGraphics.*

/**
 * @author Slava Gornostal
 */
internal fun imageToCGImage(image: Image): CGImage {
    val colorSpace = CGColorSpaceCreateDeviceRGB()
    val bytes = image.colors.flatMap { listOf(it.red().toUByte(), it.green().toUByte(), it.blue().toUByte(), 0u) }
        .toUByteArray()
        .refTo(0)
    val ctx = CGBitmapContextCreate(
        data = bytes,
        width = image.width.toULong(),
        height = image.height.toULong(),
        bitsPerComponent = 8u,
        bytesPerRow = (4 * image.width).toULong(),
        space = colorSpace,
        bitmapInfo = CGImageAlphaInfo.kCGImageAlphaNoneSkipLast.value,
    )
    CGColorSpaceRelease(colorSpace)
    val cgImage = CGBitmapContextCreateImage(ctx)
    CGContextRelease(ctx)
    return cgImage!!.pointed
}

internal fun cgImageToImage(image: CGImage): Image =
    autoreleasepool {
        val data = CGDataProviderCopyData(CGImageGetDataProvider(image.ptr))!!
        val dataBytes = CFDataGetBytePtr(data)!!
        val dataLength = CFDataGetLength(data).toInt()
        val height = CGImageGetHeight(image.ptr).toInt()
        val width = CGImageGetWidth(image.ptr).toInt()
        val bitsPerPixel = CGImageGetBitsPerPixel(image.ptr).toInt()
        val bitsPerComponent = CGImageGetBitsPerComponent(image.ptr).toInt()
        val bytesPerRow = CGImageGetBytesPerRow(image.ptr).toInt()
        val bytesPerPixel = bytesPerRow / width
        val componentsPerPixel = bitsPerPixel / bitsPerComponent
        val pixels = IntArray(height * width)
        var j = 0
        for (i in 0 until dataLength step bytesPerPixel) {
            pixels[j++] = when (componentsPerPixel) {
                1, 2 -> dataBytes[i].toInt()
                3, 4 -> (dataBytes[i].toInt() shl 16) or (dataBytes[i + 1].toInt() shl 8) or dataBytes[i + 2].toInt() or 0x000000
                else -> throw IllegalStateException("Unsupported components per pixel: $componentsPerPixel")
            }
        }
        Image(width, height, ImageColorType.RGB, pixels)
    }