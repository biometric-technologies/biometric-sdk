package net.iriscan.sdk.io

import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.io.image.ImageFormat

/**
 * @author Slava Gornostal
 */
interface InputOutputImageOperations {
    fun readImage(data: ByteArray): Image
    fun writeImage(image: Image, format: ImageFormat): ByteArray
}