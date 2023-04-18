package net.iriscan.sdk.face.impl

import io.github.aakira.napier.Napier
import kotlinx.cinterop.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.image.NativeImage
import net.iriscan.sdk.utils.imageToCGImage
import net.iriscan.sdk.utils.throwError
import platform.CoreGraphics.*
import platform.Vision.VNDetectFaceRectanglesRequest
import platform.Vision.VNFaceObservation
import platform.Vision.VNImageRequestHandler
import kotlin.math.min


/**
 * @author Slava Gornostal
 */
internal actual class FaceExtractorInternal actual constructor() {
    actual fun extract(image: Image): Image = memScoped {
        val cgImage = imageToCGImage(image)
        val imageWidth = CGImageGetWidth(cgImage.ptr).toInt()
        val imageHeight = CGImageGetHeight(cgImage.ptr).toInt()
        val face = extractInternal(cgImage.ptr) ?: return@memScoped image
        face.useContents {
            val x = origin.x.toInt()
            val y = origin.y.toInt()
            val width = size.width.toInt()
            val height = size.height.toInt()
            image[x until min((x + width), imageWidth), y until min((y + height), imageHeight)]
        }
    }

    actual fun extract(image: NativeImage): NativeImage {
        val face = extractInternal(image.ptr) ?: return image
        val cropped = CGImageCreateWithImageInRect(image.ptr, face)!!
        return cropped.pointed
    }

    private fun extractInternal(cgImage: CGImageRef): CValue<CGRect>? {
        val imageHeight = CGImageGetHeight(cgImage)
        val imageWidth = CGImageGetWidth(cgImage)
        return throwError { errorPtr ->
            val handler = VNImageRequestHandler(cgImage, mapOf<Any?, Any?>())
            val faceRect = CompletableDeferred<CValue<CGRect>?>()
            val request = VNDetectFaceRectanglesRequest { data, error ->
                if (error != null) {
                    Napier.e("Could not extract face from image: ${error.localizedDescription}")
                }
                if (error != null || data?.results.isNullOrEmpty()) {
                    faceRect.complete(null)
                    return@VNDetectFaceRectanglesRequest
                }
                val face = data!!.results!!.first() as VNFaceObservation
                val transform = CGAffineTransformTranslate(
                    CGAffineTransformMakeScale(1.0, -1.0),
                    0.0,
                    -imageHeight.toDouble()
                )
                val translate = CGAffineTransformScale(
                    CGAffineTransformIdentity.readValue(),
                    imageWidth.toDouble(),
                    imageHeight.toDouble()
                )
                val faceBox =
                    CGRectApplyAffineTransform(CGRectApplyAffineTransform(face.boundingBox, translate), transform)
                faceBox.useContents {
                    when {
                        (origin.x.toInt() in (0..imageWidth.toInt())) && (origin.y.toInt() in (0..imageHeight.toInt()))
                                && (size.width > 0) && (size.height > 0) -> faceRect.complete(faceBox)

                        else -> faceRect.complete(null)
                    }
                }
            }
            handler.performRequests(listOf(request), errorPtr)
            runBlocking { faceRect.await() }
        }
    }

}