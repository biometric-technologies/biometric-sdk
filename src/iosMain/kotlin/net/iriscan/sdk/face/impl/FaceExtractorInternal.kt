package net.iriscan.sdk.face.impl

import cnames.structs.CGImage
import io.github.aakira.napier.Napier
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.image.NativeImage
import net.iriscan.sdk.utils.cgImageToImage
import net.iriscan.sdk.utils.imageToCGImage
import net.iriscan.sdk.utils.throwError
import platform.CoreGraphics.*
import platform.UIKit.UIGraphicsBeginImageContext
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetCurrentContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.Vision.VNDetectFaceLandmarksRequest
import platform.Vision.VNFaceObservation
import platform.Vision.VNImageRequestHandler
import kotlin.math.atan2


/**
 * @author Slava Gornostal
 */
internal actual class FaceExtractorInternal actual constructor() {
    actual fun extract(image: Image): Image? {
        val cgImage = imageToCGImage(image)
        val face = extractInternal(cgImage.ptr) ?: return null
        return cgImageToImage(face)
    }

    actual fun extract(image: NativeImage): NativeImage? = extractInternal(image.ptr)

    private fun extractInternal(cgImage: CGImageRef): CGImage? {
        val face = throwError { errorPtr ->
            val handler = VNImageRequestHandler(cgImage, mapOf<Any?, Any?>())
            val faceRect = CompletableDeferred<VNFaceObservation?>()
            val request = VNDetectFaceLandmarksRequest { data, error ->
                if (error != null) {
                    Napier.e("Could not extract face from image: ${error.localizedDescription}")
                }
                if (error != null) {
                    faceRect.complete(null)
                    return@VNDetectFaceLandmarksRequest
                }
                faceRect.complete(data?.results?.firstOrNull() as? VNFaceObservation)
            }
            handler.performRequests(listOf(request), errorPtr)
            runBlocking { faceRect.await() }
        } ?: return null
        val leftEye = face.landmarks?.leftEye?.normalizedPoints?.pointed
        val rightEye = face.landmarks?.rightEye?.normalizedPoints?.pointed
        if (leftEye == null || rightEye == null) {
            return null
        }
        val angle = atan2(rightEye.y - leftEye.y, rightEye.x - leftEye.x)
        val imageHeight = CGImageGetHeight(cgImage)
        val imageWidth = CGImageGetWidth(cgImage)
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
        val faceBoxNormalized = faceBox.useContents {
            when {
                (origin.x.toInt() in (0..imageWidth.toInt())) && (origin.y.toInt() in (0..imageHeight.toInt()))
                        && (size.width > 0) && (size.height > 0) -> faceBox

                else -> null
            }
        } ?: return null
        val cropped = CGImageCreateWithImageInRect(cgImage, faceBoxNormalized)!!
        val rotated = rotate(cropped, -angle)
        return rotated.pointed
    }

    private fun rotate(cgImage: CGImageRef, angle: CGFloat): CGImageRef {
        val width = CGImageGetWidth(cgImage).toDouble()
        val height = CGImageGetHeight(cgImage).toDouble()
        UIGraphicsBeginImageContext(CGSizeMake(width, height))
        val context = UIGraphicsGetCurrentContext()
        CGContextTranslateCTM(context, width * .5, height * .5)
        CGContextScaleCTM(context, 1.0, -1.0)
        CGContextRotateCTM(context, angle)
        val rect = CGRectMake(-width * .5, -height * .5, width, height)
        CGContextDrawImage(context, rect, cgImage)
        val img = UIGraphicsGetImageFromCurrentImageContext()!!.CGImage!!
        UIGraphicsEndImageContext()
        return img
    }
}