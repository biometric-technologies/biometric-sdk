package net.iriscan.sdk.face.impl

import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.image.NativeImage

/**
 * @author Slava Gornostal
 */
internal expect class FaceExtractorInternal() {
    fun extract(image: Image): Image?
    fun extract(image: NativeImage): NativeImage?
}