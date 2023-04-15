package net.iriscan.sdk.core.image

import cnames.structs.CGImage
import net.iriscan.sdk.core.io.DataBytes
import net.iriscan.sdk.io.image.ImageFormat

/**
 * @author Slava Gornostal
 */
actual typealias NativeImage = CGImage

internal actual fun internalReadNativeImage(dataBytes: DataBytes): NativeImage = TODO("Not Implemented")

internal actual fun internalWriteNativeImage(
    image: NativeImage,
    format: ImageFormat
): DataBytes  = TODO("Not Implemented")