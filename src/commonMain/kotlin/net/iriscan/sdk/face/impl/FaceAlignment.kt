package net.iriscan.sdk.face.impl

import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.image.ImageColorType
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/** Margin around the detected face box, as a share of its size (FaceNet was trained on loose crops). */
internal const val FACE_CROP_MARGIN = 0.2

/** Eye-line tilt beyond which the eye detection is treated as unreliable and ignored. */
internal const val MAX_EYE_TILT_DEG = 30.0

/**
 * In-plane rotation of the face in degrees from the centers of the eye on the
 * image left and the eye on the image right, or null when it is implausible.
 */
internal fun eyeLineAngle(leftX: Double, leftY: Double, rightX: Double, rightY: Double): Double? {
    if (rightX <= leftX) return null
    val deg = atan2(rightY - leftY, rightX - leftX) * 180.0 / PI
    return if (kotlin.math.abs(deg) <= MAX_EYE_TILT_DEG) deg else null
}

/**
 * Square face crop of [outSize] pixels centered at ([cx], [cy]) covering [side]
 * source pixels, rotated by [angleDeg] so that the eye line becomes horizontal.
 * Bilinear sampling; pixels outside the image repeat the border.
 */
internal fun cropFace(image: Image, cx: Double, cy: Double, side: Double, angleDeg: Double, outSize: Int): Image {
    require(outSize > 0 && side > 0)
    val a = angleDeg * PI / 180.0
    val ca = cos(a)
    val sa = sin(a)
    val step = side / outSize
    val out = IntArray(outSize * outSize)
    for (v in 0 until outSize) for (u in 0 until outSize) {
        val dx = (u + 0.5 - outSize / 2.0) * step
        val dy = (v + 0.5 - outSize / 2.0) * step
        val sx = cx + dx * ca - dy * sa
        val sy = cy + dx * sa + dy * ca
        out[v * outSize + u] = sampleRgb(image, sx, sy)
    }
    return Image(outSize, outSize, ImageColorType.RGB, out)
}

/** Crop for a detected face box, with [FACE_CROP_MARGIN] around it. */
internal fun cropFaceBox(image: Image, x: Int, y: Int, w: Int, h: Int, angleDeg: Double, outSize: Int): Image =
    cropFace(image, x + w / 2.0, y + h / 2.0, max(w, h) * (1 + FACE_CROP_MARGIN), angleDeg, outSize)

private fun rgbAt(image: Image, x: Int, y: Int): Int {
    val c = image[x.coerceIn(0, image.width - 1), y.coerceIn(0, image.height - 1)]
    return when (image.colorType) {
        ImageColorType.RGB -> c and 0xFFFFFF
        ImageColorType.GRAY -> {
            val g = c.coerceIn(0, 255); (g shl 16) or (g shl 8) or g
        }
        ImageColorType.BINARY -> if (c != 0) 0xFFFFFF else 0
    }
}

private fun sampleRgb(image: Image, x: Double, y: Double): Int {
    // centers of pixels are at integer coordinates
    val x0 = floor(x).toInt()
    val y0 = floor(y).toInt()
    val fx = x - x0
    val fy = y - y0
    val c00 = rgbAt(image, x0, y0)
    val c10 = rgbAt(image, x0 + 1, y0)
    val c01 = rgbAt(image, x0, y0 + 1)
    val c11 = rgbAt(image, x0 + 1, y0 + 1)
    var result = 0
    for (shift in intArrayOf(16, 8, 0)) {
        val v = ((c00 shr shift) and 0xFF) * (1 - fx) * (1 - fy) +
                ((c10 shr shift) and 0xFF) * fx * (1 - fy) +
                ((c01 shr shift) and 0xFF) * (1 - fx) * fy +
                ((c11 shr shift) and 0xFF) * fx * fy
        result = result or (v.roundToInt().coerceIn(0, 255) shl shift)
    }
    return result
}
