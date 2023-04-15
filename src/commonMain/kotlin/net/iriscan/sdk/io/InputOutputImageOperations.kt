package net.iriscan.sdk.io

import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.image.NativeImage
import net.iriscan.sdk.core.io.DataBytes
import net.iriscan.sdk.io.image.ImageFormat

/**
 * @author Slava Gornostal
 */
interface InputOutputImageOperations {
    fun readImage(data: ByteArray): Image
    fun readNativeImage(data: DataBytes): NativeImage
    fun writeImage(image: Image, format: ImageFormat): ByteArray
    fun writeNativeImage(image: NativeImage, format: ImageFormat): DataBytes
}