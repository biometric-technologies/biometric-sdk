package net.iriscan.sdk.io.exception

import net.iriscan.sdk.exception.SdkException

/**
 * @author Slava Gornostal
 */
class IOException(message: String, throwable: Throwable? = null) :
    SdkException(message, throwable)