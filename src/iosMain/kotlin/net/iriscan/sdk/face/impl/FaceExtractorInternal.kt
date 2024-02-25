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
import net.iriscan.sdk.core.utils.Math
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
import kotlin.math.abs
import kotlin.math.atan2


/**
 * @author Slava Gornostal
 */
internal actual class FaceExtractorInternal actual constructor() {
    actual fun extract(image: Image, rotateOnWrongOrientation: Boolean, traceId: String?): Image? {
        val cgImage = imageToCGImage(image)
        val face = extractInternal(cgImage.ptr, rotateOnWrongOrientation, traceId) ?: return null
        return cgImageToImage(face)
    }

    actual fun extract(image: NativeImage, rotateOnWrongOrientation: Boolean, traceId: String?): NativeImage? =
        extractInternal(image.ptr, rotateOnWrongOrientation, traceId)

    private fun extractInternal(cgImage: CGImageRef, rotateOnWrongOrientation: Boolean, traceId: String?): CGImage? {
        Napier.d(tag = traceId) {
            "Extracting face from SDK image [${CGImageGetWidth(cgImage)},${
                CGImageGetHeight(
                    cgImage
                )
            }]"
        }
        val face = throwError { errorPtr ->
            val handler = VNImageRequestHandler(cgImage, mapOf<Any?, Any?>())
            val faceRect = CompletableDeferred<VNFaceObservation?>()
            val request = VNDetectFaceLandmarksRequest { data, error ->
                if (error != null) {
                    Napier.e(tag = traceId) { "Could not extract face from image: ${error.localizedDescription}" }
                    faceRect.complete(null)
                    return@VNDetectFaceLandmarksRequest
                }
                faceRect.complete(data?.results?.firstOrNull() as? VNFaceObservation)
            }
            handler.performRequests(listOf(request), errorPtr)
            runBlocking { faceRect.await() }
        }
        if (face == null) {
            Napier.w(tag = traceId) { "No biometrics were found on provided image" }
            return null
        }
        val leftEye = face.landmarks?.leftEye?.normalizedPoints?.pointed
        val rightEye = face.landmarks?.rightEye?.normalizedPoints?.pointed
        if (leftEye == null || rightEye == null) {
            Napier.w(tag = traceId) { "Necessary eye(s) [${leftEye}, ${rightEye}] landmarks were not found" }
            return null
        }
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
        }
        if (faceBoxNormalized == null) {
            Napier.w(tag = traceId) { "Face normalization failed" }
            return null
        }
        val faceImage = CGImageCreateWithImageInRect(cgImage, faceBoxNormalized)!!
        val resultImg = if (rotateOnWrongOrientation) {
            val angle = atan2(rightEye.y - leftEye.y, rightEye.x - leftEye.x)
            val degrees = Math.toDegrees(angle)
            if (abs(degrees) > 45) {
                Napier.d(tag = traceId) { "Rotating face to portrait position with angle: $degrees" }
                rotate(faceImage, -angle)
            } else {
                faceImage
            }
        } else {
            faceImage
        }
        Napier.d(tag = traceId) {
            "Extracting face done with resulting image [${CGImageGetWidth(resultImg)},${
                CGImageGetHeight(
                    resultImg
                )
            }]"
        }
        return resultImg.pointed
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