package net.iriscan.sdk.core.image

import java.awt.image.BufferedImage

/**
 * @author Slava Gornostal
 */
internal fun internalResizeNativeImage(image: NativeImage, width: Int, height: Int): NativeImage {
    val resizedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    var graphics2D = resizedImage.createGraphics()
    graphics2D.drawImage(image, 0, 0, width, height, null)
    graphics2D.dispose()
    val tmp = image.getScaledInstance(width, height, java.awt.Image.SCALE_AREA_AVERAGING)
    graphics2D = resizedImage.createGraphics()
    graphics2D.drawImage(tmp, 0, 0, null)
    graphics2D.dispose()
    return resizedImage
}