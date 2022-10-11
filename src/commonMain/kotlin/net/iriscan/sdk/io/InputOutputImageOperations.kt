package net.iriscan.sdk.io

import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.io.image.ImageFormat

/**
 * @author Slava Gornostal
 */
interface InputOutputImageOperations {
    fun readImage(data: ByteArray): Image
    fun readImage(filePath: String): Image
    fun writeImage(image: Image, filePath: String, format: ImageFormat)
    fun writeAsByteArrayImage(image: Image, format: ImageFormat): ByteArray
}