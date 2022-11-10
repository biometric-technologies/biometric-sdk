package net.iriscan.sdk

import net.iriscan.sdk.core.exception.SdkNotInitializedException
import net.iriscan.sdk.face.FaceOperations
import net.iriscan.sdk.io.InputOutputOperations
import net.iriscan.sdk.iris.IrisOperations
import net.iriscan.sdk.iris.impl.IrisOperationsImpl
import net.iriscan.sdk.qc.QualityControlOperations

/**
 * @author Slava Gornostal
 */
object BiometricSdkFactory : BiometricSdk {
    private val defaultConfig = BiometricSdkConfig.builder().build()
    private var instanceRef: BiometricSdkOperations? = null
    override fun configure(config: BiometricSdkConfig?) {
        if (instanceRef != null) {
            return
        }
        this.instanceRef = BiometricSdkOperationsImpl(config ?: defaultConfig)
    }

    override fun getInstance(): BiometricSdkOperations =
        instanceRef ?: throw SdkNotInitializedException("Initialize SDK by calling configure(..)")
}

private class BiometricSdkOperationsImpl(val config: BiometricSdkConfig) : BiometricSdkOperations {

    override fun io(): InputOutputOperations {
        TODO("Not implemented yet")
    }

    override fun qualityControl(): QualityControlOperations {
        TODO("Not implemented yet")
    }

    override fun iris(): IrisOperations = IrisOperationsImpl(config)
    override fun face(): FaceOperations {
        TODO("Not implemented yet")
    }

}