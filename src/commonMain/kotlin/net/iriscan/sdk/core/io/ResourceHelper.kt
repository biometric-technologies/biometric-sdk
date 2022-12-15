package net.iriscan.sdk.core.io

/**
 * @author Slava Gornostal
 */
expect class ResourceHelper {
    fun getCached(name: String, url: String): ByteArray
}