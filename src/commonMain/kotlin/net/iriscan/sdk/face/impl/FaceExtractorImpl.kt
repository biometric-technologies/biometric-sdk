package net.iriscan.sdk.face.impl

import net.iriscan.sdk.core.image.Image

/**
 * @author Slava Gornostal
 */
internal expect class FaceExtractorInternal() {
    fun extract(image: Image): Image
}