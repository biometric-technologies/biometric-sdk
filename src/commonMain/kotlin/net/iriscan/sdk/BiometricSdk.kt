package net.iriscan.sdk

import net.iriscan.sdk.io.exception.IOException

/**
 * @author Slava Gornostal
 *
 * SDK Interface
 */
interface BiometricSdk {
    /**
     * Initialized SDK, should be called before using any operations
     * */
    @Throws(SdkInitializeException::class, IOException::class)
    fun configure(config: BiometricSdkConfig)

    /**
     * Get SDK operations
     *
     * @see BiometricSdkOperations
     * */
    fun getInstance(): BiometricSdkOperations
}

class SdkInitializeException(message: String) : Exception(message)