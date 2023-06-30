package net.iriscan.sdk

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

internal class BiometricSdkOperationsImpl(config: BiometricSdkConfig) : BiometricSdkOperations {

    private val ioOperations = InputOutputOperationsImpl()
    private val irisOperations = config.iris?.let { IrisOperationsImpl(it) }
    private val faceOperations = config.face?.let { FaceOperationsImpl(it) }

    override fun io(): InputOutputOperations = ioOperations

    override fun qualityControl(): QualityControlOperations {
        TODO("Not implemented yet")
    }

    override fun iris(): IrisOperations =
        irisOperations ?: throw IllegalStateException("Please initialize SDK with iris first")

    override fun face(): FaceOperations =
        faceOperations ?: throw IllegalStateException("Please initialize SDK with face first")

}