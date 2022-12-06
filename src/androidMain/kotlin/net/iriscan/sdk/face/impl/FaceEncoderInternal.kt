package net.iriscan.sdk.face.impl

import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.tf.InterpreterImpl

/**
 * @author Slava Gornostal
 */
internal actual class FaceEncoderInternal actual constructor(faceNetModel: ByteArray) {
    private val interpreter = InterpreterImpl(faceNetModel)

    actual fun encode(image: Image): ByteArray {
        TODO("Not implemented yet")
    }
}