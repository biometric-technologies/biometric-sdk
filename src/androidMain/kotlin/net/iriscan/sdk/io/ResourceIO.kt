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
        path.startsWith("assets:") ->
            context.assets.open(path.replace("assets:", "")).readBytes()

        path.startsWith("classpath:") ->
            javaClass.getResourceAsStream(path.replace("classpath:", ""))
                ?.readBytes() ?: throw IOException("File $path not found")

        path.startsWith("file:") ->
            context.openFileInput(path.replace("file:", "")).readBytes()

        path.startsWith("https:") -> runBlocking { client.readBytes(path) }
        else -> throw IOException("Illegal path: $path")
    }

    override fun calculateHash(data: ByteArray, method: HashMethod): String =
        when (method) {
            HashMethod.CRC32 -> CRC32.compute(data).toString(16)
            HashMethod.SHA256 -> SHA256.digest(data).base64
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

}
