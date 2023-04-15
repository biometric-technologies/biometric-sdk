package net.iriscan.sdk.core.image

import net.iriscan.sdk.core.io.DataBytes
import net.iriscan.sdk.io.image.ImageFormat

/**
 * @author Slava Gornostal
 */
expect class NativeImage

internal expect fun internalReadNativeImage(dataBytes: DataBytes): NativeImage
internal expect fun internalWriteNativeImage(image: NativeImage, format: ImageFormat): DataBytes