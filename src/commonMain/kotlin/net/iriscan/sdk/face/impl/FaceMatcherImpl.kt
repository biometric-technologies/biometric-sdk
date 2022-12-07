package net.iriscan.sdk.face.impl

import java.nio.ByteBuffer
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * @author Slava Gornostal
 */
internal fun matchFaceNetTemplatesInternal(mask1: ByteArray, mask2: ByteArray): Float {
    val mask1f = toFloatArray(mask1)
    val mask2f = toFloatArray(mask2)
    val sum = mask1f
        .mapIndexed { index, vi -> (vi - mask2f[index]).pow(2) }
        .sum()
    return sqrt(sum)
}

private fun toFloatArray(bytes: ByteArray): FloatArray {
    val floats = FloatArray(bytes.size / 4)
    ByteBuffer.wrap(bytes).asFloatBuffer()[floats, 0, bytes.size / 4]
    return floats
}