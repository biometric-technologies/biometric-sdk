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

        path.startsWith("file:") ->
            File(path.replace("file:", "")).readBytes()

        path.startsWith("https:") -> runBlocking { client.readBytes(path) }
        else -> throw IOException("Illegal path: $path")
    }

    override fun calculateHash(data: ByteArray, method: HashMethod): String =
        when (method) {
            HashMethod.CRC32 -> CRC32.compute(data).toString(16)
            HashMethod.SHA256 -> SHA256.digest(data).base64
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
}