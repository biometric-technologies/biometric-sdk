package net.iriscan.sdk.core.io

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * What a model source accepts when it is created: a wrong URL or checksum has to fail where the
 * configuration is written, with a message that says what is wrong, not at the first download.
 */
class ModelSourceTest {

    /** SHA-256 of "abc" (FIPS 180-4 example), as sha256sum prints it. */
    private val sha256OfAbc = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

    private val url = "https://models.example.com/facenet.tflite"

    @Test
    fun aDownloadTakesAnHttpsUrlAndTheSha256OfTheFile() {
        val download = ModelSource.Download(url, sha256OfAbc)
        assertEquals(url, download.url)
        assertEquals(sha256OfAbc, download.sha256)
        // the text the SDK itself computes for a downloaded file (ResourceIO.calculateHash)
        ModelSource.Download(url, calculateHash("abc".encodeToByteArray(), HashMethod.SHA256))
        // PowerShell's Get-FileHash prints upper-case digits
        ModelSource.Download(url, sha256OfAbc.uppercase())
    }

    @Test
    fun aDownloadFromAnythingButAnHttpsUrlIsRejected() {
        val notHttps = listOf(
            "http://models.example.com/facenet.tflite",
            "ftp://models.example.com/facenet.tflite",
            "models.example.com/facenet.tflite",
            "file:/opt/models/facenet.tflite",
            "HTTPS://models.example.com/facenet.tflite",
            "https://",
            "",
        )
        for (wrong in notHttps) {
            assertFailsWith<IllegalArgumentException>(wrong) { ModelSource.Download(wrong, sha256OfAbc) }
        }
    }

    @Test
    fun aChecksumThatIsNotASha256IsRejectedByEverySourceThatTakesOne() {
        val notSha256 = listOf(
            "",
            sha256OfAbc.dropLast(1),
            sha256OfAbc + "0",
            "g" + sha256OfAbc.drop(1),
            " $sha256OfAbc",
            // a CRC-32 in the text the older configuration accepts with HashMethod.CRC32
            calculateHash("abc".encodeToByteArray(), HashMethod.CRC32),
        )
        for (wrong in notSha256) {
            assertFailsWith<IllegalArgumentException>(wrong) { ModelSource.Download(url, wrong) }
            assertFailsWith<IllegalArgumentException>(wrong) { ModelSource.File("/opt/models/facenet.tflite", wrong) }
            assertFailsWith<IllegalArgumentException>(wrong) { ModelSource.AppResource("models/facenet.tflite", wrong) }
        }
    }

    @Test
    fun filesAndAppResourcesNeedANameAndMayHaveAChecksum() {
        assertNull(ModelSource.File("/opt/models/facenet.tflite").sha256)
        assertNull(ModelSource.AppResource("models/facenet.tflite").sha256)
        assertEquals(sha256OfAbc, ModelSource.File("/opt/models/facenet.tflite", sha256OfAbc).sha256)
        assertEquals(sha256OfAbc, ModelSource.AppResource("models/facenet.tflite", sha256OfAbc).sha256)
        for (blank in listOf("", "  ")) {
            assertFailsWith<IllegalArgumentException> { ModelSource.File(blank) }
            assertFailsWith<IllegalArgumentException> { ModelSource.AppResource(blank) }
        }
        // a JVM class loader would find it, an Android asset manager or an iOS bundle would not
        assertFailsWith<IllegalArgumentException> { ModelSource.AppResource("/models/facenet.tflite") }
    }

    @Test
    fun theErrorSaysWhatIsWrong() {
        val httpUrl = "http://models.example.com/facenet.tflite"
        val wrongUrl = assertFailsWith<IllegalArgumentException> { ModelSource.Download(httpUrl, sha256OfAbc) }
        assertEquals("Model URL $httpUrl: models are downloaded from https:// URLs only", wrongUrl.message)
        val wrongChecksum = assertFailsWith<IllegalArgumentException> { ModelSource.Download(url, "352441c2") }
        assertEquals(
            "Model checksum \"352441c2\" is not a SHA-256: " +
                "expected 64 hexadecimal digits, as sha256sum prints them",
            wrongChecksum.message,
        )
    }
}
