package net.iriscan.sdk.iris.impl

import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.image.ImageColorType
import net.iriscan.sdk.core.utils.createImg
import net.iriscan.sdk.core.utils.normalizeHistogramImg
import net.iriscan.sdk.iris.IrisEncodeProperties

/**
 * @author Slava Gornostal
 */
internal fun encodeInternal(texture: Image, props: IrisEncodeProperties): ByteArray {
    require(texture.colorType == ImageColorType.GRAY)
    var image = texture.clone()
    normalizeHistogramImg(image)
    image = extractNBP(texture)
    val templateWidth = props.templateWidth
    val templateHeight = props.templateHeight
    val blockWidth = image.width / templateWidth
    val blockHeight = image.height / templateHeight
    val means = createImg(templateWidth, templateHeight, type = ImageColorType.GRAY) { x, y ->
        image[x * blockWidth..x * blockWidth + x, y * blockHeight..y * blockHeight + y].colors.average().toInt()
    }
    return means.colors
        .toList()
        .chunked(templateWidth)
        .flatMapIndexed { index: Int, row: List<Int> ->
            when (index % 2 == 0) {
                true -> row
                false -> row.reversed()
            }
        }
        .windowed(2) {
            when (it[1] > it[0]) {
                true -> 1.toByte()
                false -> 0.toByte()
            }
        }
        .toByteArray()
}

private fun extractNBP(texture: Image): Image {
    val result = texture.clone()
    for (x in 1 until texture.width - 1) {
        for (y in 1 until texture.height - 1) {
            val bytesList = listOf(
                texture[x - 1, y - 1],
                texture[x, y - 1],
                texture[x + 1, y - 1],
                texture[x + 1, y],
                texture[x + 1, y + 1],
                texture[x, y + 1],
                texture[x - 1, y + 1],
                texture[x - 1, y],
            )
            val bytes = bytesList.windowed(2) {
                when (it[1] > it[0]) {
                    true -> "1"
                    false -> "0"
                }
            }
                .joinToString("")
                .reversed()
            result[x, y] = bytes.toInt(2)
        }
    }
    return result
}