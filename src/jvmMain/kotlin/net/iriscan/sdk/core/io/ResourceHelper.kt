package net.iriscan.sdk.core.io

/**
 * @author Slava Gornostal
 */
actual class ResourceHelper {
    actual fun read(url: String): ByteArray {
        TODO("Not yet implemented")
    }

    actual fun cacheAndRead(name: String, url: String, checksum: Int): ByteArray {
        TODO("Not yet implemented")
    }

    actual fun cacheAndGetPath(name: String, url: String, checksum: Int): String {
        TODO("Not yet implemented")
    }
}

actual object ResourceHelperFactory {
    actual fun getInstance(): ResourceHelper {
        TODO("Not yet implemented")
    }
}