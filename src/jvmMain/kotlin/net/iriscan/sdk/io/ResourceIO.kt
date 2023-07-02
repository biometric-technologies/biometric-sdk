package net.iriscan.sdk.io

import com.soywiz.korio.net.http.HttpClient
import com.soywiz.korio.util.checksum.CRC32
import com.soywiz.korio.util.checksum.compute
import com.soywiz.krypto.SHA256
import kotlinx.coroutines.runBlocking
import net.iriscan.sdk.core.PlatformContext
import net.iriscan.sdk.core.io.HashMethod
import net.iriscan.sdk.io.exception.IOException
import java.io.File
import java.nio.file.Files
import kotlin.io.path.writeBytes

/**
 * @author Slava Gornostal
 */
internal actual class ResourceIOImpl actual constructor(private val context: PlatformContext) : ResourceIO {
    private val client = HttpClient()
    override fun read(path: String): ByteArray = when {

        path.startsWith("classpath:") ->
            javaClass.getResourceAsStream(path.replace("classpath:", ""))
                ?.readBytes() ?: throw IOException("File $path not found")

        path.startsWith("file:") -> try {
            File(path.replace("file:", "")).readBytes()
        } catch (e: java.io.IOException) {
            throw IOException("Could not read file at path: $path", e)
        }

        path.startsWith("https:") -> runBlocking { client.readBytes(path) }
        else -> throw IOException("Illegal path: $path")
    }

    override fun calculateHash(data: ByteArray, method: HashMethod): String =
        when (method) {
            HashMethod.CRC32 -> CRC32.compute(data).toString(16)
            HashMethod.SHA256 -> SHA256.digest(data).hexLower
        }

    override fun cacheExists(name: String): Boolean {
        val cachePath = File("${System.getProperty("java.io.tmpdir")}/.biometric-sdk/$name").toPath()
        return Files.exists(cachePath)
    }

    override fun cacheSave(name: String, data: ByteArray) {
        val cachePath = File("${System.getProperty("java.io.tmpdir")}/.biometric-sdk/$name").toPath()
        Files.createFile(cachePath)
            .writeBytes(data)
    }

    override fun cacheLoad(name: String): ByteArray {
        val cachePath = File("${System.getProperty("java.io.tmpdir")}/.biometric-sdk/$name").toPath()
        return Files.readAllBytes(cachePath)
    }

    override fun cacheDelete(name: String): Boolean {
        val cachePath = File("${System.getProperty("java.io.tmpdir")}/.biometric-sdk/$name").toPath()
        return Files.deleteIfExists(cachePath)
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