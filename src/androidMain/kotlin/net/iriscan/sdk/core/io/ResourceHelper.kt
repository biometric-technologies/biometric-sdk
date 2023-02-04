package net.iriscan.sdk.core.io

import android.annotation.SuppressLint
import android.content.Context
import com.soywiz.korio.net.http.HttpClient
import kotlinx.coroutines.runBlocking
import java.io.FileNotFoundException

/**
 * @author Slava Gornostal
 */
actual class ResourceHelper(private val context: Context) {
    private val client = HttpClient()
    actual fun read(url: String): ByteArray = readUrl(url)

    actual fun cacheAndRead(name: String, url: String): ByteArray {
        if (url.startsWith("assets://")) {
            return readUrl(url)
        }
        return try {
            context.openFileInput(name).readBytes()
        } catch (e: FileNotFoundException) {
            val modelBytes = readUrl(url)
            context.openFileOutput(name, Context.MODE_PRIVATE).use {
                it.write(modelBytes)
            }
            modelBytes
        }
    }

    actual fun cacheAndGetPath(name: String, url: String): String {
        if (url.startsWith("assets://")) {
            return url.replace("assets://", "")
        }
        if (context.fileList().contains(name)) {
            return name
        }
        val modelBytes = runBlocking { client.readBytes(url) }
        context.openFileOutput(name, Context.MODE_PRIVATE).use {
            it.write(modelBytes)
        }
        return name
    }

    private fun readUrl(url: String): ByteArray = when {
        url.startsWith("assets://") ->
            context.assets.open(url.replace("assets://", "")).readBytes()

        url.startsWith("https://") -> runBlocking { client.readBytes(url) }
        else -> throw IllegalArgumentException("Illegal URL: $url")
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