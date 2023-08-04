package net.iriscan.sdk.core.utils

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import net.iriscan.sdk.io.exception.IOException

/**
 * @author Slava Gornostal
 */
internal suspend fun HttpClient.getBytes(path: String, onProgress: ((percent: Int) -> Unit)? = null): ByteArray =
    try {
        this.get(path) {
            val reportProgress = (0..100 step 10).toMutableList()
            onDownload { sent, total ->
                val progress = (sent.toDouble() * 100 / total).toInt()
                if (reportProgress.contains(progress)) {
                    reportProgress.remove(progress)
                    onProgress?.invoke(progress)
                }
            }
        }
            .readBytes()
    } catch (ex: Exception) {
        throw IOException(ex.message ?: "Unexpected http error", ex.cause)
    }