package net.iriscan.sdk.face.impl

import net.iriscan.sdk.core.image.NativeImage
import net.iriscan.sdk.core.utils.generateTraceID
import net.iriscan.sdk.face.LivenessModelConfiguration

/**
 * @author Slava Gornostal
 */
internal expect class FaceLivenessDetectionInternal(modelConfig: LivenessModelConfiguration) {
    fun validate(image: NativeImage, traceId: String? = generateTraceID()): Boolean
    fun score(image: NativeImage, traceId: String? = generateTraceID()): Double
}