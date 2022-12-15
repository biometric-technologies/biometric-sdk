package net.iriscan.sdk.face.impl

import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.face.FaceNetModelConfiguration

/**
 * @author Slava Gornostal
 */
internal actual class FaceEncoderInternal actual constructor(
    faceNetModel: ByteArray,
    faceNetModelConfig: FaceNetModelConfiguration
) {
    actual fun encode(image: Image): ByteArray {
        TODO("Not implemented yet")
    }
}