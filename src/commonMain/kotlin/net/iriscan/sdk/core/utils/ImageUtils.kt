package net.iriscan.sdk.core.utils

import net.iriscan.sdk.core.image.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/**
 * @author Slava Gornostal
 */
internal fun grayscaleImg(image: Image) {
    if (image.colorType == ImageColorType.GRAY) {
        return
    }
    for (x in 0 until image.width) {
        for (y in 0 until image.height) {
            val (red, green, blue) = image[x, y]
            if (red == green && green == blue) {
                image[x, y] = red
            } else {
                image[x, y] = (red + green + blue) / 3
            }
        }
    }
    image.colorType = ImageColorType.GRAY
}

internal inline fun thresholdImg(image: Image, condition: (color: Int) -> Boolean): Image {
    require(image.colorType == ImageColorType.GRAY)
    val result = image.clone()
    result.colorType = ImageColorType.BINARY
    for (x in 0 until result.width) {
        for (y in 0 until result.height) {
            result[x, y] = when (condition(result[x, y])) {
                true -> 1
                false -> 0
            }
        }
    }
    return result
}

internal fun fillImg(image: Image, threshold: Int, kernel: Int, fillKernel: Int) {
    require(image.colorType == ImageColorType.GRAY)
    require(threshold in 0..255)
    val offset = kernel / 2
    val fillOffset = fillKernel / 2
    val thresholdMask = thresholdImg(image) { it > threshold }
    for (x in 0 until image.width) {
        for (y in 0 until image.height) {
            if (thresholdMask[x, y] == 0) continue
            val (kxmin, kxmax) = max(x - offset, 0) to min(x + offset, image.width - 1)
            val (kymin, kymax) = max(y - offset, 0) to min(y + offset, image.height - 1)
            val pixels = IntArray((kxmax - kxmin + 1) * (kymax - kymin + 1))
            var k = 0
            for (xk in kxmin..kxmax) {
                for (yk in kymin..kymax) {
                    pixels[k++] = image[xk, yk]
                }
            }
            val fillColor = pixels.average().toInt()
            val (jxmin, jxmax) = max(x - fillOffset, 0) to min(x + fillOffset, image.width - 1)
            val (jymin, jymax) = max(y - fillOffset, 0) to min(y + fillOffset, image.height - 1)
            for (xj in jxmin..jxmax) {
                for (yj in jymin..jymax) {
                    image[xj, yj] = fillColor
                }
            }
        }
    }
}

internal fun gaussianFilterImg(
    image: Image,
    mask: Array<IntArray> = arrayOf(intArrayOf(1, 2, 1), intArrayOf(2, 4, 2), intArrayOf(1, 2, 1)),
    divisor: Int = 16
) {
    require(image.colorType == ImageColorType.GRAY)
    for (x in 0 until image.width) {
        for (y in 0 until image.height) {
            val (xmin, xmax) = max(x - 1, 0) to min(x + 1, image.width - 1)
            val (ymin, ymax) = max(y - 1, 0) to min(y + 1, image.height - 1)
            val pixels = arrayOf(
                intArrayOf(image[xmin, ymin], image[x, ymin], image[xmax, ymin]),
                intArrayOf(image[xmin, y], image[x, y], image[xmax, y]),
                intArrayOf(image[xmin, ymax], image[x, ymax], image[xmax, ymax]),
            )
            image[x, y] = min((pixels * mask).sum() / divisor, 255)
        }
    }
}

internal fun normalizeHistogramImg(image: Image, mask: Image? = null) {
    require(image.colorType == ImageColorType.GRAY)
    val histogram = IntArray(256)
    val histogramSrc = mask ?: image
    for (i in 0 until histogramSrc.size) {
        histogram[histogramSrc[i]]++
    }
    val lookupTable = IntArray(256)
    val sum = histogram.sum().toDouble()
    var acc = 0
    for (i in 0..255) {
        acc += histogram[i]
        lookupTable[i] = (acc * 255 / sum).toInt()
    }
    for (x in 0 until image.width) {
        for (y in 0 until image.height) {
            image[x, y] = lookupTable[image[x, y]]
        }
    }
}

internal fun createImg(
    width: Int,
    height: Int,
    type: ImageColorType = ImageColorType.RGB,
    getColorAt: (x: Int, y: Int) -> Int
): Image =
    Image(
        width = width,
        height = height,
        colorType = type,
        colors = IntArray(width * height) { index -> getColorAt(index % width, index / width) }
    )

internal fun resizeImg(
    image: Image,
    newWidth: Int,
    newHeight: Int,
): Image {
    require(newWidth > 0 && newHeight > 0)
    val scaleX = newWidth / image.width.toDouble()
    val scaleY = newHeight / image.height.toDouble()
    return createImg(newWidth, newHeight, image.colorType) { x, y ->
        val ix = min(image.width - 1, round(x / scaleX).toInt())
        val iy = min(image.height - 1, round(y / scaleY).toInt())
        image[ix, iy]
    }
}