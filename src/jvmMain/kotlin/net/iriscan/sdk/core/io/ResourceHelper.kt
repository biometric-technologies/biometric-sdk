package net.iriscan.sdk.core.io

import com.soywiz.korio.net.http.HttpClient
import com.soywiz.korio.util.checksum.CRC32
import com.soywiz.korio.util.checksum.compute
import kotlinx.coroutines.runBlocking
import net.iriscan.sdk.io.exception.IOException
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.io.path.writeBytes

/**
 * @author Slava Gornostal
 */
actual class ResourceHelper {
    private val client = HttpClient()
    actual fun read(url: String): ByteArray = readUrl(url)

    actual fun cacheAndRead(name: String, url: String, checksum: Int): ByteArray {
        if (url.startsWith("assets://")) {
            val bytes = readUrl(url)
            if (checksum > 0) {
                checkCrc32(bytes, checksum)
            }
        }
        return try {
            javaClass.getResourceAsStream(name)!!.readBytes()
        } catch (e: FileNotFoundException) {
            val bytes = readUrl(url)
            if (checksum > 0) {
                checkCrc32(bytes, checksum)
            }
            Files.createFile(File("${System.getProperty("java.io.tmpdir")}/.biometric-sdk/$name").toPath())
                .writeBytes(bytes)
            bytes
        }
    }

    actual fun cacheAndGetPath(name: String, url: String, checksum: Int): String {
        if (url.startsWith("assets://")) {
            val path = url.replace("assets://", "/")
            if (checksum > 0) {
                val bytes = readUrl(url)
                checkCrc32(bytes, checksum)
            }
            return path
        }
        val cachePath = File("${System.getProperty("java.io.tmpdir")}/.biometric-sdk/$name").toPath()
        if (Files.exists(cachePath)) {
            return cachePath.absolutePathString()
        }
        val bytes = runBlocking { client.readBytes(url) }
        if (checksum > 0) {
            checkCrc32(bytes, checksum)
        }
        Files.createFile(File("${System.getProperty("java.io.tmpdir")}/.biometric-sdk/$name").toPath())
            .writeBytes(bytes)
        return name
    }

    private fun readUrl(url: String): ByteArray = when {
        url.startsWith("assets://") ->
            javaClass.getResource(url.replace("assets://", "/")).readBytes()

        url.startsWith("https://") -> runBlocking { client.readBytes(url) }
        else -> throw IllegalArgumentException("Illegal URL: $url")
    }

    private fun checkCrc32(bytes: ByteArray, expected: Int) {
        val computedChecksum = CRC32.compute(bytes)
        if (computedChecksum != expected) {
            throw IOException("Invalid data checksum, expected: $expected given: $computedChecksum")
        }
    }
}

actual object ResourceHelperFactory {

    @Volatile
    private var instance: ResourceHelper? = null
    fun initialize() {
        this.instance = ResourceHelper()
    }

    actual fun getInstance(): ResourceHelper =
        this.instance ?: throw IllegalStateException("Resource helper was not initialized")
}