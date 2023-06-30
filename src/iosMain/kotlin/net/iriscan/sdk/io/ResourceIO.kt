package net.iriscan.sdk.io

import com.soywiz.korio.util.checksum.CRC32
import com.soywiz.korio.util.checksum.compute
import com.soywiz.krypto.SHA256
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking
import net.iriscan.sdk.core.PlatformContext
import net.iriscan.sdk.core.io.HashMethod
import net.iriscan.sdk.io.exception.IOException
import net.iriscan.sdk.utils.NSErrorException
import net.iriscan.sdk.utils.toNSData

/**
 * @author Slava Gornostal
 */
internal actual class ResourceIOImpl actual constructor(private val context: PlatformContext) : ResourceIO {

    private val client = HttpClient()
    private val bundle = NSBundle.mainBundle

    override fun read(path: String): ByteArray = when {

        path.startsWith("bundle:") -> {
            val pathParts = path.replace("bundle:", "").split(".")
            val bundlePath = bundle
                .pathForResource(pathParts[pathParts.size - 2], pathParts[pathParts.size - 1])!!
            NSData.dataWithContentsOfFile(bundlePath)?.toByteArray() ?: throw IOException("No file found at path $path")
        }

        path.startsWith("file:") ->
            NSData.dataWithContentsOfFile(path.replace("file:", ""))!!.toByteArray()

        path.startsWith("https:") -> runBlocking { client.readBytes(path) }
        else -> throw IOException("Illegal path: $path")
    }

    override fun calculateHash(data: ByteArray, method: HashMethod): String =
        when (method) {
            HashMethod.CRC32 -> CRC32.compute(data).toString(16)
            HashMethod.SHA256 -> SHA256.digest(data).base64
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
        NSData.dataWithContentsOfFile(getFilePath(name))!!.toByteArray()

}