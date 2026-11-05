package net.iriscan.sdk.core.utils

import com.sun.net.httpserver.HttpServer
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import kotlinx.coroutines.runBlocking
import net.iriscan.sdk.io.exception.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * [getBytes] is how `ResourceIO` downloads `https:` models, and whatever it returns is cached under
 * the model's name and loaded on every start, so an error answer must never come back as the file.
 * The tests run the OkHttp engine of the JVM build against a local server and need no network.
 */
class HttpUtilsTest {

    private val server = HttpServer.create(InetSocketAddress(LOOPBACK, 0), 0).apply { start() }

    /** Configured like the client of the JVM `ResourceIO`. */
    private val client = HttpClient(OkHttp) {
        engine {
            config {
                followRedirects(true)
            }
        }
    }

    private val model = ByteArray(300_000) { (it * 31 + it / 1000).toByte() }

    @AfterTest
    fun stop() {
        client.close()
        server.stop(0)
    }

    /** Answers every request for [path] with [status], [headers] and [body]. */
    private fun serve(path: String, status: Int, body: ByteArray = ByteArray(0), headers: Map<String, String> = emptyMap()) {
        server.createContext(path) { exchange ->
            headers.forEach { (name, value) -> exchange.responseHeaders.add(name, value) }
            // -1 announces an empty body; 0 would announce a chunked body of unknown length
            exchange.sendResponseHeaders(status, if (body.isEmpty()) -1 else body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
    }

    private fun url(path: String) = "http://$LOOPBACK:${server.address.port}$path"

    private fun download(url: String): ByteArray = runBlocking { client.getBytes(url) }

    @Test
    fun returnsTheBodyOfASuccessfulAnswer() {
        serve("/facenet.tflite", 200, model)
        assertContentEquals(model, download(url("/facenet.tflite")))
    }

    @Test
    fun followsARedirectToTheFile() {
        serve("/facenet.tflite", 200, model)
        serve("/latest/facenet.tflite", 302, headers = mapOf("Location" to url("/facenet.tflite")))
        assertContentEquals(model, download(url("/latest/facenet.tflite")))
    }

    @Test
    fun errorPageIsNotReturnedAsTheFile() {
        serve("/facenet.tflite", 404, "<html><body><h1>Not Found</h1></body></html>".encodeToByteArray())
        val error = assertFailsWith<IOException> { download(url("/facenet.tflite")) }
        assertTrue("404" in error.message.orEmpty(), error.message)
    }

    @Test
    fun serverErrorIsNotReturnedAsTheFile() {
        serve("/facenet.tflite", 503, "Service Unavailable".encodeToByteArray())
        val error = assertFailsWith<IOException> { download(url("/facenet.tflite")) }
        assertTrue("503" in error.message.orEmpty(), error.message)
    }

    @Test
    fun failedConnectionIsAnIOExceptionWithTheUrlAndTheCause() {
        // a port that was free a moment ago: nothing listens on it, so the connection is refused
        val closedPort = ServerSocket(0, 1, InetAddress.getByName(LOOPBACK)).use { it.localPort }
        val modelUrl = "http://$LOOPBACK:$closedPort/facenet.tflite"
        val error = assertFailsWith<IOException> { download(modelUrl) }
        assertTrue(modelUrl in error.message.orEmpty(), error.message)
        assertNotNull(error.cause)
    }

    private companion object {
        /** An IPv4 literal, so the URLs need no brackets and no name lookup. */
        const val LOOPBACK = "127.0.0.1"
    }
}
