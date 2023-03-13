package net.iriscan.sdk.core.io

import com.soywiz.korio.util.checksum.CRC32
import com.soywiz.korio.util.checksum.compute
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking
import net.iriscan.sdk.io.exception.IOException
import net.iriscan.sdk.utils.throwError
import net.iriscan.sdk.utils.toByteArray
import net.iriscan.sdk.utils.toNSData
import platform.Foundation.*
import platform.darwin.NSObject
import platform.darwin.NSObjectMeta

/**
 * @author Slava Gornostal
 */
actual class ResourceHelper {
    private class BundleMarker : NSObject() {
        companion object : NSObjectMeta()
    }

    private val client = HttpClient()
    private val bundle = NSBundle.mainBundle

    actual fun read(url: String): ByteArray = readUrl(url)

    actual fun cacheAndRead(name: String, url: String, checksum: Int): ByteArray {
        if (url.startsWith("assets://")) {
            return readUrl(url)
        }
        val pathToSave = getFilePath(name)
        if (NSFileManager.defaultManager.fileExistsAtPath(pathToSave)) {
            return NSData.dataWithContentsOfFile(pathToSave)!!.toByteArray()
        }
        val modelData = readUrl(url)
        if (checksum > 0) {
            checkCrc32(modelData, checksum)
        }
        NSFileManager.defaultManager.createFileAtPath(pathToSave, modelData.toNSData(), null)
        return modelData
    }

    actual fun cacheAndGetPath(name: String, url: String, checksum: Int): String {
        if (url.startsWith("assets://")) {
            val pathParts = url.replace("assets://", "").split(".")
            return bundle.pathForResource(pathParts[0], pathParts[1])!!
        }
        val pathToSave = getFilePath(name)
        if (NSFileManager.defaultManager.fileExistsAtPath(pathToSave)) {
            return pathToSave
        }
        val modelData = readUrl(url)
        if (checksum > 0) {
            checkCrc32(modelData, checksum)
        }
        NSFileManager.defaultManager.createFileAtPath(pathToSave, modelData.toNSData(), null)
        return pathToSave
    }

    private fun getFilePath(name: String) = throwError { errorPointer ->
        NSFileManager.defaultManager.URLForDirectory(NSCachesDirectory, NSUserDomainMask, null, true, errorPointer)!!
            .URLByAppendingPathComponent(name)!!.path!!
    }

    private fun readUrl(url: String): ByteArray = when {
        url.startsWith("assets://") -> {
            val pathParts = url.replace("assets://", "").split(".")
            val path = bundle
                .pathForResource(pathParts[pathParts.size - 2], pathParts[pathParts.size - 1])!!
            NSData.dataWithContentsOfFile(path)!!.toByteArray()
        }

        url.startsWith("https://") -> runBlocking { client.get(url).readBytes() }
        else -> throw IllegalArgumentException("Illegal URL: $url")
    }

    private fun checkCrc32(modelBytes: ByteArray, expected: Int) {
        val computedChecksum = CRC32.compute(modelBytes)
        if (computedChecksum != expected) {
            throw IOException("Invalid data checksum, expected: $expected given: $computedChecksum")
        }
    }

}

actual object ResourceHelperFactory {
    private var instance: ResourceHelper? = null
    fun initialize() {
        this.instance = ResourceHelper()
    }

    actual fun getInstance(): ResourceHelper =
        this.instance ?: throw IllegalStateException("Resource helper was not initialized")
}