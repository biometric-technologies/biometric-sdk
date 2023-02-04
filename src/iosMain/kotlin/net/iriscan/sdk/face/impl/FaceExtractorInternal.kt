package net.iriscan.sdk.face.impl

import kotlinx.cinterop.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import net.iriscan.sdk.core.exception.BiometricNotFoundException
import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.utils.imageToCGImage
import net.iriscan.sdk.utils.throwError
import platform.CoreGraphics.*
import platform.Vision.VNDetectFaceRectanglesRequest
import platform.Vision.VNFaceObservation
import platform.Vision.VNImageRequestHandler

/**
 * @author Slava Gornostal
 */
internal actual class FaceExtractorInternal actual constructor() {
    actual fun extract(image: Image): Image = autoreleasepool {
        val cgImage = imageToCGImage(image)
        val (x, y, width, height) = throwError { errorPtr ->
            val handler = VNImageRequestHandler(cgImage.ptr, mapOf<Any?, Any?>())
            val faceRect = CompletableDeferred<CValue<CGRect>>()
            val request = VNDetectFaceRectanglesRequest { data, error ->
                if (error != null || data?.results.isNullOrEmpty()) {
                    faceRect.completeExceptionally(BiometricNotFoundException("Face was not found on the image"))
                    return@VNDetectFaceRectanglesRequest
                }
                val face = data!!.results!!.first() as VNFaceObservation
                val transform = CGAffineTransformTranslate(
                    CGAffineTransformMakeScale(1.0, -1.0),
                    0.0,
                    -image.height.toDouble()
                )
                val translate = CGAffineTransformScale(
                    CGAffineTransformIdentity.readValue(),
                    image.width.toDouble(),
                    image.height.toDouble()
                )
                val faceBox =
                    CGRectApplyAffineTransform(CGRectApplyAffineTransform(face.boundingBox, translate), transform)
                faceRect.complete(faceBox)
            }
            handler.performRequests(listOf(request), errorPtr)
            runBlocking {
                faceRect.await().useContents {
                    arrayOf(
                        origin.x.toInt(),
                        origin.y.toInt(),
                        size.width.toInt(),
                        size.height.toInt()
                    )
                }
            }
        }
        image[x..x + width, y..y + height]
    }
}