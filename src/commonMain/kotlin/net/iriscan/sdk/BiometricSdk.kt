package net.iriscan.sdk

/**
 * @author Slava Gornostal
 *
 * SDK Interface
 */
interface BiometricSdk {
    /**
     * Initialized SDK, should be called before using any operations
     * */
    fun configure(config: BiometricSdkConfig? = null)

    /**
     * Get SDK operations
     *
     * @see BiometricSdkOperations
     * @throws SdkNotInitializedException
     * */
    fun getInstance(): BiometricSdkOperations
}

class SdkNotInitializedException(message: String) : Exception(message)