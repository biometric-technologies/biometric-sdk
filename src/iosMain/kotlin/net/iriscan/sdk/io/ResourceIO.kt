package net.iriscan.sdk.io

import com.soywiz.korio.util.checksum.CRC32
import com.soywiz.korio.util.checksum.compute
import com.soywiz.krypto.SHA256
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.engine.darwin.*
import kotlinx.coroutines.runBlocking
import net.iriscan.sdk.core.PlatformContext
import net.iriscan.sdk.core.io.HashMethod
import net.iriscan.sdk.core.utils.getBytes
import net.iriscan.sdk.io.exception.IOException
import net.iriscan.sdk.utils.NSErrorException
import net.iriscan.sdk.utils.throwError
import net.iriscan.sdk.utils.toByteArray
import net.iriscan.sdk.utils.toNSData
import platform.Foundation.*

/**
 * @author Slava Gornostal
 */
internal actual class ResourceIOImpl actual constructor(private val context: PlatformContext) : ResourceIO {

    private val client = HttpClient(Darwin) {
        engine {
            configureRequest {
                setAllowsCellularAccess(true)
            }
        }
    }
    private val bundle = NSBundle.mainBundle

    override fun read(path: String): ByteArray = when {

        path.startsWith("bundle:") -> {
            val pathParts = path.replace("bundle:", "").split(".")
            val pathUrl = pathParts.take(pathParts.size - 1).joinToString(separator = ".")
            val pathExt = pathParts.lastOrNull() ?: throw IOException("Invalid path: $path")
            val bundlePath = bundle.pathForResource(pathUrl, pathExt)
                ?: throw IOException("No file found at path $path")
            NSData.dataWithContentsOfFile(bundlePath)?.toByteArray()
                ?: throw IOException("No file found at path $path")
        }

        path.startsWith("file:") ->
            NSData.dataWithContentsOfFile(path.replace("file:", ""))?.toByteArray()
                ?: throw IOException("No file found at path $path")

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

    override fun cacheExists(name: String): Boolean = try {
        val filePath = getFilePath(name)
        NSFileManager.defaultManager.fileExistsAtPath(filePath)
    } catch (e: NSErrorException) {
        false
    }

    override fun cacheSave(name: String, data: ByteArray) {
        NSFileManager.defaultManager.createFileAtPath(getFilePath(name), data.toNSData(), null)
    }

    override fun cacheLoad(name: String): ByteArray =
        NSData.dataWithContentsOfFile(getFilePath(name))?.toByteArray()
            ?: throw IOException("Cache $name not found")

    override fun cacheDelete(name: String): Boolean =
        NSFileManager.defaultManager.removeItemAtPath(getFilePath(name), null)

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
        val url = getFilePath(name)
        return when (cacheExists(name)) {
            true -> CachedData(url, cacheLoad(name))
            else -> {
                Napier.i("Cache $name does not exists, loading from path: $path")
                val data = read(path)
                cacheSave(name, data)
                Napier.i("Cache $name saved")
                CachedData(url, data)
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
        return CachedData(getFilePath(name), data)
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

    private fun getFilePath(name: String) = throwError { errorPointer ->
        NSFileManager.defaultManager.URLForDirectory(NSCachesDirectory, NSUserDomainMask, null, true, errorPointer)!!
            .URLByAppendingPathComponent(name)?.path ?: throw IOException("Invalid file name $name")
    }
}