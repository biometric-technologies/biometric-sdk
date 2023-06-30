package net.iriscan.sdk

import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import net.iriscan.sdk.exception.SdkNotInitializedException
import net.iriscan.sdk.io.ResourceIOFactory

/**
 * @author Slava Gornostal
 */
object BiometricSdkFactory : BiometricSdk {

    private var instanceRef: BiometricSdkOperations? = null
    override fun configBuilder(): BiometricSdkConfigBuilder = BiometricSdkConfigBuilder()

    override fun initialize(config: BiometricSdkConfig) {
        if (instanceRef != null) {
            return
        }
        Napier.base(DebugAntilog())
        ResourceIOFactory.initialize(config.context)
        this.instanceRef = BiometricSdkOperationsImpl(config)
    }

    override fun getInstance(): BiometricSdkOperations =
        instanceRef ?: throw SdkNotInitializedException("Initialize SDK by calling initialize(...)")

}
