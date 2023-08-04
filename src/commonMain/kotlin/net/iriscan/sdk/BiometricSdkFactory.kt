package net.iriscan.sdk

import com.soywiz.korio.concurrent.atomic.korAtomic
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import net.iriscan.sdk.io.ResourceIOFactory

/**
 * @author Slava Gornostal
 */
object BiometricSdkFactory : BiometricSdk {

    init {
        Napier.base(DebugAntilog())
    }

    private val instanceRef = korAtomic<BiometricSdkOperations?>(null)
    override fun configBuilder(): BiometricSdkConfigBuilder = BiometricSdkConfigBuilder()

    override fun initialize(config: BiometricSdkConfig) {
        if (instanceRef.value != null) {
            return
        }
        ResourceIOFactory.initialize(config.context)
        this.instanceRef.compareAndSet(null, BiometricSdkOperationsImpl(config))
    }

    override fun getInstance(): BiometricSdkOperations? {
        val instance = instanceRef.value
        if (instance == null) {
            Napier.e("Biometric SDK is not ready. Initialize SDK by calling initialize(...)")
        }
        return instance
    }

}
