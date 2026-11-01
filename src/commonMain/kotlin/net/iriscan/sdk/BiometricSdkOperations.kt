package net.iriscan.sdk

import io.github.aakira.napier.Napier
import net.iriscan.sdk.face.FaceOperations
import net.iriscan.sdk.face.impl.FaceOperationsImpl
import net.iriscan.sdk.io.InputOutputOperations
import net.iriscan.sdk.io.InputOutputOperationsImpl
import net.iriscan.sdk.iris.IrisOperations
import net.iriscan.sdk.iris.impl.IrisOperationsImpl
import net.iriscan.sdk.qc.QualityControlOperations

/**
 * @author Slava Gornostal
 */
interface BiometricSdkOperations {
    fun io(): InputOutputOperations
    fun qualityControl(): QualityControlOperations
    fun iris(): IrisOperations
    fun face(): FaceOperations
}

/**
 * @param createFaceOperations sets up the face part; replaceable in tests.
 */
internal class BiometricSdkOperationsImpl(
    config: BiometricSdkConfig,
    createFaceOperations: (FaceConfig) -> FaceOperations = ::FaceOperationsImpl,
) : BiometricSdkOperations {

    private val ioOperations = InputOutputOperationsImpl()
    private val irisOperations = config.iris?.let { IrisOperationsImpl(it) }

    /**
     * Face is set up apart from iris. It loads native libraries (OpenCV and TensorFlow Lite on the
     * JVM) and a model file, and any of them can be missing on a given machine. If that fails, the
     * error is kept and reported by [face], and iris and I/O keep working.
     */
    private val faceOperations: Result<FaceOperations>? = config.face?.let { faceConfig ->
        runCatching { createFaceOperations(faceConfig) }
            .onFailure { Napier.e("Face operations are unavailable", it) }
    }

    override fun io(): InputOutputOperations = ioOperations

    override fun qualityControl(): QualityControlOperations {
        TODO("Not implemented yet")
    }

    override fun iris(): IrisOperations =
        irisOperations ?: throw IllegalStateException("Please initialize SDK with iris first")

    override fun face(): FaceOperations {
        val result = faceOperations ?: throw IllegalStateException("Please initialize SDK with face first")
        return result.getOrElse { cause ->
            throw IllegalStateException("Face operations are unavailable: ${cause::class.simpleName}: ${cause.message}", cause)
        }
    }

}