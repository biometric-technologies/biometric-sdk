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

/**
 * Distance between two templates when one of them has no direction to compare: it is empty, all
 * its values are zero, or one of its values is not a finite number (NaN or an infinity). Such
 * templates come from damaged or wrongly decoded bytes (for example float32 values written in the
 * other byte order), not from a face. The value lies above every distance between two embeddings
 * (at most 2), so such a template matches no face at any finite threshold, and the closest of
 * several templates is never one of them. It is returned rather than thrown so that one damaged
 * template does not abort a comparison with many, and because on iOS an exception from a call
 * without `@Throws` terminates the app.
 */
internal const val NOT_COMPARABLE_DISTANCE = Float.POSITIVE_INFINITY

internal fun embeddingDistance(a: FloatArray, b: FloatArray): Float {
    val na = euclideanLength(a)
    val nb = euclideanLength(b)
    // Without this check such an embedding would give NaN (0 / 0, or infinity / infinity): NaN
    // matches nothing, but it also makes the minimum over several templates NaN, so that none of
    // them matches.
    if (!hasDirection(na) || !hasDirection(nb)) return NOT_COMPARABLE_DISTANCE
    var sum = 0.0
    for (i in a.indices) {
        val d = a[i] / na - b[i] / nb
        sum += d * d
    }
    return sqrt(sum).toFloat()
}

/**
 * Euclidean length of [v]. The squares are taken in Double: in Float a value above about 1.8e19
 * squares to infinity and values below about 1e-22 square to zero, which would give such an
 * embedding a length of infinity or 0 although it has a direction.
 */
private fun euclideanLength(v: FloatArray): Double {
    var sum = 0.0
    for (x in v) sum += x.toDouble() * x
    return sqrt(sum)
}

/**
 * False for a length of 0 (no values, or all of them zero) and for one that is not finite (a NaN
 * or infinite value): such an embedding has no direction.
 */
private fun hasDirection(length: Double): Boolean = length > 0.0 && length.isFinite()

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
