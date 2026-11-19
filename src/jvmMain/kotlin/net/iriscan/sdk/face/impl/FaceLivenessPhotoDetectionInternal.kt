package net.iriscan.sdk.face.impl

import net.iriscan.sdk.core.image.NativeImage
import net.iriscan.sdk.face.LivenessModelPhotoConfiguration
import net.iriscan.sdk.tf.InterpreterImpl
import java.awt.RenderingHints
import java.awt.image.BufferedImage

/**
 * @author Slava Gornostal
 */
internal actual class FaceLivenessPhotoDetectionInternal actual constructor(
    private val modelConfig: LivenessModelPhotoConfiguration
) {

    private val interpreter = InterpreterImpl(
        "liveness-photo.tflite",
        modelConfig.path,
        modelConfig.modelChecksum,
        modelConfig.modelChecksumMethod,
        modelConfig.overrideCacheOnWrongChecksum
    )

    actual fun validate(image: NativeImage, traceId: String?): Boolean =
        calculateScore(image) > modelConfig.threshold

    actual fun score(image: NativeImage, traceId: String?): Double =
        calculateScore(image)

    /**
     * The model (DeePixBiS) takes a 3 x H x W planar tensor normalized to [-1, 1]
     * and returns a 14 x 14 map of live probabilities; the score is its mean
     * (1.0 = live). Same preprocessing as on Android.
     */
    private fun calculateScore(image: NativeImage): Double {
        val resized = resize(image, modelConfig.inputWidth, modelConfig.inputHeight)
        val data = toPlanarTensor(resized)
        val modelInputs = mapOf(0 to data)
        val modelOutputs = mutableMapOf<Int, Any>(0 to FloatArray(OUTPUT_MAP_SIZE))
        interpreter.invoke(modelInputs, modelOutputs)
        return (modelOutputs[0] as FloatArray).average()
    }

    private fun resize(image: NativeImage, width: Int, height: Int): BufferedImage {
        val output = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = output.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(image, 0, 0, width, height, null)
        g.dispose()
        return output
    }

    private fun toPlanarTensor(image: BufferedImage): FloatArray {
        val w = image.width
        val h = image.height
        val plane = w * h
        val out = FloatArray(3 * plane)
        for (y in 0 until h) for (x in 0 until w) {
            val rgb = image.getRGB(x, y)
            val i = y * w + x
            out[i] = (((rgb shr 16) and 0xFF) / 255f - 0.5f) / 0.5f
            out[plane + i] = (((rgb shr 8) and 0xFF) / 255f - 0.5f) / 0.5f
            out[2 * plane + i] = ((rgb and 0xFF) / 255f - 0.5f) / 0.5f
        }
        return out
    }

    private companion object {
        const val OUTPUT_MAP_SIZE = 14 * 14
    }
}
