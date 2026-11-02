package net.iriscan.sdk.face.impl

import io.github.aakira.napier.Napier
import net.iriscan.sdk.core.io.DataBytes
import net.iriscan.sdk.core.io.asByteArray
import kotlin.math.sqrt

/**
 * @author Slava Gornostal
 *
 * Euclidean distance between L2-normalized face embeddings (0 = identical,
 * sqrt(2) = orthogonal, 2 = opposite). Embeddings are normalized here as well,
 * so models without an L2 normalization layer give comparable distances.
 */
internal fun matchFaceNetTemplatesInternal(mask1: DataBytes, mask2: DataBytes, traceId: String? = null): Float {
    val a = toFloatArray(mask1.asByteArray())
    val b = toFloatArray(mask2.asByteArray())
    require(a.size == b.size) { "Face templates have different sizes: ${a.size} and ${b.size}" }
    val result = embeddingDistance(a, b)
    Napier.d(tag = traceId) { "Resolved face distance $result" }
    return result
}

internal fun embeddingDistance(a: FloatArray, b: FloatArray): Float {
    var na = 0.0
    var nb = 0.0
    for (i in a.indices) {
        na += a[i] * a[i]; nb += b[i] * b[i]
    }
    na = sqrt(na).coerceAtLeast(1e-12)
    nb = sqrt(nb).coerceAtLeast(1e-12)
    var sum = 0.0
    for (i in a.indices) {
        val d = a[i] / na - b[i] / nb
        sum += d * d
    }
    return sqrt(sum).toFloat()
}

/** Big-endian float32 array, as written by the encoders. */
internal fun toFloatArray(bytes: ByteArray): FloatArray {
    require(bytes.size % 4 == 0) { "Face template size must be a multiple of 4" }
    return FloatArray(bytes.size / 4) { k ->
        val i = k * 4
        Float.fromBits(
            (bytes[i].toInt() shl 24) or
                    (bytes[i + 1].toInt() and 0xFF shl 16) or
                    (bytes[i + 2].toInt() and 0xFF shl 8) or
                    (bytes[i + 3].toInt() and 0xFF)
        )
    }
}
