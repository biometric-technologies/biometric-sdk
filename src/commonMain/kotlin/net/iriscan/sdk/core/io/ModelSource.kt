package net.iriscan.sdk.core.io

import kotlin.jvm.JvmOverloads

/**
 * Where an operation takes its TensorFlow Lite model from: the model built into the SDK, or the
 * app's own model, shipped inside the app, stored on the device or downloaded once.
 *
 * One type for every operation that runs a model (face detection on the JVM, face encoding and
 * photo liveness now, iris segmentation and fingerprint minutiae later), so that an app chooses and
 * checks a model the same way everywhere. No configuration takes it yet: they still take a `path`
 * with a location prefix and three checksum parameters (README, "Model loading and caching").
 * This type is the API to agree on before they change.
 *
 * A checksum is the SHA-256 of the model file as 64 hexadecimal digits, as `sha256sum` prints it,
 * in either case. A [Download] needs one, because it is the only source the app does not hold
 * itself; the other sources may have one, to catch a wrong or damaged file. CRC-32, which the
 * older configuration also accepts, is not taken: it catches transmission errors, not a file
 * that was replaced.
 *
 * Each source checks its values when it is created, so that a wrong URL or checksum fails where
 * the configuration is written rather than at the first download. The constructors declare the
 * exception, so that Swift gets throwing initializers instead of a crash.
 */
sealed class ModelSource {

    /**
     * The model the SDK ships for the operation. Only small models are built in, a few megabytes
     * at most; larger ones are downloaded. An operation without a built-in model rejects this
     * source when the SDK is configured. No checksum: the model comes with the SDK.
     */
    data object BuiltIn : ModelSource()

    /**
     * A model shipped inside the app, by its path from the root of the app's resources, such as
     * `models/facenet.tflite`: a classpath resource on the JVM, an asset on Android, a resource of
     * the main bundle on iOS. One name for every platform lets shared Kotlin code configure the
     * model once.
     */
    data class AppResource @JvmOverloads @Throws(IllegalArgumentException::class) constructor(
        val name: String,
        val sha256: String? = null,
    ) : ModelSource() {
        init {
            require(name.isNotBlank()) { "The name of a model resource is blank" }
            // only a JVM class loader would accept it; Android assets and iOS bundles would not
            require(!name.startsWith("/")) {
                "Model resource $name: give its path from the root of the app's resources, without a leading /"
            }
            requireSha256OrNull(sha256)
        }
    }

    /**
     * A model file on the device, by its absolute path. On Android too: the older `file:` prefix
     * takes a bare name in the app's files directory there, and a path on the other platforms.
     */
    data class File @JvmOverloads @Throws(IllegalArgumentException::class) constructor(
        val path: String,
        val sha256: String? = null,
    ) : ModelSource() {
        init {
            require(path.isNotBlank()) { "The path of a model file is blank" }
            requireSha256OrNull(sha256)
        }
    }

    /**
     * A model downloaded from an `https://` URL at the first initialization and kept in the app's
     * cache directory under its checksum. Later initializations load it from the cache without
     * contacting the server. A cached file with another checksum (damaged) is downloaded again,
     * and a download with another checksum fails the initialization with an IOException. Because
     * the cache file is named by the checksum, a new model gets a new file, where the fixed cache
     * names of the older configuration keep serving the old model after the URL has changed.
     */
    data class Download @Throws(IllegalArgumentException::class) constructor(
        val url: String,
        val sha256: String,
    ) : ModelSource() {
        init {
            // a model is code the app runs, so no plain http; the checksum guards the content too
            require(url.startsWith(HTTPS_PREFIX) && url.length > HTTPS_PREFIX.length) {
                "Model URL $url: models are downloaded from https:// URLs only"
            }
            requireSha256OrNull(sha256)
        }
    }
}

/** How every download URL starts. Lower case, as `ResourceIO` matches it. */
private const val HTTPS_PREFIX = "https://"

/** A SHA-256 is 32 bytes, written with two hexadecimal digits per byte. */
private const val SHA256_HEX_DIGITS = 64

private fun requireSha256OrNull(checksum: String?) {
    if (checksum == null) return
    require(checksum.length == SHA256_HEX_DIGITS && checksum.all { it.isHexDigit() }) {
        "Model checksum \"$checksum\" is not a SHA-256: " +
            "expected $SHA256_HEX_DIGITS hexadecimal digits, as sha256sum prints them"
    }
}

private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
