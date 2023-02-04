package net.iriscan.sdk.io

import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.utils.cgImageToImage
import net.iriscan.sdk.utils.imageToCGImage
import net.iriscan.sdk.utils.toByteArray
import net.iriscan.sdk.utils.toNSData
import platform.CoreGraphics.CGImageRef
import platform.Foundation.NSData

/**
 * @author Slava Gornostal
 */
actual interface InputOutputImageConvertOperations {
    fun convert(image: CGImageRef): Image
    fun convertToCGImage(image: Image): CGImageRef

    fun nsDataToByteArray(data: NSData): ByteArray
    fun byteArrayToNsData(data: ByteArray): NSData
}

internal actual class InputOutputImageConvertOperationsImpl actual constructor() : InputOutputImageConvertOperations {
    override fun convert(image: CGImageRef): Image = cgImageToImage(image.pointed)
    override fun convertToCGImage(image: Image): CGImageRef = imageToCGImage(image).ptr
    override fun nsDataToByteArray(data: NSData): ByteArray = data.toByteArray()
    override fun byteArrayToNsData(data: ByteArray): NSData = data.toNSData()

}