package net.iriscan.sdk.core.io

/**
 * @author Slava Gornostal
 */
expect class ResourceHelper {
    fun read(url: String): ByteArray
    fun cacheAndRead(name: String, url: String): ByteArray
    fun cacheAndGetPath(name: String, url: String): String
}

expect object ResourceHelperFactory {
    fun getInstance(): ResourceHelper
}