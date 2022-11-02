package net.iriscan.sdk.core.image

/**
 * @author Slava Gornostal
 *
 * Image data
 * @see ImageType
 * @see Color
 */
class Image(val width: Int, val height: Int, var type: ImageType, val colors: IntArray) {

    val size = colors.size

    operator fun get(x: Int, y: Int): Int = this.colors[y * width + x]

    operator fun get(index: Int): Int = this.colors[index]

    operator fun get(xRange: IntRange, yRange: IntRange): Image {
        val newWidth = xRange.count()
        val newHeight = yRange.count()
        val colors = IntArray(newWidth * newHeight)
        var index = 0
        for (y in yRange) {
            for (x in xRange) {
                colors[index++] = this[x, y]
            }
        }
        return Image(newWidth, newHeight, type, colors)
    }

    operator fun contains(point: Point): Boolean =
        point.y * width + point.x < colors.size

    operator fun set(x: Int, y: Int, color: Int) {
        this.colors[y * width + x] = color
    }

    operator fun set(index: Int, color: Int) {
        this.colors[index] = color
    }

    fun clone(): Image = Image(width, height, type, IntArray(colors.size) { colors[it] })
}