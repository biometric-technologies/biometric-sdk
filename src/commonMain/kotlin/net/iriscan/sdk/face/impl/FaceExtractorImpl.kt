package net.iriscan.sdk.face.impl

import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.image.NativeImage
import net.iriscan.sdk.core.utils.generateTraceID

/**
 * @author Slava Gornostal
 */
internal expect class FaceExtractorInternal() {
    fun extract(image: Image, rotateOnWrongOrientation: Boolean, traceId: String? = generateTraceID()): Image?
    fun extract(
        image: NativeImage,
        rotateOnWrongOrientation: Boolean,
        traceId: String? = generateTraceID()
    ): NativeImage?
}