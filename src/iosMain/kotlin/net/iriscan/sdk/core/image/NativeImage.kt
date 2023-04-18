package net.iriscan.sdk.core.image

import cnames.structs.CGImage
import kotlinx.cinterop.*
import net.iriscan.sdk.core.io.DataBytes
import net.iriscan.sdk.io.image.ImageFormat
import platform.CoreFoundation.*
import platform.CoreGraphics.*
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
import platform.ImageIO.CGImageSourceCreateImageAtIndex
import platform.ImageIO.CGImageSourceCreateWithData
import platform.ImageIO.kCGImageSourceShouldCache

/**
 * @author Slava Gornostal
 */
actual typealias NativeImage = CGImage

internal actual fun internalReadNativeImage(dataBytes: DataBytes): NativeImage {
    val dataPointer = dataBytes.bytes!!.reinterpret<UByteVar>()
    val dataLength = dataBytes.length.toLong()
    val cfData = CFDataCreateWithBytesNoCopy(kCFAllocatorDefault, dataPointer, dataLength, null)!!
    val keys = arrayOf(kCGImageSourceShouldCache)
    val values = arrayOf(kCFBooleanFalse)
    val cfOptions = CFDictionaryCreateMutable(
        kCFAllocatorDefault,
        1,
        null,
        null
    )
    CFDictionarySetValue(cfOptions, keys[0]!!.reinterpret<CFTypeRefVar>(), values[0]!!.reinterpret<CFTypeRefVar>())
    val imageSource = CGImageSourceCreateWithData(cfData, cfOptions)
    val image = CGImageSourceCreateImageAtIndex(imageSource, 0, null)!!
    return image.pointed
}

internal actual fun internalWriteNativeImage(
    image: NativeImage,
    format: ImageFormat
): DataBytes {
    val width = CGImageGetWidth(image.ptr)
    val height = CGImageGetHeight(image.ptr)
    val pixelCount = width * height
    val bytesPerRow = width * 4u
    val data = nativeHeap.allocArray<UIntVar>(pixelCount.toInt())
    val colorSpace = CGColorSpaceCreateDeviceRGB()
    val bitmapInfo = CGImageAlphaInfo.kCGImageAlphaNoneSkipFirst.value or kCGBitmapByteOrder32Big
    val context = CGBitmapContextCreate(data, width, height, 8u, bytesPerRow, colorSpace, bitmapInfo)
    CGContextDrawImage(context, CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()), image.ptr)
    val dataBytes = CGBitmapContextGetData(context)!!.reinterpret<UByteVar>()
    val dataLength = height * bytesPerRow
    val cfData = CFDataCreateWithBytesNoCopy(kCFAllocatorDefault, dataBytes, dataLength.toLong(), null)!!
    val nsData = NSData.dataWithBytes(
        CFDataGetBytePtr(cfData),
        CFDataGetLength(cfData).toULong()
    )
    CGContextRelease(context)
    CFRelease(cfData)
    return nsData
}