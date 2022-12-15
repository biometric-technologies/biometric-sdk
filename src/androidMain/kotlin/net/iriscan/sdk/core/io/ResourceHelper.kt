package net.iriscan.sdk.core.io

import android.content.Context
import com.soywiz.korio.net.http.HttpClient
import kotlinx.coroutines.runBlocking
import net.iriscan.sdk.SdkNotInitializedException
import java.io.FileNotFoundException

/**
 * @author Slava Gornostal
 */
actual class ResourceHelper(private val context: Context) {
    private val client = HttpClient()

    actual fun getCached(name: String, url: String): ByteArray =
        when {
            url.startsWith("resources://") ->
                context.assets.open(url.replace("resources://", "")).readBytes()

            url.startsWith("https://") -> runBlocking { getCachedOrDownload(name, url) }
            else -> throw IllegalArgumentException("Illegal URL: $url")
        }

    private suspend fun getCachedOrDownload(name: String, url: String): ByteArray =
        try {
            context.openFileInput(name).readBytes()
        } catch (nfo: FileNotFoundException) {
            val modelBytes = client.readBytes(url)
            context.openFileOutput(name, Context.MODE_PRIVATE).use {
                it.write(modelBytes)
            }
            modelBytes
        } catch (th: Throwable) {
            throw SdkNotInitializedException("Could not download resource with url: ${url}, error: ${th.message}")
        }


}