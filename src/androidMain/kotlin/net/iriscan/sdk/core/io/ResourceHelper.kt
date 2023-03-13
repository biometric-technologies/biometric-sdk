package net.iriscan.sdk.core.io

import android.annotation.SuppressLint
import android.content.Context
import com.soywiz.korio.net.http.HttpClient
import com.soywiz.korio.util.checksum.CRC32
import com.soywiz.korio.util.checksum.compute
import kotlinx.coroutines.runBlocking
import net.iriscan.sdk.io.exception.IOException
import java.io.FileNotFoundException

/**
 * @author Slava Gornostal
 */
actual class ResourceHelper(private val context: Context) {
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
            context.openFileInput(name).readBytes()
        } catch (e: FileNotFoundException) {
            val bytes = readUrl(url)
            if (checksum > 0) {
                checkCrc32(bytes, checksum)
            }
            context.openFileOutput(name, Context.MODE_PRIVATE).use {
                it.write(bytes)
            }
            bytes
        }
    }

    actual fun cacheAndGetPath(name: String, url: String, checksum: Int): String {
        if (url.startsWith("assets://")) {
            val path = url.replace("assets://", "")
            if (checksum > 0) {
                val bytes = readUrl(url)
                checkCrc32(bytes, checksum)
            }
            return path
        }
        if (context.fileList().contains(name)) {
            return name
        }
        val bytes = runBlocking { client.readBytes(url) }
        if (checksum > 0) {
            checkCrc32(bytes, checksum)
        }
        context.openFileOutput(name, Context.MODE_PRIVATE).use {
            it.write(bytes)
        }
        return name
    }

    private fun readUrl(url: String): ByteArray = when {
        url.startsWith("assets://") ->
            context.assets.open(url.replace("assets://", "")).readBytes()

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
    @SuppressLint("StaticFieldLeak")
    @Volatile
    private var instance: ResourceHelper? = null
    fun initialize(context: Context) {
        this.instance = ResourceHelper(context)
    }

    actual fun getInstance(): ResourceHelper =
        this.instance ?: throw IllegalStateException("Resource helper was not initialized")
}