package net.iriscan.sdk.io

import android.content.Context
import com.soywiz.korio.net.http.HttpClient
import com.soywiz.korio.util.checksum.CRC32
import com.soywiz.korio.util.checksum.compute
import com.soywiz.krypto.SHA256
import kotlinx.coroutines.runBlocking
import net.iriscan.sdk.core.PlatformContext
import net.iriscan.sdk.core.io.HashMethod
import net.iriscan.sdk.io.exception.IOException
import java.io.FileNotFoundException

/**
 * @author Slava Gornostal
 */
internal actual class ResourceIOImpl actual constructor(private val context: PlatformContext) : ResourceIO {

    private val client = HttpClient()
    override fun read(path: String): ByteArray = when {
        path.startsWith("assets:") -> try {
            context.assets.open(path.replace("assets:", "")).readBytes()
        } catch (e: java.io.IOException) {
            throw IOException("Could not read path: $path", e)
        }

        path.startsWith("classpath:") ->
            javaClass.getResourceAsStream(path.replace("classpath:", ""))
                ?.readBytes() ?: throw IOException("File $path not found")

        path.startsWith("file:") ->
            context.openFileInput(path.replace("file:", "")).readBytes()

        path.startsWith("https:") -> runBlocking { client.readBytes(path) }
        else -> throw IOException("Illegal path location: $path")
    }

    override fun calculateHash(data: ByteArray, method: HashMethod): String =
        when (method) {
            HashMethod.CRC32 -> CRC32.compute(data).toString(16)
            HashMethod.SHA256 -> SHA256.digest(data).hexLower
        }

    override fun cacheSave(name: String, data: ByteArray) {
        try {
            context.openFileOutput(name, Context.MODE_PRIVATE).use {
                it.write(data)
            }
        } catch (e: FileNotFoundException) {
            throw IOException("File not found", e)
        }
    }

    override fun cacheExists(name: String): Boolean = try {
        context.openFileInput(name)
        true
    } catch (e: FileNotFoundException) {
        false
    }

    override fun cacheLoad(name: String): ByteArray = try {
        context.openFileInput(name).readBytes()
    } catch (e: FileNotFoundException) {
        throw IOException("File not found", e)
    }

    override fun cacheDelete(name: String) = try {
        context.deleteFile(name)
    } catch (e: FileNotFoundException) {
        true
    }

    override fun readOrCacheLoadData(name: String, path: String): ByteArray =
        readOrCache(name, path).data

    override fun readOrCacheLoadData(
        name: String,
        path: String,
        modelCheckSum: String,
        modelChecksumMethod: HashMethod,
        overrideOnWrongChecksum: Boolean
    ): ByteArray = readOrCache(name, path).data

    override fun readOrCacheLoadPath(name: String, path: String): String =
        readOrCache(name, path).url

    override fun readOrCacheLoadPath(
        name: String,
        path: String,
        modelCheckSum: String,
        modelChecksumMethod: HashMethod,
        overrideOnWrongChecksum: Boolean
    ): String = readOrCache(name, path, modelCheckSum, modelChecksumMethod, overrideOnWrongChecksum)
        .url

    private fun readOrCache(
        name: String,
        path: String,
    ): CachedData =
        when (cacheExists(name)) {
            true -> CachedData(name, cacheLoad(name))
            else -> {
                val data = read(path)
                cacheSave(name, data)
                CachedData(name, data)
            }
        }

    private fun readOrCache(
        name: String,
        path: String,
        modelCheckSum: String,
        modelChecksumMethod: HashMethod,
        overrideOnWrongChecksum: Boolean
    ): CachedData {
        val modelExists = cacheExists(name)
        val data = readOrCacheLoadData(name, path)
        val checkSum = calculateHash(data, modelChecksumMethod)
        return if (!modelExists || (overrideOnWrongChecksum && checkSum != modelCheckSum)) {
            if (modelExists) {
                cacheDelete(name)
            }
            val newData = read(path)
            val newDataChecksum = calculateHash(newData, modelChecksumMethod)
            if (newDataChecksum != modelCheckSum) {
                throw IOException("Invalid $path checksum, expected: $newDataChecksum, provided: $modelCheckSum")
            }
            cacheSave(name, newData)
            CachedData(name, newData)
        } else {
            CachedData(name, data)
        }
    }

    private data class CachedData(val url: String, val data: ByteArray)
}
