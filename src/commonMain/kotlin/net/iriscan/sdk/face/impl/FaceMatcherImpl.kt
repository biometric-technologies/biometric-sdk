package net.iriscan.sdk.face.impl

import com.soywiz.kds.mapFloat
import net.iriscan.sdk.core.io.DataBytes
import net.iriscan.sdk.core.io.asByteArray
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * @author Slava Gornostal
 */
internal fun matchFaceNetTemplatesInternal(mask1: DataBytes, mask2: DataBytes): Float {
    val mask1f = toFloatArray(mask1.asByteArray())
    val mask2f = toFloatArray(mask2.asByteArray())
    val sum = mask1f
        .mapIndexed { index, vi -> (vi - mask2f[index]).pow(2) }
        .sum()
    return sqrt(sum)
}

private fun toFloatArray(bytes: ByteArray): FloatArray =
    bytes.toList()
        .chunked(4)
        .mapFloat {
            val asInt = (it[0].toInt() shl 24) or
                    (it[1].toInt() and 0xFF shl 16) or
                    (it[2].toInt() and 0xFF shl 8) or
                    (it[3].toInt() and 0xFF)
            Float.fromBits(asInt)
        }
        .toFloatArray()