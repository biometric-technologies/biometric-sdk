package net.iriscan.sdk

import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import net.iriscan.sdk.io.ResourceIOFactory

/**
 * @author Slava Gornostal
 */
object BiometricSdkFactory : BiometricSdk {

    init {
        Napier.base(DebugAntilog())
    }

    private val instanceRef: AtomicRef<BiometricSdkOperations?> = atomic(null)
    override fun configBuilder(): BiometricSdkConfigBuilder = BiometricSdkConfigBuilder()

    override fun initialize(config: BiometricSdkConfig) {
        if (instanceRef.value != null) {
            return
        }
        ResourceIOFactory.initialize(config.context)
        this.instanceRef.lazySet(BiometricSdkOperationsImpl(config))
    }

    override fun getInstance(): BiometricSdkOperations? {
        Napier.e("Biometric SDK is not ready. Initialize SDK by calling initialize(...)")
        return instanceRef.value
    }

}
