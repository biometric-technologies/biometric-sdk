package net.iriscan.sdk

import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import net.iriscan.sdk.io.ResourceIOFactory
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * @author Slava Gornostal
 */
@OptIn(ExperimentalAtomicApi::class)
object BiometricSdkFactory : BiometricSdk {

    init {
        Napier.base(DebugAntilog())
    }

    private val instanceRef = AtomicReference<BiometricSdkOperations?>(null)
    override fun configBuilder(): BiometricSdkConfigBuilder = BiometricSdkConfigBuilder()

    override fun initialize(config: BiometricSdkConfig) {
        if (instanceRef.load() != null) {
            return
        }
        ResourceIOFactory.initialize(config.context)
        this.instanceRef.compareAndSet(null, BiometricSdkOperationsImpl(config))
    }

    override fun getInstance(): BiometricSdkOperations? {
        val instance = instanceRef.load()
        if (instance == null) {
            Napier.e("Biometric SDK is not ready. Initialize SDK by calling initialize(...)")
        }
        return instance
    }

}
