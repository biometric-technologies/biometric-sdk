package net.iriscan.sdk.io

import com.soywiz.korio.util.checksum.CRC32
import com.soywiz.korio.util.checksum.compute
import com.soywiz.krypto.SHA256
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import kotlinx.coroutines.runBlocking
import net.iriscan.sdk.core.PlatformContext
import net.iriscan.sdk.core.io.HashMethod
import net.iriscan.sdk.core.utils.getBytes
import net.iriscan.sdk.io.exception.IOException
import java.io.File
import java.io.FileNotFoundException

/**
 * @author Slava Gornostal
 */
internal actual class ResourceIOImpl actual constructor(private val context: PlatformContext) : ResourceIO {

    private val client = HttpClient(OkHttp) {
        engine {
            config {
                followRedirects(true)
            }
        }
    }

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

        path.startsWith("https:") -> runBlocking {
            client.getBytes(path) { progress ->
                Napier.i("Reading data from url: $path - $progress % complete")
            }
        }

        else -> throw IOException("Illegal path location: $path")
    }

    override fun calculateHash(data: ByteArray, method: HashMethod): String =
        when (method) {
            HashMethod.CRC32 -> CRC32.compute(data).toString(16)
            HashMethod.SHA256 -> SHA256.digest(data).hexLower
        }

    override fun cacheSave(name: String, data: ByteArray) {
        try {
            File(context.cacheDir, name).writeBytes(data)
        } catch (e: java.io.IOException) {
            throw IOException("File not found", e)
        }
    }

    override fun cacheExists(name: String): Boolean = try {
        File(context.cacheDir, name).exists()
    } catch (e: java.io.IOException) {
        false
    }

    override fun cacheLoad(name: String): ByteArray = try {
        File(context.cacheDir, name).readBytes()
    } catch (e: FileNotFoundException) {
        throw IOException("File not found", e)
    }

    override fun cacheDelete(name: String) = try {
        File(context.cacheDir, name).delete()
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
    ): CachedData {
        if (!path.startsWith("https:")) {
            return CachedData(name, read(path))
        }
        return when (cacheExists(name)) {
            true -> CachedData(name, cacheLoad(name))
            else -> {
                Napier.i("Cache $name does not exists, loading from path: $path")
                val data = read(path)
                cacheSave(name, data)
                CachedData(name, data)
            }
        }
    }

    private fun readOrCache(
        name: String,
        path: String,
        modelCheckSum: String,
        modelChecksumMethod: HashMethod,
        overrideOnWrongChecksum: Boolean
    ): CachedData {
        if (!path.startsWith("https:")) {
            return CachedData(name, downloadAndVerifyChecksum(path, modelCheckSum, modelChecksumMethod))
        }
        val modelExists = cacheExists(name)
        val data = if (modelExists) {
            val data = cacheLoad(name)
            if (overrideOnWrongChecksum) {
                val checkSum = calculateHash(data, modelChecksumMethod)
                if (checkSum != modelCheckSum) {
                    Napier.i("Cache $name exists with different checksum $checkSum, loading new from path: $path")
                    val newData = downloadAndVerifyChecksum(path, modelCheckSum, modelChecksumMethod)
                    cacheSave(name, newData)
                    Napier.i("Cache $name saved")
                    newData
                } else {
                    data
                }
            } else {
                data
            }
        } else {
            Napier.i("Cache $name does not exists, loading from path: $path")
            val data = downloadAndVerifyChecksum(path, modelCheckSum, modelChecksumMethod)
            cacheSave(name, data)
            Napier.i("Cache $name saved")
            data
        }
        return CachedData(name, data)
    }

    private fun downloadAndVerifyChecksum(
        path: String,
        modelCheckSum: String,
        modelChecksumMethod: HashMethod
    ): ByteArray {
        val data = read(path)
        val checksum = calculateHash(data, modelChecksumMethod)
        if (checksum != modelCheckSum) {
            throw IOException("Invalid $path checksum, expected: $checksum, provided: $modelCheckSum")
        }
        return data
    }

    private data class CachedData(val url: String, val data: ByteArray)
}
