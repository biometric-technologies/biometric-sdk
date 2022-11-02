package net.iriscan.sdk

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import net.iriscan.sdk.core.exception.SdkNotInitializedException
import net.iriscan.sdk.io.InputOutputOperations
import net.iriscan.sdk.iris.IrisOperations
import net.iriscan.sdk.iris.impl.IrisOperationsImpl
import net.iriscan.sdk.qc.QualityControlOperations

/**
 * @author Slava Gornostal
 */
object BiometricSdkFactory : BiometricSdk {
    private val instanceRef: AtomicRef<BiometricSdkOperationsImpl?> = atomic(null)

    override fun configure(config: BiometricSdkConfig?) {
        val instanceConfig = this.instanceRef.value?.config
        val configToSet = config ?: BiometricSdkConfig.builder().build()
        if (config != instanceConfig) {
            this.instanceRef.compareAndSet(this.instanceRef.value, BiometricSdkOperationsImpl(configToSet))
        }
    }

    override fun getInstance(): BiometricSdkOperations =
        instanceRef.value ?: throw SdkNotInitializedException("Initialize SDK by calling configure(..)")
}

private class BiometricSdkOperationsImpl(val config: BiometricSdkConfig) : BiometricSdkOperations {

    override fun io(): InputOutputOperations {
        TODO("Not implemented yet")
    }

    override fun qualityControl(): QualityControlOperations {
        TODO("Not implemented yet")
    }

    override fun iris(): IrisOperations = IrisOperationsImpl(config)

}