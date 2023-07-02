package net.iriscan.sdk.io

import net.iriscan.sdk.core.PlatformContext
import net.iriscan.sdk.core.io.HashMethod
import net.iriscan.sdk.io.exception.IOException

/**
 * @author Slava Gornostal
 */
interface ResourceIO {

    /**
     * location:path
     *
     * available locations:
     * assets - android only
     * bundle - ios only
     * classpath - android/jvm
     * file - android/jvm/ios
     * https - any
     *
     * @throws IOException
     * */
    @Throws(IOException::class)
    fun read(path: String): ByteArray

    fun calculateHash(data: ByteArray, method: HashMethod): String

    fun cacheExists(name: String): Boolean

    @Throws(IOException::class)
    fun cacheSave(name: String, data: ByteArray)

    @Throws(IOException::class)
    fun cacheLoad(name: String): ByteArray

    fun cacheDelete(name: String): Boolean

    @Throws(IOException::class)
    fun readOrCacheLoadData(
        name: String,
        path: String
    ): ByteArray

    @Throws(IOException::class)
    fun readOrCacheLoadData(
        name: String,
        path: String,
        modelCheckSum: String,
        modelChecksumMethod: HashMethod,
        overrideOnWrongChecksum: Boolean
    ): ByteArray

    @Throws(IOException::class)
    fun readOrCacheLoadPath(
        name: String,
        path: String,
    ): String

    @Throws(IOException::class)
    fun readOrCacheLoadPath(
        name: String,
        path: String,
        modelCheckSum: String,
        modelChecksumMethod: HashMethod,
        overrideOnWrongChecksum: Boolean
    ): String
}

internal expect class ResourceIOImpl(context: PlatformContext) : ResourceIO