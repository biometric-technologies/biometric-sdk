package net.iriscan.sdk.face.impl

import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.io.DataBytes
import net.iriscan.sdk.face.FaceNetModelConfiguration

/**
 * @author Slava Gornostal
 */
internal expect class FaceEncoderInternal(faceNetModelConfig: FaceNetModelConfiguration) {
    fun encode(image: Image): DataBytes
}