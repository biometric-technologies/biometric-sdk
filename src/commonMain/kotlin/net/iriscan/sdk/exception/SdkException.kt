package net.iriscan.sdk.exception

/**
 * @author Slava Gornostal
 */
open class SdkException(override val message: String?, override val cause: Throwable?) :
    Exception(message, cause)

class SdkInitializationException(message: String?, cause: Throwable?) : SdkException(message, cause)

class SdkNotInitializedException(message: String?) : RuntimeException(message)