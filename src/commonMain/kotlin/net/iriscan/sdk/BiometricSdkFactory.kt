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
object BiometricSdkFactory : BiometricSdk {

    private var instanceRef: BiometricSdkOperations? = null
    override fun configure(config: BiometricSdkConfig) {
        if (instanceRef != null) {
            return
        }
        this.instanceRef = BiometricSdkOperationsImpl(config)
    }

    override fun getInstance(): BiometricSdkOperations =
        instanceRef ?: throw SdkNotInitializedException("Initialize SDK by calling configure(..)")
}

private class BiometricSdkOperationsImpl(val config: BiometricSdkConfig) : BiometricSdkOperations {

    private val ioOperations = lazy { InputOutputOperationsImpl() }
    private val irisOperations = lazy { IrisOperationsImpl(config.iris!!) }
    private val faceOperations = lazy { FaceOperationsImpl(config.face!!) }

    override fun io(): InputOutputOperations = ioOperations.value

    override fun qualityControl(): QualityControlOperations {
        TODO("Not implemented yet")
    }

    override fun iris(): IrisOperations = when (config.iris) {
        null -> throw IllegalStateException("Please initialize SDK with iris first")
        else -> irisOperations.value
    }

    override fun face(): FaceOperations = when (config.face) {
        null -> throw IllegalStateException("Please initialize SDK with face first")
        else -> faceOperations.value
    }

}