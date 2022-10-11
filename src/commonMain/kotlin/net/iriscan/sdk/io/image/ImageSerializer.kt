package net.iriscan.sdk.io.image

import net.iriscan.sdk.core.image.Image

/**
 * @author Slava Gornostal
 *
 * Implement this interface for custom image format serializer
 *
 * @see Image
 * @see net.iriscan.sdk.BiometricSdkConfig
 */
interface ImageSerializer {
    fun canRead(data: ByteArray): Boolean
    fun read(data: ByteArray): Image
    fun write(image: Image): ByteArray
}