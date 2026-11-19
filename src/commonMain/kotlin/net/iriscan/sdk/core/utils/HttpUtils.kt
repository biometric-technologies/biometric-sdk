package net.iriscan.sdk.core.utils

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.isSuccess
import net.iriscan.sdk.io.exception.IOException

/**
 * @author Slava Gornostal
 *
 * Downloads [path] and returns the response body; `ResourceIO` uses it for `https:` models.
 *
 * Only a 2xx answer counts as a download. Ktor does not treat other status codes as errors
 * by default (`expectSuccess` is false); without the check, the body of an error answer, such
 * as a "404 Not Found" page, would come back as the file, and `ResourceIO` would cache it under
 * the model's name and load it on every start instead of downloading the model again.
 *
 * @throws IOException when the request fails (the original exception is the cause) or when
 * the server answers with a status other than 2xx
 */
internal suspend fun HttpClient.getBytes(path: String, onProgress: ((percent: Int) -> Unit)? = null): ByteArray {
    val (status, body) = try {
        val response = this.get(path) {
            val reportProgress = (0..100 step 10).toMutableList()
            onDownload { sent, total ->
                if (total == null || total <= 0L) return@onDownload
                val progress = (sent.toDouble() * 100 / total).toInt()
                if (reportProgress.contains(progress)) {
                    reportProgress.remove(progress)
                    onProgress?.invoke(progress)
                }
            }
        }
        response.status to response.body<ByteArray>()
    } catch (ex: Exception) {
        throw IOException("Could not download $path: ${ex.message ?: ex}", ex)
    }
    if (!status.isSuccess()) {
        throw IOException("Could not download $path: the server answered $status")
    }
    return body
}