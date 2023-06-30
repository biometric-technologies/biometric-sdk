package net.iriscan.sdk

import net.iriscan.sdk.exception.SdkNotInitializedException

/**
 * @author Slava Gornostal
 *
 * SDK Interface
 */
interface BiometricSdk {

    fun configBuilder(): BiometricSdkConfigBuilder

    /**
     * Initialized SDK, should be called before using any operations
     * */
    @Throws(SdkNotInitializedException::class)
    fun initialize(config: BiometricSdkConfig)

    /**
     * Get SDK operations
     *
     * @see BiometricSdkOperations
     * */
    fun getInstance(): BiometricSdkOperations
}
